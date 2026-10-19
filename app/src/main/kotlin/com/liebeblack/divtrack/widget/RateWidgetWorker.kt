package com.liebeblack.divtrack.widget

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.liebeblack.divtrack.domain.model.RateSource
import com.liebeblack.divtrack.domain.repository.RateRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Worker de WorkManager para actualizar el widget cada 3 horas.
 *
 * Optimizaciones:
 * - Restricciones: solo ejecuta con internet y batería suficiente
 * - Limpieza inmediata de memoria tras ejecución
 * - Sin Foreground Services ni AlarmManager
 * - Single execution por periodo para evitar duplicados
 */
@HiltWorker
class RateWidgetWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val rateRepository: RateRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // Ejecutar en dispatcher IO para no bloquear
            withContext(Dispatchers.IO) {
                // Obtener tasa oficial
                val rate = rateRepository.getCurrentRate(RateSource.OFICIAL)

                if (rate != null) {
                    // Guardar en cache usando singleton (instantáneo, sin bloqueos)
                    WidgetDataManager.getInstance(context).saveRate(
                        rate = rate.rate,
                        timestamp = rate.lastUpdatedAtMillis,
                        source = rate.providerId,
                    )

                    // Actualizar widget UI
                    RateWidgetProvider.updateAllWidgets(context)

                    Result.success()
                } else {
                    // Sin datos: marcar como fallo temporal (reintentará)
                    Result.retry()
                }
            }
        } catch (e: CancellationException) {
            // Cancelación externa: no reintentar
            throw e
        } catch (e: Exception) {
            // Error inesperado: reintentar con backoff
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "rate_widget_update"
        private const val MANUAL_WORK_NAME = "rate_widget_manual_update"

        /**
         * Restricciones inteligentes para optimizar batería:
         * - Solo con internet activo
         * - No en batería ultra baja
         * - Evita ejecución cuando el dispositivo está ahorrando energía
         */
        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        /**
         * Programa actualización periódica cada 3 horas.
         * WorkManager mínimo: 15 minutos, pero usamos 3 horas como solicitado.
         */
        fun schedulePeriodicUpdates(context: Context) {
            val periodicRequest = PeriodicWorkRequestBuilder<RateWidgetWorker>(
                repeatInterval = 3,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // No reemplazar si ya existe
                periodicRequest,
            )
        }

        /**
         * Cancela actualización periódica (cuando se elimina el widget).
         */
        fun cancelPeriodicUpdates(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /**
         * Dispara actualización manual inmediata (botón del widget).
         * Usa OneTimeWorkRequest con las mismas restricciones.
         */
        fun enqueueUpdate(context: Context, manual: Boolean = false) {
            val oneTimeRequest = OneTimeWorkRequestBuilder<RateWidgetWorker>()
                .setConstraints(constraints)
                .build()

            val workName = if (manual) MANUAL_WORK_NAME else WORK_NAME
            val policy = if (manual) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP

            WorkManager.getInstance(context).enqueueUniqueWork(
                workName,
                policy,
                oneTimeRequest,
            )
        }
    }
}
