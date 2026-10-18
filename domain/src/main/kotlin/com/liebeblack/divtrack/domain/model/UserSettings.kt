package com.liebeblack.divtrack.domain.model

import com.liebeblack.divtrack.core.common.utils.AppConstants
import com.liebeblack.divtrack.core.common.utils.ProviderIds

/**
 * Preferencias del usuario ya interpretadas (enums, no cadenas de DataStore).
 *
 * @param defaultProviderId proveedor que se consulta primero al sincronizar. `null`
 * significa "automático": el orden por prioridad del proyecto (DolarAPI → Yadio →
 * ExchangeRateAPI). El id es la clave canónica de [ProviderIds].
 * @param syncOnWifiOnly cuando es `true`, el trabajo periódico solo corre con wifi:
 * ahorra datos móviles; cuando es `false`, corre con cualquier red disponible.
 */
data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultSource: RateSource = RateSource.OFICIAL,
    val autoSyncEnabled: Boolean = true,
    val syncIntervalMinutes: Int = AppConstants.SYNC_DEFAULT_INTERVAL_MINUTES,
    val defaultProviderId: String? = null,
    val syncOnWifiOnly: Boolean = false,
    val welcomeCompleted: Boolean = false,
    val showParallelRate: Boolean = false,
)
