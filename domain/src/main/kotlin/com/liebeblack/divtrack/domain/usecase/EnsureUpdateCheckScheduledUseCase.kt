package com.liebeblack.divtrack.domain.usecase

import com.liebeblack.divtrack.domain.scheduler.UpdateCheckScheduler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deja programada la comprobación de versión en segundo plano. Se invoca al arrancar la
 * app, junto a la sincronización de tasas, y es idempotente: WorkManager actualiza la
 * programación existente en lugar de duplicarla.
 */
@Singleton
class EnsureUpdateCheckScheduledUseCase @Inject constructor(
    private val updateCheckScheduler: UpdateCheckScheduler,
) {
    suspend operator fun invoke() = updateCheckScheduler.ensureScheduled()
}
