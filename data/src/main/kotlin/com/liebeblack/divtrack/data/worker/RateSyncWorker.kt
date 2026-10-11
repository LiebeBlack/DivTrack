package com.liebeblack.divtrack.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.liebeblack.divtrack.core.common.logging.Logger
import com.liebeblack.divtrack.core.common.result.Result as AppResult
import com.liebeblack.divtrack.domain.usecase.SyncRatesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Sincronización silenciosa en segundo plano.
 *
 * No muestra notificaciones ni UI: escribe en Room y, si Room emite, las pantallas se
 * actualizan solas la próxima vez que el usuario las mire.
 *
 * **Se reintenta solo lo que puede cambiar por esperar** (misma regla que el
 * `RetryInterceptor` de la capa de red, ADR 22): un proveedor caído o una conexión que
 * falla se reintentan con el backoff exponencial de WorkManager; un 404 (endpoint
 * cambiado), una respuesta ilegible o un envoltorio de error del proveedor devuelven el
 * mismo resultado en 30 segundos, así que este intento se marca como fallido y el
 * siguiente ciclo periódico vuelve a intentarlo sin martillear la API.
 *
 * La cancelación estructurada se propaga: `CancellationException` nunca se convierte en
 * `retry()`.
 */
@HiltWorker
class RateSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncRates: SyncRatesUseCase,
    private val logger: Logger,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): ListenableWorker.Result {
        return try {
            when (val ratesResult = syncRates()) {
                is AppResult.Success -> {
                    logger.debug(TAG, "Tasas sincronizadas: ${ratesResult.data.updatedSources.size} fuentes")
                    ListenableWorker.Result.success()
                }

                is AppResult.Error -> {
                    logger.warn(
                        TAG,
                        "Sincronización falló (intento ${runAttemptCount + 1}): ${ratesResult.error.message}",
                    )
                    if (ratesResult.error.isWorthRetrying()) {
                        ListenableWorker.Result.retry()
                    } else {
                        ListenableWorker.Result.failure()
                    }
                }

                // Nunca lo emite una función `suspend` (ver el KDoc de `Result`): se
                // reintenta por si acaso en lugar de dar la pasada por perdida.
                AppResult.Loading -> ListenableWorker.Result.retry()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            // Excepción no clasificada: puede ser del entorno (almacenamiento, proceso),
            // así que se reintenta. El backoff de WorkManager acota el coste.
            logger.error(
                TAG,
                "Error inesperado en sincronización (intento ${runAttemptCount + 1}): ${exception.message}",
                exception,
            )
            ListenableWorker.Result.retry()
        }
    }

    private companion object {
        const val TAG = "RateSyncWorker"
    }
}
