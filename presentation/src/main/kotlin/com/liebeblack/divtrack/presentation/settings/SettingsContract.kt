package com.liebeblack.divtrack.presentation.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.liebeblack.divtrack.core.common.utils.AppConstants
import com.liebeblack.divtrack.core.common.utils.ProviderIds
import com.liebeblack.divtrack.domain.model.DownloadPhase
import com.liebeblack.divtrack.domain.model.ProviderDiagnostics
import com.liebeblack.divtrack.domain.model.RateSource
import com.liebeblack.divtrack.domain.model.ThemeMode
import com.liebeblack.divtrack.domain.model.UserSettings
import com.liebeblack.divtrack.presentation.R
import com.liebeblack.divtrack.presentation.common.UiText

/** Intervalos de sincronización ofrecidos. WorkManager impone el mínimo de 15 minutos. */
val SyncIntervalOptions: List<Int> = listOf(15, 30, 60, 120, 240)

/**
 * Valor del selector que representa "orden automático". Es solo un sentinela de UI: nunca
 * se persiste (DataStore guarda cadena vacía para automático) ni viaja a los proveedores.
 */
const val AUTOMATIC_PROVIDER_ID: String = "auto"

/** Opciones del selector de proveedor preferido: automático + los cuatro, en su orden base. */
val ProviderOptions: List<String> = listOf(AUTOMATIC_PROVIDER_ID) + ProviderIds.ordered

/** Etiqueta corta de cada opción del selector (los segmentos son angostos). */
@StringRes
internal fun providerLabelRes(providerId: String): Int = when (providerId) {
    AUTOMATIC_PROVIDER_ID -> R.string.provider_automatic
    ProviderIds.DOLARAPI -> R.string.provider_dolarapi
    ProviderIds.YADIO -> R.string.provider_yadio
    ProviderIds.EXCHANGERATEAPI -> R.string.provider_exchangerateapi
    ProviderIds.DOLARVZLA -> R.string.provider_dolarvzla
    else -> R.string.provider_automatic
}

/**
 * Estado del módulo de actualizaciones tal como se pinta.
 *
 * Todos los textos llegan ya formateados (fechas en es-VE y tamaño en MB) por el mismo
 * motivo que en el resto de la app: el ViewModel no toca `Context` y la pantalla no calcula.
 *
 * `checkedAtText` es la fecha **real** de la última comprobación que respondió, y es lo que
 * hace honesto el estado guardado: la pantalla nunca finge que acaba de preguntar (cuando lo
 * está haciendo, lo dice `isChecking`).
 *
 * @param isVersionUnknown `true` cuando el sistema no pudo leer la versión instalada. La
 *   pantalla lo dice en lugar de afirmar «estás al día», que sería una respuesta inventada.
 * @param canDownload `true` solo si hay versión nueva **y** el release adjuntó APK.
 * @param canInstall `true` cuando el APK ya está en el dispositivo.
 */
@Immutable
data class UpdateUiState(
    val installedVersion: String = "",
    val latestVersion: String? = null,
    val isUpdateAvailable: Boolean = false,
    val isVersionUnknown: Boolean = false,
    val checkedAtText: String? = null,
    val publishedAtText: String? = null,
    val downloadSizeText: String? = null,
    val releasePageUrl: String? = null,
    val isChecking: Boolean = false,
    val downloadPhase: DownloadPhase = DownloadPhase.NONE,
    /** 0..1 o `null` cuando el gestor todavía no sabe el tamaño total. */
    val downloadProgress: Float? = null,
    val downloadFileName: String? = null,
    val canDownload: Boolean = false,
    val canInstall: Boolean = false,
)

@Immutable
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultSource: RateSource = RateSource.OFICIAL,
    val igtfEnabled: Boolean = false,
    val autoSyncEnabled: Boolean = true,
    val syncIntervalMinutes: Int = AppConstants.SYNC_DEFAULT_INTERVAL_MINUTES,
    /** Proveedor preferido; `null` = orden automático por prioridad. */
    val defaultProviderId: String? = null,
    val syncOnWifiOnly: Boolean = false,
    val showParallelRate: Boolean = false,
    /** Diagnóstico de fuentes ya ejecutado en esta sesión; `null` = todavía no se comprobó. */
    val providerDiagnostics: ProviderDiagnostics? = null,
    val isCheckingProviders: Boolean = false,
    /** Sistema de Actualización Inteligente: versión publicada, descarga y progreso. */
    val update: UpdateUiState = UpdateUiState(),
)

/** Dominio -> UI. El estado de la pantalla solo expone lo que se pinta. */
internal fun UserSettings.toUiState(): SettingsUiState = SettingsUiState(
    themeMode = themeMode,
    defaultSource = defaultSource,
    igtfEnabled = igtfEnabled,
    autoSyncEnabled = autoSyncEnabled,
    syncIntervalMinutes = syncIntervalMinutes,
    defaultProviderId = defaultProviderId,
    syncOnWifiOnly = syncOnWifiOnly,
    showParallelRate = showParallelRate,
)

sealed interface SettingsIntent {

    data class SelectTheme(val mode: ThemeMode) : SettingsIntent

    data class SelectDefaultSource(val source: RateSource) : SettingsIntent

    data class SetIgtfDefault(val enabled: Boolean) : SettingsIntent

    data class SetAutoSync(val enabled: Boolean) : SettingsIntent

    data class SetSyncInterval(val minutes: Int) : SettingsIntent

    /** `null` = volver al orden automático (DolarAPI → Yadio → ExchangeRateAPI → DolarVZLA). */
    data class SelectProvider(val providerId: String?) : SettingsIntent

    data class SetWifiOnly(val enabled: Boolean) : SettingsIntent

    data class SetShowParallelRate(val enabled: Boolean) : SettingsIntent
}

sealed interface SettingsEffect {

    data class ShowMessage(val text: UiText) : SettingsEffect

    /**
     * El APK descargado está listo: la capa de UI lanza el instalador del sistema con la
     * ruta del archivo. La instalación **no** la hace la app (ni puede): Android muestra su
     * propia pantalla de confirmación y el usuario decide.
     */
    data class LaunchInstaller(val fileName: String) : SettingsEffect

    /** Abre la página del release en el navegador (novedades, canal, fecha). */
    data class OpenReleasePage(val url: String) : SettingsEffect
}
