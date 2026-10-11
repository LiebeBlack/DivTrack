package com.liebeblack.divtrack.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET https://api.github.com/repos/{owner}/{repo}/releases/latest`
 *
 * Respuesta real de la API pública v3 (documentada, no un endpoint de terceros): publica el
 * último release no borrador y no preliminar del repositorio. Este proyecto publica **un
 * release rolling** por cada push a `main` (ver `.github/workflows/android-ci.yml`), así
 * que ese endpoint siempre apunta al APK vigente.
 *
 * Solo se modelan los campos que la app usa. `ignoreUnknownKeys` está activo en el `Json`
 * compartido (NetworkModule), de modo que un campo nuevo de GitHub no puede romper la
 * comprobación.
 *
 * @param tagName etiqueta del release. En este flujo vale `latest` (sin números), así que
 *   **no** es una fuente de versión: la versión legible va en [name] y, si faltara, en [body].
 * @param body notas del release en Markdown. Su primera línea es `## DivTrack <versión>`.
 * @param assets archivos adjuntos; aquí vive el APK y su URL de descarga.
 */
@Serializable
data class GithubReleaseDto(
    @SerialName("tag_name") val tagName: String? = null,
    val name: String? = null,
    val body: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAssetDto> = emptyList(),
)

/** Un adjunto del release: nombre del archivo, URL de descarga directa y tamaño en bytes. */
@Serializable
data class GithubAssetDto(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String? = null,
    val size: Long = 0L,
    @SerialName("content_type") val contentType: String? = null,
)
