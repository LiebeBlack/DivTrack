package com.liebeblack.divtrack.data.mapper

import com.liebeblack.divtrack.core.common.utils.ProviderIds
import com.liebeblack.divtrack.core.datastore.model.UserPreferences
import com.liebeblack.divtrack.domain.model.RateSource
import com.liebeblack.divtrack.domain.model.ThemeMode
import com.liebeblack.divtrack.domain.model.UserSettings

/**
 * DataStore (primitivos) -> dominio (enums). Este mapeo es la razón de que
 * `:core:datastore` no necesite conocer el dominio.
 */
internal fun UserPreferences.toDomain(): UserSettings = UserSettings(
    themeMode = ThemeMode.fromStorage(themeMode),
    defaultSource = RateSource.fromKey(defaultSourceKey) ?: RateSource.OFICIAL,
    autoSyncEnabled = autoSyncEnabled,
    syncIntervalMinutes = syncIntervalMinutes,
    // Cadena vacía = automático: se normaliza aquí para que el dominio solo vea `null`
    // o un id conocido de ProviderIds.
    defaultProviderId = preferredProviderId.takeIf { it in ProviderIds.ordered },
    syncOnWifiOnly = syncOnWifiOnly,
    welcomeCompleted = welcomeCompleted,
    showParallelRate = showParallelRate,
    enablePersistentNotification = enablePersistentNotification,
    hideUnofficialRate = hideUnofficialRate,
    customOfficialRate = customOfficialRate,
    customParallelRate = customParallelRate,
)
