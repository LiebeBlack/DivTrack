package com.liebeblack.divtrack.core.datastore.model

import com.liebeblack.divtrack.core.common.utils.AppConstants
import com.liebeblack.divtrack.core.common.utils.ProviderIds
import com.liebeblack.divtrack.core.common.utils.SourceKeys

/**
 * Preferencias del usuario tal como se persisten. Se mantienen como tipos primitivos para
 * que este módulo no dependa del dominio: el mapeo a `ThemeMode`/`RateSource` vive en `:data`
 * (misma dirección de dependencias que el resto del proyecto).
 */
data class UserPreferences(
    /** `SYSTEM` | `LIGHT` | `DARK` (nombres del enum de dominio). */
    val themeMode: String = THEME_SYSTEM,

    /** Clave canónica de la fuente por defecto: `oficial` | `paralelo`. */
    val defaultSourceKey: String = SourceKeys.OFICIAL,

    /** Sincronización periódica en segundo plano (WorkManager). */
    val autoSyncEnabled: Boolean = true,

    val syncIntervalMinutes: Int = AppConstants.SYNC_DEFAULT_INTERVAL_MINUTES,

    /** Proveedor que se consulta primero; vacío = orden automático por prioridad. */
    val preferredProviderId: String = "",

    /** El trabajo periódico solo corre con wifi (ahorra datos móviles). */
    val syncOnWifiOnly: Boolean = false,

    /** Indica si la bienvenida de primer uso ya se completó. */
    val welcomeCompleted: Boolean = false,

    /** Muestra la tasa paralela junto a la oficial en el panel. */
    val showParallelRate: Boolean = false,
) {
    companion object {
        const val THEME_SYSTEM: String = "SYSTEM"
        const val THEME_LIGHT: String = "LIGHT"
        const val THEME_DARK: String = "DARK"
    }
}
