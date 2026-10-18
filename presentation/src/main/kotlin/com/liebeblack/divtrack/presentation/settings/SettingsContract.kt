package com.liebeblack.divtrack.presentation.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.liebeblack.divtrack.core.common.utils.AppConstants
import com.liebeblack.divtrack.core.common.utils.ProviderIds
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

@Immutable
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultSource: RateSource = RateSource.OFICIAL,
    val autoSyncEnabled: Boolean = true,
    val syncIntervalMinutes: Int = AppConstants.SYNC_DEFAULT_INTERVAL_MINUTES,
    /** Proveedor preferido; `null` = orden automático por prioridad. */
    val defaultProviderId: String? = null,
    val syncOnWifiOnly: Boolean = false,
    val showParallelRate: Boolean = false,
    /** Diagnóstico de fuentes ya ejecutado en esta sesión; `null` = todavía no se comprobó. */
    val providerDiagnostics: ProviderDiagnostics? = null,
    val isCheckingProviders: Boolean = false,
)

/** Dominio -> UI. El estado de la pantalla solo expone lo que se pinta. */
internal fun UserSettings.toUiState(): SettingsUiState = SettingsUiState(
    themeMode = themeMode,
    defaultSource = defaultSource,
    autoSyncEnabled = autoSyncEnabled,
    syncIntervalMinutes = syncIntervalMinutes,
    defaultProviderId = defaultProviderId,
    syncOnWifiOnly = syncOnWifiOnly,
    showParallelRate = showParallelRate,
)

sealed interface SettingsIntent {

    data class SelectTheme(val mode: ThemeMode) : SettingsIntent

    data class SelectDefaultSource(val source: RateSource) : SettingsIntent

    data class SetAutoSync(val enabled: Boolean) : SettingsIntent

    data class SetSyncInterval(val minutes: Int) : SettingsIntent

    /** `null` = volver al orden automático (DolarAPI → Yadio → ExchangeRateAPI → DolarVZLA). */
    data class SelectProvider(val providerId: String?) : SettingsIntent

    data class SetWifiOnly(val enabled: Boolean) : SettingsIntent

    data class SetShowParallelRate(val enabled: Boolean) : SettingsIntent
}

sealed interface SettingsEffect {

    data class ShowMessage(val text: UiText) : SettingsEffect
}
