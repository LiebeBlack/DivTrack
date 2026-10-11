package com.liebeblack.divtrack.data.repository

import com.liebeblack.divtrack.core.common.error.DataError
import com.liebeblack.divtrack.core.common.logging.Logger
import com.liebeblack.divtrack.core.common.result.Result
import com.liebeblack.divtrack.core.common.time.TimeProvider
import com.liebeblack.divtrack.core.common.utils.AppConstants
import com.liebeblack.divtrack.core.datastore.UpdateStateDataSource
import com.liebeblack.divtrack.core.network.BuildConfig
import com.liebeblack.divtrack.core.network.error.NetworkErrorMapper
import com.liebeblack.divtrack.core.network.monitor.ConnectivityObserver
import com.liebeblack.divtrack.core.network.service.GithubReleasesService
import com.liebeblack.divtrack.data.di.IoDispatcher
import com.liebeblack.divtrack.data.mapper.toAppRelease
import com.liebeblack.divtrack.data.mapper.toUpdateStatus
import com.liebeblack.divtrack.domain.model.DownloadPhase
import com.liebeblack.divtrack.domain.model.UpdateDownload
import com.liebeblack.divtrack.domain.model.UpdateStatus
import com.liebeblack.divtrack.domain.repository.AppDownloader
import com.liebeblack.divtrack.domain.repository.InstalledAppVersionProvider
import com.liebeblack.divtrack.domain.repository.UpdateRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Módulo de actualizaciones.
 *
 * Reglas, en el mismo espíritu que `RateRepositoryImpl`:
 *
 * 1. **El estado guardado es la única fuente para la UI.** [observeStatus] emite lo que hay
 *    en DataStore: la sección de Ajustes se pinta sin red y sin esperar a nadie.
 * 2. **La red solo escribe.** [checkForUpdates] pregunta a la API pública de GitHub; si
 *    responde, guarda el resultado; si falla, **no** escribe nada (un fallo de conexión no
 *    puede convertirse en un «no hay actualizaciones» durante seis horas) y devuelve el
 *    error tipado para que la pantalla cuente la causa real.
 * 3. **Fallo rápido sin conectividad**, igual que las tasas: no se espera un timeout para
 *    decir «sin conexión», y la comprobación no pasa por la caché HTTP —que podría devolver
 *    el release de ayer como si fuera de ahora— porque ni siquiera se intenta.
 * 4. **Una descarga que quedó de una versión anterior se borra.** Cuando aparece una versión
 *    nueva, el APK viejo se elimina del dispositivo: no se acumulan instaladores que el
 *    usuario no puede ni ver ni limpiar.
 *
 * @param installedAppVersion puerto que lee el `versionName` del paquete instalado.
 * @param downloader puerto del gestor de descargas (`:data` lo implementa con el sistema).
 */
