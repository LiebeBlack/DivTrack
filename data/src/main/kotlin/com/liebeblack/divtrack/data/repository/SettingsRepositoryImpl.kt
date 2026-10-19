package com.liebeblack.divtrack.data.repository

import com.liebeblack.divtrack.core.datastore.UserPreferencesDataSource
import com.liebeblack.divtrack.data.mapper.toDomain
import com.liebeblack.divtrack.domain.model.RateSource
import com.liebeblack.divtrack.domain.model.ThemeMode
import com.liebeblack.divtrack.domain.model.UserSettings
import com.liebeblack.divtrack.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val preferencesDataSource: UserPreferencesDataSource,
) : SettingsRepository {

    override fun observeSettings(): Flow<UserSettings> =
        preferencesDataSource.preferences.map { preferences -> preferences.toDomain() }

    override suspend fun setThemeMode(mode: ThemeMode) =
        preferencesDataSource.setThemeMode(mode.name)

    override suspend fun setDefaultSource(source: RateSource) =
        preferencesDataSource.setDefaultSource(source.key)

    override suspend fun setAutoSyncEnabled(enabled: Boolean) =
        preferencesDataSource.setAutoSyncEnabled(enabled)

    override suspend fun setSyncInterval(minutes: Int) =
        preferencesDataSource.setSyncIntervalMinutes(minutes)

    override suspend fun setDefaultProvider(providerId: String?) =
        preferencesDataSource.setPreferredProvider(providerId.orEmpty())

    override suspend fun setSyncOnWifiOnly(enabled: Boolean) =
        preferencesDataSource.setSyncOnWifiOnly(enabled)

    override suspend fun setWelcomeCompleted(completed: Boolean) =
        preferencesDataSource.setWelcomeCompleted(completed)

    override suspend fun setShowParallelRate(enabled: Boolean) =
        preferencesDataSource.setShowParallelRate(enabled)

    override suspend fun setEnablePersistentNotification(enabled: Boolean) =
        preferencesDataSource.setEnablePersistentNotification(enabled)

    override suspend fun setHideUnofficialRate(enabled: Boolean) =
        preferencesDataSource.setHideUnofficialRate(enabled)

    override suspend fun setCustomOfficialRate(rate: Double) =
        preferencesDataSource.setCustomOfficialRate(rate)

    override suspend fun setCustomParallelRate(rate: Double) =
        preferencesDataSource.setCustomParallelRate(rate)
}
