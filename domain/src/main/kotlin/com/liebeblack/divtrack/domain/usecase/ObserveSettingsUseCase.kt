package com.liebeblack.divtrack.domain.usecase

import com.liebeblack.divtrack.domain.model.UserSettings
import com.liebeblack.divtrack.domain.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Observa las preferencias: tema, fuente por defecto y sincronización en segundo plano. */
class ObserveSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    operator fun invoke(): Flow<UserSettings> = repository.observeSettings()
}
