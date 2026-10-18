package com.liebeblack.divtrack.domain.usecase

import com.liebeblack.divtrack.domain.model.RateSource
import com.liebeblack.divtrack.domain.model.ThemeMode
import com.liebeblack.divtrack.domain.repository.SettingsRepository
import javax.inject.Inject

/** Escrituras de preferencias. Cada acción del usuario es una llamada pequeña y explícita. */
class UpdateSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {

    suspend fun setThemeMode(mode: ThemeMode) = repository.setThemeMode(mode)

    suspend fun setDefaultSource(source: RateSource) = repository.setDefaultSource(source)

    suspend fun setAutoSyncEnabled(enabled: Boolean) = repository.setAutoSyncEnabled(enabled)

    suspend fun setSyncInterval(minutes: Int) = repository.setSyncInterval(minutes)

    /** `null` vuelve al orden automático. */
    suspend fun setDefaultProvider(providerId: String?) = repository.setDefaultProvider(providerId)

    suspend fun setSyncOnWifiOnly(enabled: Boolean) = repository.setSyncOnWifiOnly(enabled)

    suspend fun setWelcomeCompleted(completed: Boolean) =
        repository.setWelcomeCompleted(completed)

    suspend fun setShowParallelRate(enabled: Boolean) =
        repository.setShowParallelRate(enabled)
}