@Singleton
class UpdateRepositoryImpl @Inject constructor(
    private val service: GithubReleasesService,
    private val updateStateDataSource: UpdateStateDataSource,
    private val installedAppVersion: InstalledAppVersionProvider,
    private val downloader: AppDownloader,
    private val connectivityObserver: ConnectivityObserver,
    private val errorMapper: NetworkErrorMapper,
    private val timeProvider: TimeProvider,
    private val logger: Logger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : UpdateRepository {

    /**
     * Se lee una sola vez por proceso: el `PackageManager` no cambia de versión mientras la
     * app corre (una actualización reinicia el proceso). Evita una llamada al sistema por
     * cada emisión del flujo.
     */
    private val installedVersion: String by lazy { installedAppVersion.versionName() }

    override fun observeStatus(): Flow<UpdateStatus> = updateStateDataSource.state
        .map { state -> state.toUpdateStatus(installedVersion = installedVersion) }
        .flowOn(ioDispatcher)

    override suspend fun checkForUpdates(force: Boolean): Result<UpdateStatus> =
        withContext(ioDispatcher) {
            val persisted = updateStateDataSource.state.first()
            val now = timeProvider.nowMillis()

            if (!force && isWithinFreshnessWindow(persisted.checkedAtMillis, now)) {
                logger.debug(TAG, "Comprobación de versión omitida: la última sigue siendo fresca")
                return@withContext Result.Success(
                    persisted.toUpdateStatus(installedVersion = installedVersion),
                )
            }

            if (!connectivityObserver.isOnline()) {
                logger.warn(TAG, "Comprobación de versión omitida: sin red activa")
                return@withContext Result.Error(DataError.Network(message = "Sin conexión a internet"))
            }

            try {
                val dto = service.getLatestRelease(BuildConfig.GITHUB_REPOSITORY)
                val release = dto.toAppRelease()
                    ?: return@withContext Result.Error(
                        DataError.Parse(
                            message = "El release publicado no declara una versión legible",
                        ),
                    )

                if (persisted.downloadId > 0L && persisted.latestVersion != release.versionName) {
                    removeStaleDownload(persisted.downloadId)
                }

                updateStateDataSource.saveCheck(
                    latestVersion = release.versionName,
                    latestTitle = release.title.orEmpty(),
                    latestPageUrl = release.pageUrl.orEmpty(),
                    latestDownloadUrl = release.downloadUrl.orEmpty(),
                    latestSizeBytes = release.downloadSizeBytes,
                    latestPublishedAtMillis = release.publishedAtMillis ?: 0L,
                    checkedAtMillis = now,
                )

                val saved = updateStateDataSource.state.first()
                logger.debug(TAG, "Versión publicada: ${release.versionName}")
                Result.Success(saved.toUpdateStatus(installedVersion = installedVersion))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                logger.warn(TAG, "Comprobación de versión falló: ${exception.message}", exception)
                Result.Error(errorMapper.map(exception))
            }
        }

    override suspend fun downloadUpdate(): Result<Long> = withContext(ioDispatcher) {
        val persisted = updateStateDataSource.state.first()
        val url = persisted.latestDownloadUrl.takeIf { it.isNotBlank() }
            ?: return@withContext Result.Error(
                DataError.EmptyCache(message = "No hay una actualización comprobada que descargar"),
            )

        val fileName = apkFileName(version = persisted.latestVersion, url = url)

        try {
            val downloadId = downloader.enqueue(
                url = url,
                fileName = fileName,
                title = "DivTrack ${persisted.latestVersion}".trim(),
            )
            updateStateDataSource.saveDownload(downloadId = downloadId, fileName = fileName)
            logger.debug(TAG, "Descarga encolada (id $downloadId): $fileName")
            Result.Success(downloadId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            logger.warn(TAG, "No se pudo encolar la descarga: ${exception.message}", exception)
            Result.Error(
                DataError.Unknown(
                    message = "No se pudo iniciar la descarga",
                    cause = exception,
                ),
            )
        }
    }

    override suspend fun pollDownload(): UpdateDownload = withContext(ioDispatcher) {
        val persisted = updateStateDataSource.state.first()
        if (persisted.downloadId <= 0L) return@withContext UpdateDownload()

        val download = downloader.stateOf(persisted.downloadId)
        val resolved = download.copy(
            fileName = download.fileName
                ?: persisted.downloadFileName.takeIf { it.isNotBlank() },
        )

        when (resolved.phase) {
            // Terminada: se anota para que la pantalla pueda ofrecer el instalador incluso
            // después de cerrar y volver a abrir la app.
            DownloadPhase.READY -> if (!persisted.downloadReady) {
                updateStateDataSource.markDownloadReady(ready = true)
            }

            // El gestor ya no conoce esa descarga (el usuario la borró, o el sistema limpió
            // su base): el estado guardado es basura y se descarta.
            DownloadPhase.NONE -> updateStateDataSource.clearDownload()

            DownloadPhase.PENDING,
            DownloadPhase.RUNNING,
            DownloadPhase.FAILED,
            -> Unit
        }

        resolved
    }

    /**
     * Borra el APK de una versión que ya no es la publicada.
     *
     * Nunca interrumpe la comprobación: si el borrado falla (el gestor no está, el archivo
     * ya no existe), se anota y se sigue. Lo que importa es que la versión nueva quede
     * guardada.
     */
    private suspend fun removeStaleDownload(downloadId: Long) {
        try {
            downloader.remove(downloadId)
            logger.debug(TAG, "Descarga anterior ($downloadId) descartada: hay versión nueva")
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            logger.warn(TAG, "No se pudo borrar la descarga anterior: ${exception.message}", exception)
        }
    }

    private fun isWithinFreshnessWindow(checkedAtMillis: Long, nowMillis: Long): Boolean =
        checkedAtMillis > 0L &&
            nowMillis - checkedAtMillis < AppConstants.UPDATE_CHECK_MIN_INTERVAL_MILLIS

    /**
     * Nombre del archivo descargado: el del APK publicado (`DivTrack-debug.apk`) y, si la
     * URL no lo dijera, uno construido con la versión. Se filtran los caracteres raros porque
     * el nombre acaba en una ruta del sistema de archivos.
     */
    private fun apkFileName(version: String, url: String): String {
        val fromUrl = url.substringAfterLast('/')
            .substringBefore('?')
            .filter { character -> character.isLetterOrDigit() || character in ALLOWED_NAME_CHARS }

        return fromUrl.takeIf { it.endsWith(APK_SUFFIX, ignoreCase = true) }
            ?: "DivTrack-${version.ifBlank { FALLBACK_FILE_VERSION }}.apk"
    }

    private companion object {
        const val TAG = "UpdateRepository"
        const val APK_SUFFIX = ".apk"
        const val FALLBACK_FILE_VERSION = "update"
        const val ALLOWED_NAME_CHARS = "-_."
    }
}
