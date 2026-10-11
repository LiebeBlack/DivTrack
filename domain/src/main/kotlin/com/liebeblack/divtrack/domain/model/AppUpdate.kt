package com.liebeblack.divtrack.domain.model

/** Estado de la comprobación de versión. */
enum class UpdateAvailability {

    /** Nunca se ha podido comprobar (sin red y sin dato guardado). */
    UNKNOWN,

    /** La versión instalada es la última publicada. */
    UP_TO_DATE,

    /** Hay una versión más nueva publicada y se puede ofrecer. */
    UPDATE_AVAILABLE,
}

/** Fase de la descarga del APK en el gestor del sistema. */
enum class DownloadPhase {

    /** No hay ninguna descarga asociada. */
    NONE,

    /** Encolada: el sistema la arrancará cuando tenga red. */
    PENDING,

    /** Descargando. */
    RUNNING,

    /** Terminada: el APK está en el dispositivo y se puede lanzar el instalador. */
    READY,

    /** El sistema la dio por fallida (sin red, sin espacio, URL rota). */
    FAILED,
}

/**
 * Release publicado que la app puede ofrecer instalar.
 *
 * @param versionName versión normalizada y legible ("4.7"); es la que se compara con la
 *   instalada. El título del release trae texto decorativo y la etiqueta del release rolling
 *   vale `latest`, así que la comparación **nunca** se hace contra esos campos.
 * @param downloadUrl APK principal. `null` cuando el release existe pero no adjuntó APK:
 *   es un caso real (un release a medio publicar) y la UI lo cuenta en vez de ofrecer un
 *   botón que lleva a un 404.
 * @param pageUrl página del release, para leer las novedades en el navegador. Las notas
 *   viajan en Markdown y no se guardan: se enlazan, que es lo que el usuario puede leer.
 * @param publishedAtMillis instante de publicación; `null` si GitHub no lo dio o no se pudo
 *   leer.
 */
data class AppRelease(
    val versionName: String,
    val title: String? = null,
    val pageUrl: String? = null,
    val downloadUrl: String? = null,
    val downloadSizeBytes: Long = 0L,
    val publishedAtMillis: Long? = null,
)

/** Descarga del APK: fase, archivo y progreso. */
data class UpdateDownload(
    val phase: DownloadPhase = DownloadPhase.NONE,
    val fileName: String? = null,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
) {
    val isActive: Boolean
        get() = phase == DownloadPhase.PENDING || phase == DownloadPhase.RUNNING

    /**
     * Fracción descargada (0..1) o `null` cuando todavía no se sabe el tamaño total.
     * `null` es información: la UI pinta una barra indeterminada en lugar de un 0 % mentiroso.
     */
    val progressFraction: Float?
        get() = if (isActive && totalBytes > 0L) {
            (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            null
        }
}

/**
 * Todo lo que la pantalla necesita saber de las actualizaciones, ya interpretado.
 *
 * @param installedVersion versión del APK que está ejecutándose.
 * @param latestVersion última publicada, o `null` si todavía no se sabe.
 * @param checkedAtMillis instante de la última comprobación que respondió; `null` = ninguna.
 *   Es la fecha **real**, y es lo que mantiene honesto al estado guardado: la pantalla nunca
 *   finge que acaba de preguntar (cuando está preguntando, lo dice el estado de «comprobando»
 *   de la propia pantalla).
 */
data class UpdateStatus(
    val installedVersion: String,
    val latestVersion: String? = null,
    val availability: UpdateAvailability = UpdateAvailability.UNKNOWN,
    val release: AppRelease? = null,
    val checkedAtMillis: Long? = null,
    val download: UpdateDownload = UpdateDownload(),
)
