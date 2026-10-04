package com.liebeblack.divtrack.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.liebeblack.divtrack.core.common.error.DataError
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
                    if (ratesResult.error.isTransient()) {
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

    /**
     * `true` cuando reintentar tiene sentido: es la misma frontera que usa la capa de red.
     * Los fallos deterministas (respuesta ilegible, endpoint cambiado, datos vacíos) no
     * mejoran por esperar, y reintentarlos solo gasta radio y batería.
     */
    private fun DataError.isTransient(): Boolean = when (this) {
        is DataError.Network, is DataError.Timeout -> true
        is DataError.Http -> code == HTTP_REQUEST_TIMEOUT ||
            code == HTTP_TOO_MANY_REQUESTS ||
            code in HTTP_SERVER_ERROR_RANGE
        is DataError.Parse,
        is DataError.EmptyCache,
        is DataError.Storage,
        is DataError.Unknown,
        -> false
    }

    companion object {
        private const val TAG = "RateSyncWorker"
        private const val HTTP_REQUEST_TIMEOUT = 408
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private val HTTP_SERVER_ERROR_RANGE = 500..599
    }
}
