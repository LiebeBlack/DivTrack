package com.liebeblack.divtrack.data.downloader

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.liebeblack.divtrack.core.common.utils.AppConstants
import com.liebeblack.divtrack.domain.model.DownloadPhase
import com.liebeblack.divtrack.domain.model.UpdateDownload
import com.liebeblack.divtrack.domain.repository.AppDownloader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Descarga del APK con `DownloadManager`, el gestor del sistema.
 *
 * **Por qué el gestor del sistema y no OkHttp.** La descarga tiene que sobrevivir a que el
 * usuario cierre la app o el fabricante mate el proceso — eso es «en segundo plano» de
 * verdad—, y `DownloadManager` es el único mecanismo que lo hace sin permisos nuevos, sin
 * servicio en primer plano y sin que la app tenga que seguir viva para reanudar si se corta
 * la conexión. OkHttp dentro de un `CoroutineWorker` habría obligado a gestionar reanudación,
 * progreso y reintentos a mano para acabar en el mismo sitio.
 *
 * **Dónde baja el archivo.** En la carpeta privada de la app
 * (`Android/data/<paquete>/files/Download/`), no en la carpeta pública de descargas: así no
 * se le pide al usuario ningún permiso de almacenamiento y el APK no queda tirado en un
 * directorio compartido. Esa carpeta es exactamente la que cubre el `FileProvider` de
 * `:app` para lanzar el instalador.
 *
 * **Qué se consulta.** El estado y el progreso se leen de la propia base del gestor
 * (`COLUMN_STATUS`, `COLUMN_BYTES_DOWNLOADED_SO_FAR`, `COLUMN_TOTAL_SIZE_BYTES`), sin abrir
 * ninguna conexión: sondeo barato, y solo mientras la descarga está viva.
 */
@Singleton
class DownloadManagerAppDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppDownloader {

    override suspend fun enqueue(url: String, fileName: String, title: String): Long {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setMimeType(AppConstants.APK_MIME_TYPE)
            // Notificación del sistema gestionada por el propio gestor de descargas: la app
            // no publica notificaciones (ni declara el permiso) y el usuario ve el avance.
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
            )
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                fileName,
            )
            // Con datos móviles sí (es una actualización que el usuario pidió), en roaming no.
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        return manager().enqueue(request)
    }

    override suspend fun stateOf(downloadId: Long): UpdateDownload {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = manager().query(query) ?: return UpdateDownload()

        return cursor.use { result ->
            if (!result.moveToFirst()) return@use UpdateDownload()

            val status = result.getInt(result.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = result.getLong(
                result.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
            )
            val total = result.getLong(
                result.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
            )
            val localUri = result.getString(
                result.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI),
            )

            UpdateDownload(
                phase = status.toPhase(),
                // El nombre real del archivo sale de la URI local del gestor: es el dato que
                // no puede discrepar con lo que hay en el disco.
                fileName = localUri?.substringAfterLast('/')?.takeIf { it.isNotBlank() },
                bytesDownloaded = downloaded.coerceAtLeast(0L),
                totalBytes = total.coerceAtLeast(0L),
            )
        }
    }

    override suspend fun remove(downloadId: Long) {
        // Cuidado con el orden: borrar la entrada del gestor borra también el archivo.
        manager().remove(downloadId)
    }

    private fun manager(): DownloadManager = context.getSystemService(DownloadManager::class.java)

    /**
     * `PAUSED` cuenta como descarga viva (el gestor puede reanudarla sola): lo que la UI
     * necesita saber es si todavía hay trabajo en curso, no si el estado interno es «pausado».
     */
    private fun Int.toPhase(): DownloadPhase = when (this) {
        DownloadManager.STATUS_PENDING -> DownloadPhase.PENDING
        DownloadManager.STATUS_RUNNING -> DownloadPhase.RUNNING
        DownloadManager.STATUS_PAUSED -> DownloadPhase.RUNNING
        DownloadManager.STATUS_SUCCESSFUL -> DownloadPhase.READY
        DownloadManager.STATUS_FAILED -> DownloadPhase.FAILED
        else -> DownloadPhase.NONE
    }
}
