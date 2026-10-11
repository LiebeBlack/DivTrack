package com.liebeblack.divtrack.data.mapper

import com.liebeblack.divtrack.core.common.utils.VersionNames
import com.liebeblack.divtrack.core.datastore.model.UpdateState
import com.liebeblack.divtrack.core.network.dto.GithubAssetDto
import com.liebeblack.divtrack.core.network.dto.GithubReleaseDto
import com.liebeblack.divtrack.domain.model.AppRelease
import com.liebeblack.divtrack.domain.model.DownloadPhase
import com.liebeblack.divtrack.domain.model.UpdateAvailability
import com.liebeblack.divtrack.domain.model.UpdateDownload
import com.liebeblack.divtrack.domain.model.UpdateStatus
import java.time.Instant

/**
 * Release de GitHub -> modelo de dominio.
 *
 * Tres reglas que no son obvias y están aquí por eso:
 *
 * 1. **La versión se busca, no se supone.** El release rolling de este proyecto se titula
 *    `DivTrack 4.7 (latest)` y su etiqueta es `latest`, sin números: la versión se extrae del
 *    título y, si faltara, del cuerpo de las notas (que empieza con `## DivTrack 4.7`). Un
 *    release sin ningún número legible devuelve `null` y la comprobación lo cuenta como
 *    fallo en vez de inventarse una versión.
 * 2. **El APK principal, nunca el de Lite.** El release publica dos APKs
 *    (`DivTrack-release.apk` / `DivTrack-debug.apk` y `apklite.apk`). Se descarta cualquier
 *    adjunto «lite» y se prefiere el canal release sobre el debug: instalar el APK de Lite
 *    encima de esta app no funcionaría (son aplicaciones distintas) y ofrecerlo sería un
 *    botón que rompe.
 * 3. **Un release sin APK sigue siendo un release.** [AppRelease.downloadUrl] queda `null` y
 *    la pantalla lo cuenta, en lugar de ofrecer una descarga que daría 404.
 */
internal fun GithubReleaseDto.toAppRelease(): AppRelease? {
    val version = VersionNames.firstVersionOf(name, body, tagName) ?: return null
    val asset = mainApkAsset()

    return AppRelease(
        versionName = version,
        title = name?.takeIf { it.isNotBlank() },
        pageUrl = htmlUrl?.takeIf { it.isNotBlank() },
        downloadUrl = asset?.downloadUrl?.takeIf { it.isNotBlank() },
        downloadSizeBytes = asset?.size ?: 0L,
        publishedAtMillis = publishedAtMillisOrNull(publishedAt),
    )
}

/**
 * Adjunto del APK principal. El orden de preferencia es release → debug → cualquiera que no
 * sea de Lite, que es el mismo orden con el que CI elige lo que publica.
 */
private fun GithubReleaseDto.mainApkAsset(): GithubAssetDto? {
    val apks = assets.filter { asset ->
        asset.name.endsWith(APK_SUFFIX, ignoreCase = true) &&
            !asset.name.contains(LITE_MARKER, ignoreCase = true) &&
            !asset.downloadUrl.isNullOrBlank()
    }

    return apks.firstOrNull { asset -> asset.name.contains(RELEASE_MARKER, ignoreCase = true) }
        ?: apks.firstOrNull { asset -> asset.name.contains(DEBUG_MARKER, ignoreCase = true) }
        ?: apks.firstOrNull()
}

/**
 * `published_at` de GitHub viene siempre en UTC con `Z`
 * (`2026-10-04T12:00:00Z`), así que `Instant.parse` basta. Se protege con `runCatching`
 * porque una fecha ilegible **no** debe impedir ofrecer la actualización: el instante de
 * publicación es un adorno de la pantalla, no el dato que decide.
 */
private fun publishedAtMillisOrNull(raw: String?): Long? = try {
    Instant.parse(raw.orEmpty().trim()).toEpochMilli()
} catch (_: Exception) {
    null
}

/**
 * Estado persistido + versión instalada -> estado de dominio.
 *
 * Es la única conversión que necesita el módulo: tanto el flujo de observación como la
 * comprobación que acaba de responder se leen del mismo sitio (lo guardado), así que la
 * pantalla nunca puede mostrar algo que no se pueda volver a leer mañana.
 */
internal fun UpdateState.toUpdateStatus(installedVersion: String): UpdateStatus {
    val latest = latestVersion.takeIf { it.isNotBlank() }

    return UpdateStatus(
        installedVersion = installedVersion,
        latestVersion = latest,
        availability = availabilityOf(latestVersion = latest, installedVersion = installedVersion),
        release = latest?.let { version ->
            AppRelease(
                versionName = version,
                title = latestTitle.ifBlank { null },
                pageUrl = latestPageUrl.ifBlank { null },
                downloadUrl = latestDownloadUrl.ifBlank { null },
                downloadSizeBytes = latestSizeBytes,
                publishedAtMillis = latestPublishedAtMillis.takeIf { it > 0L },
            )
        },
        checkedAtMillis = checkedAtMillis.takeIf { it > 0L },
        download = UpdateDownload(
            phase = when {
                downloadId <= 0L -> DownloadPhase.NONE
                downloadReady -> DownloadPhase.READY
                else -> DownloadPhase.PENDING
            },
            fileName = downloadFileName.ifBlank { null },
            // El progreso lo aporta el sondeo al gestor del sistema, no el estado guardado:
            // aquí no se inventan bytes descargados.
            bytesDownloaded = 0L,
            totalBytes = 0L,
        ),
    )
}

/**
 * `UNKNOWN` cuando falta cualquiera de los dos datos. Sin versión instalada legible no se
 * puede afirmar «estás al día»: sería una respuesta inventada.
 */
private fun availabilityOf(latestVersion: String?, installedVersion: String): UpdateAvailability = when {
    latestVersion == null || installedVersion.isBlank() -> UpdateAvailability.UNKNOWN
    VersionNames.isNewer(candidate = latestVersion, installed = installedVersion) ->
        UpdateAvailability.UPDATE_AVAILABLE
    else -> UpdateAvailability.UP_TO_DATE
}

private const val APK_SUFFIX = ".apk"
private const val LITE_MARKER = "lite"
private const val RELEASE_MARKER = "release"
private const val DEBUG_MARKER = "debug"
