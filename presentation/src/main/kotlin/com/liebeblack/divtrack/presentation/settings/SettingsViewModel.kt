package com.liebeblack.divtrack.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liebeblack.divtrack.core.common.result.Result
import com.liebeblack.divtrack.domain.model.ProviderDiagnostics
import com.liebeblack.divtrack.domain.usecase.DiagnoseProvidersUseCase
import com.liebeblack.divtrack.domain.usecase.EnsureSyncScheduledUseCase
import com.liebeblack.divtrack.domain.usecase.ObserveSettingsUseCase
import com.liebeblack.divtrack.domain.usecase.UpdateSettingsUseCase
import com.liebeblack.divtrack.presentation.R
import com.liebeblack.divtrack.presentation.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    private val ensureSyncScheduled: EnsureSyncScheduledUseCase,
    private val diagnoseProviders: DiagnoseProvidersUseCase,
) : ViewModel() {

    private val _effects = MutableSharedFlow<SettingsEffect>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: SharedFlow<SettingsEffect> = _effects.asSharedFlow()

    private val isCheckingProviders = MutableStateFlow(false)
    private val providerDiagnostics = MutableStateFlow<ProviderDiagnostics?>(null)
    private val settingsUpdateMutex = Mutex()

    val state: StateFlow<SettingsUiState> = combine(
        observeSettings().map { settings -> settings.toUiState() },
        isCheckingProviders,
        providerDiagnostics,
    ) { uiState, checking, diagnostics ->
        uiState.copy(
            isCheckingProviders = checking,
            providerDiagnostics = diagnostics,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SettingsUiState(),
    )

    fun onIntent(intent: SettingsIntent) {
        viewModelScope.launch {
            try {
                settingsUpdateMutex.withLock {
                    when (intent) {
                        is SettingsIntent.SelectTheme -> updateSettings.setThemeMode(intent.mode)

                        is SettingsIntent.SelectDefaultSource ->
                            updateSettings.setDefaultSource(intent.source)

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

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
