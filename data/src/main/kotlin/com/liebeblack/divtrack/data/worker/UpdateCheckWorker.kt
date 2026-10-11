package com.liebeblack.divtrack.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.liebeblack.divtrack.core.common.logging.Logger
import com.liebeblack.divtrack.core.common.result.Result as AppResult
import com.liebeblack.divtrack.domain.usecase.CheckForUpdatesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Comprobación silenciosa de versión en segundo plano.
 *
 * No notifica nada: guarda el resultado en DataStore y la app lo cuenta en Ajustes cuando el
 * usuario entra. Es la diferencia entre «la app se entera» y «la app interrumpe», y es
 * también lo que permite que este módulo no declare el permiso de notificaciones.
 *
 * La comprobación se pide **sin** forzar (`force = false`): respeta la ventana de frescura de
 * seis horas, de modo que un usuario que acaba de comprobar a mano no genera una segunda
 * petición a la API pública de GitHub solo porque WorkManager despertara.
 *
 * Los reintentos siguen la regla compartida [isWorthRetrying]: un fallo de red o un 5xx se
 * reintentan con el backoff de WorkManager; un 404 o un release ilegible no, porque el
 * siguiente ciclo periódico ya volverá a intentarlo sin martillear la API.
 */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val checkForUpdates: CheckForUpdatesUseCase,
    private val logger: Logger,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): ListenableWorker.Result {
        return try {
            when (val result = checkForUpdates(force = false)) {
                is AppResult.Success -> {
                    logger.debug(
                        TAG,
                        "Versión publicada: ${result.data.latestVersion ?: "desconocida"}",
                    )
                    ListenableWorker.Result.success()
                }

                is AppResult.Error -> {
                    logger.warn(
                        TAG,
                        "Comprobación de versión falló (intento ${runAttemptCount + 1}): " +
                            "${result.error.message}",
                    )
                    if (result.error.isWorthRetrying()) {
                        ListenableWorker.Result.retry()
                    } else {
                        ListenableWorker.Result.failure()
                    }
                }

                // Nunca lo emite una función `suspend` (ver el KDoc de `Result`): se reintenta
                // en lugar de dar la comprobación por perdida.
                AppResult.Loading -> ListenableWorker.Result.retry()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            logger.error(
                TAG,
                "Error inesperado comprobando la versión (intento ${runAttemptCount + 1}): " +
                    exception.message,
                exception,
            )
            ListenableWorker.Result.retry()
        }
    }

    private companion object {
        const val TAG = "UpdateCheckWorker"
    }
}
