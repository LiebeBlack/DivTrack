package com.liebeblack.divtrack.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liebeblack.divtrack.core.common.result.Result
import com.liebeblack.divtrack.core.common.utils.CurrencyFormatters
import com.liebeblack.divtrack.domain.model.DownloadPhase
import com.liebeblack.divtrack.domain.model.ProviderDiagnostics
import com.liebeblack.divtrack.domain.model.UpdateAvailability
import com.liebeblack.divtrack.domain.model.UpdateDownload
import com.liebeblack.divtrack.domain.model.UpdateStatus
import com.liebeblack.divtrack.domain.usecase.CheckForUpdatesUseCase
import com.liebeblack.divtrack.domain.usecase.DiagnoseProvidersUseCase
import com.liebeblack.divtrack.domain.usecase.DownloadUpdateUseCase
import com.liebeblack.divtrack.domain.usecase.EnsureSyncScheduledUseCase
import com.liebeblack.divtrack.domain.usecase.ObserveSettingsUseCase
import com.liebeblack.divtrack.domain.usecase.ObserveUpdateStatusUseCase
import com.liebeblack.divtrack.domain.usecase.PollUpdateDownloadUseCase
import com.liebeblack.divtrack.domain.usecase.UpdateSettingsUseCase
import com.liebeblack.divtrack.presentation.R
import com.liebeblack.divtrack.presentation.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Ajustes. No hay estado local duplicado: la pantalla es un reflejo de DataStore, así que
 * cualquier cambio se persiste y vuelve por el mismo flujo (una sola fuente de verdad).
 *
 * Los cambios de sincronización reprograman además el trabajo periódico, para que la
 * preferencia y lo que hace WorkManager nunca se desincronicen. La comprobación de fuentes
 * sí es estado local (no es una preferencia): vive solo en la memoria del ViewModel.
 *
 * **Módulo de actualizaciones.** La sección «Actualizaciones» vive aquí y no en una pantalla
 * aparte porque su superficie es pequeña (versión instalada, versión publicada, descargar e
 * instalar) y comparte los avisos y el estado de la pantalla de Ajustes. Tampoco es una
 * preferencia: comprobar y descargar son acciones puntuales, así que van en métodos propios
 * (`onCheckForUpdates`, `onDownloadUpdate`, `onInstallUpdate`), igual que `onCheckProviders`,
 * y no por el `when` de intents que persiste ajustes.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    private val ensureSyncScheduled: EnsureSyncScheduledUseCase,
    private val diagnoseProviders: DiagnoseProvidersUseCase,
    observeUpdateStatus: ObserveUpdateStatusUseCase,
    private val checkForUpdates: CheckForUpdatesUseCase,
    private val downloadUpdate: DownloadUpdateUseCase,
    private val pollUpdateDownload: PollUpdateDownloadUseCase,
) : ViewModel() {

    private val _effects = MutableSharedFlow<SettingsEffect>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: SharedFlow<SettingsEffect> = _effects.asSharedFlow()

    private val isCheckingProviders = MutableStateFlow(false)
    private val providerDiagnostics = MutableStateFlow<ProviderDiagnostics?>(null)
    private val settingsUpdateMutex = Mutex()

    private val isCheckingUpdate = MutableStateFlow(false)

    /**
     * Última lectura del gestor de descargas. `null` = todavía no se ha sondeado, y entonces
     * manda el estado guardado, que solo sabe si había una descarga encolada.
     */
    private val liveDownload = MutableStateFlow<UpdateDownload?>(null)
    private var downloadPollingJob: Job? = null

    /**
     * Los tres datos de la sección de actualizaciones, agrupados: `combine` tipado solo
     * admite cinco flujos y la pantalla ya tiene tres fuentes propias.
     *
     * `isCheckingUpdate` viaja como flujo (y no se lee con `.value` dentro del `combine`) a
     * propósito: un `MutableStateFlow` leído así no dispara una nueva emisión, y el botón se
     * quedaría girando sin que el estado se enterara.
     */
    private val updateInputs: Flow<UpdateInputs> = combine(
        observeUpdateStatus(),
        liveDownload,
        isCheckingUpdate,
    ) { status, download, checking ->
        UpdateInputs(status = status, download = download, isChecking = checking)
    }

    val state: StateFlow<SettingsUiState> = combine(
        observeSettings().map { settings -> settings.toUiState() },
        isCheckingProviders,
        providerDiagnostics,
        updateInputs,
    ) { uiState, checking, diagnostics, update ->
        uiState.copy(
            isCheckingProviders = checking,
            providerDiagnostics = diagnostics,
            update = update.status.toUpdateUiState(
                liveDownload = update.download,
                isChecking = update.isChecking,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SettingsUiState(),
    )

    init {
        // Si el usuario vuelve a Ajustes con una descarga en curso, el sondeo se reengancha:
        // el gestor del sistema siguió descargando con la app cerrada. Una sola lectura, no
        // una recolección permanente: sin descarga activa no queda nada observando.
        viewModelScope.launch {
            if (observeUpdateStatus().first().download.isActive) pollDownloadUntilSettled()
        }
    }

    fun onIntent(intent: SettingsIntent) {
        viewModelScope.launch {
            try {
                settingsUpdateMutex.withLock {
                    when (intent) {
                        is SettingsIntent.SelectTheme -> updateSettings.setThemeMode(intent.mode)

                        is SettingsIntent.SelectDefaultSource ->
                            updateSettings.setDefaultSource(intent.source)

                        is SettingsIntent.SetIgtfDefault ->
                            updateSettings.setIgtfEnabled(intent.enabled)

                        is SettingsIntent.SetAutoSync -> {
                            updateSettings.setAutoSyncEnabled(intent.enabled)
                            ensureSyncScheduled()
                        }

                        is SettingsIntent.SetSyncInterval -> {
                            updateSettings.setSyncInterval(intent.minutes)
                            ensureSyncScheduled()
                        }

                        is SettingsIntent.SelectProvider ->
                            updateSettings.setDefaultProvider(intent.providerId)

                        is SettingsIntent.SetWifiOnly -> {
                            updateSettings.setSyncOnWifiOnly(intent.enabled)
                            ensureSyncScheduled()
                        }

                        is SettingsIntent.SetShowParallelRate ->
                            updateSettings.setShowParallelRate(intent.enabled)
                    }

                    _effects.emit(SettingsEffect.ShowMessage(UiText.Res(R.string.settings_saved)))
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: IOException) {
                _effects.emit(SettingsEffect.ShowMessage(UiText.Res(R.string.settings_save_failed)))
            } catch (_: Exception) {
                // Misma promesa que arriba: un fallo inesperado del almacén se cuenta en
                // pantalla en lugar de dejar la corrutina muerta y la app caída.
                _effects.emit(SettingsEffect.ShowMessage(UiText.Res(R.string.settings_save_failed)))
            }
        }
    }

    /**
     * Comprueba las fuentes contra sus APIs de verdad. Es una acción puntual, no una
     * preferencia: el resultado vive en el estado local del ViewModel.
     */
    fun onCheckProviders() {
        if (isCheckingProviders.value) return

        viewModelScope.launch {
            isCheckingProviders.value = true
            try {
                val diagnostics = diagnoseProviders()
                providerDiagnostics.value = diagnostics
                _effects.emit(
                    SettingsEffect.ShowMessage(
                        UiText.ResArgs(
                            R.string.msg_providers_checked,
                            listOf(diagnostics.reachableCount, diagnostics.total),
                        ),
                    ),
                )
            } catch (cancellation: CancellationException) {
                // Salir de la pantalla mientras se comprueba no es un fallo: la cancelación
                // se propaga en lugar de contarse como si las fuentes hubieran fallado.
                throw cancellation
            } catch (_: Exception) {
                // El diagnóstico ya se protege en el repositorio; esta red de seguridad
                // existe para que un fallo inesperado se cuente en pantalla en vez de dejar
                // la corrutina muerta con el botón girando para siempre.
                _effects.emit(
                    SettingsEffect.ShowMessage(UiText.Res(R.string.settings_providers_check_failed)),
                )
            } finally {
                isCheckingProviders.value = false
            }
        }
    }

    /**
     * Comprobación manual de versión. Fuerza la consulta: el usuario pidió la respuesta y no
     * una lectura de la ventana de frescura.
     */
    fun onCheckForUpdates() {
        if (isCheckingUpdate.value) return

        viewModelScope.launch {
            isCheckingUpdate.value = true
            try {
                when (val result = checkForUpdates(force = true)) {
                    is Result.Success -> _effects.emit(
                        SettingsEffect.ShowMessage(
                            if (result.data.availability == UpdateAvailability.UPDATE_AVAILABLE) {
                                UiText.ResArgs(
                                    R.string.msg_update_available,
                                    listOf(result.data.latestVersion.orEmpty()),
                                )
                            } else {
                                UiText.Res(R.string.msg_update_current)
                            },
                        ),
                    )

                    // El motivo real viaja en el error tipado; el aviso no puede decir «sin
                    // conexión» cuando lo que pasó fue otra cosa (misma regla que las tasas).
                    is Result.Error -> _effects.emit(
                        SettingsEffect.ShowMessage(UiText.Res(R.string.settings_update_check_failed)),
                    )

                    Result.Loading -> Unit
                }
            } finally {
                isCheckingUpdate.value = false
            }
        }
    }

    /** Descarga el APK publicado y sigue su progreso mientras la descarga está viva. */
    fun onDownloadUpdate() {
        if (liveDownload.value?.isActive == true) return

        viewModelScope.launch {
            when (downloadUpdate()) {
                is Result.Success -> pollDownloadUntilSettled()

                is Result.Error -> _effects.emit(
                    SettingsEffect.ShowMessage(UiText.Res(R.string.settings_update_download_failed)),
                )

                Result.Loading -> Unit
            }
        }
    }

    /**
     * Pide lanzar el instalador del sistema con el APK ya descargado.
     *
     * La ruta del archivo se resuelve aquí (del sondeo, o del estado guardado si la descarga
     * terminó en una sesión anterior) y la capa de UI es la única que toca `Context`.
     */
    fun onInstallUpdate() {
        val fileName = liveDownload.value?.fileName
            ?: state.value.update.downloadFileName
        if (fileName.isNullOrBlank()) return

        viewModelScope.launch { _effects.emit(SettingsEffect.LaunchInstaller(fileName)) }
    }

    /** Abre la página del release publicado: las novedades se leen donde se escriben. */
    fun onOpenReleasePage() {
        val url = state.value.update.releasePageUrl ?: return
        viewModelScope.launch { _effects.emit(SettingsEffect.OpenReleasePage(url)) }
    }

    /**
     * Sondeo del gestor de descargas, solo mientras hay trabajo en curso.
     *
     * Se detiene en la primera fase terminal (`READY`, `FAILED` o ninguna descarga), así que
     * no deja ningún bucle vivo cuando la descarga termina —ni cuando el usuario cambia de
     * pestaña, porque el trabajo va en `viewModelScope`.
     */
    private fun pollDownloadUntilSettled() {
        if (downloadPollingJob?.isActive == true) return

        downloadPollingJob = viewModelScope.launch {
            while (isActive) {
                val download = pollUpdateDownload()
                liveDownload.value = download
                if (!download.isActive) return@launch
                delay(DOWNLOAD_POLL_MILLIS)
            }
        }
    }

    /**
     * Estado de actualizaciones para pintar. El tamaño va en MB con el formato es-VE de la
     * app ("21,9") porque el texto que lo rodea es un recurso; las fechas, en el mismo
     * formato corto que el resto de la interfaz.
     */
    private fun UpdateStatus.toUpdateUiState(
        liveDownload: UpdateDownload?,
        isChecking: Boolean,
    ): UpdateUiState {
        // `fromCache` no se pinta: lo que se enseña es la fecha real de la última
        // comprobación que respondió, y con eso el usuario sabe si el dato es de ahora o de
        // ayer sin que la pantalla tenga que etiquetarlo.
        val effectiveDownload = liveDownload ?: download

        return UpdateUiState(
            installedVersion = installedVersion,
            latestVersion = latestVersion,
            isUpdateAvailable = availability == UpdateAvailability.UPDATE_AVAILABLE,
            isVersionUnknown = installedVersion.isBlank(),
            checkedAtText = checkedAtMillis?.let(::formatTimestampOrNull),
            publishedAtText = release?.publishedAtMillis?.let(::formatTimestampOrNull),
            downloadSizeText = release?.downloadSizeBytes
                ?.takeIf { bytes -> bytes > 0L }
                ?.let { bytes ->
                    CurrencyFormatters.amount(bytes / BYTES_PER_MEGABYTE, decimals = 1)
                },
            releasePageUrl = release?.pageUrl,
            isChecking = isChecking,
            downloadPhase = effectiveDownload.phase,
            downloadProgress = effectiveDownload.progressFraction,
            downloadFileName = effectiveDownload.fileName,
            // Descargar exige dos cosas: que haya versión nueva y que el release adjuntara
            // APK. Un release a medio publicar se cuenta, no se convierte en un botón roto.
            canDownload = availability == UpdateAvailability.UPDATE_AVAILABLE &&
                release?.downloadUrl != null,
            canInstall = effectiveDownload.phase == DownloadPhase.READY,
        )
    }

    private fun formatTimestampOrNull(millis: Long): String? =
        runCatching { CurrencyFormatters.timestamp(Instant.ofEpochMilli(millis)) }.getOrNull()

    /** Los tres datos de la sección de actualizaciones, agrupados para `combine`. */
    private data class UpdateInputs(
        val status: UpdateStatus,
        val download: UpdateDownload?,
        val isChecking: Boolean,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Suficiente para que la barra avance sin castigar la batería con sondeos. */
        const val DOWNLOAD_POLL_MILLIS = 800L

        const val BYTES_PER_MEGABYTE = 1_048_576.0
    }
}
