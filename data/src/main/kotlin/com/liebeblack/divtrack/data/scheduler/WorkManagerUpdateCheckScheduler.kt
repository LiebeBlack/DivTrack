package com.liebeblack.divtrack.data.scheduler

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.liebeblack.divtrack.core.common.utils.AppConstants
import com.liebeblack.divtrack.data.worker.UpdateCheckWorker
import com.liebeblack.divtrack.domain.scheduler.UpdateCheckScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprobación diaria de versión con WorkManager.
 *
 * - **Nombre único y `UPDATE`:** volver a programar no duplica el trabajo ni lo reinicia a
 *   cero; es el mismo criterio que usa la sincronización de tasas.
 * - **Restricción de red `CONNECTED`:** nunca despierta el proceso para fallar por falta de
 *   datos; sin red, el trabajo simplemente espera.
 * - **Backoff exponencial:** si GitHub no responde, el reintento se aleja en lugar de
 *   martillear la API (que además limita a 60 peticiones por hora sin autenticar).
 */
@Singleton
class WorkManagerUpdateCheckScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : UpdateCheckScheduler {

    override suspend fun ensureScheduled() {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            AppConstants.UPDATE_CHECK_INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private companion object {
        const val PERIODIC_WORK_NAME = "divtrack_update_check_periodic"
        const val BACKOFF_SECONDS = 60L
    }
}
