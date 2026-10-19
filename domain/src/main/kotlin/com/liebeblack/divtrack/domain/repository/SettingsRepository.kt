package com.liebeblack.divtrack.domain.repository

import com.liebeblack.divtrack.domain.model.RateSource
import com.liebeblack.divtrack.domain.model.ThemeMode
import com.liebeblack.divtrack.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

/** Contrato de preferencias (tema, fuente, sincronización, proveedor preferido). */
interface SettingsRepository {

    fun observeSettings(): Flow<UserSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDefaultSource(source: RateSource)

    suspend fun setAutoSyncEnabled(enabled: Boolean)

    suspend fun setSyncInterval(minutes: Int)

    /** `null` = orden automático por prioridad del proyecto. */
    suspend fun setDefaultProvider(providerId: String?)

    suspend fun setSyncOnWifiOnly(enabled: Boolean)

    suspend fun setWelcomeCompleted(completed: Boolean)

    suspend fun setShowParallelRate(enabled: Boolean)

    suspend fun setEnablePersistentNotification(enabled: Boolean)

    suspend fun setHideUnofficialRate(enabled: Boolean)

    suspend fun setCustomOfficialRate(rate: Double)

    suspend fun setCustomParallelRate(rate: Double)
}
