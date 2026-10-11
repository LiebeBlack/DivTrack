package com.liebeblack.divtrack.core.network.service

import com.liebeblack.divtrack.core.network.dto.GithubReleaseDto
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Último release publicado del repositorio (API pública de GitHub, sin autenticación).
 *
 * Tres detalles que hacen que la consulta sea barata y segura:
 *
 * 1. **Una sola petición y un solo JSON.** El endpoint devuelve el release vigente con sus
 *    adjuntos; no hay que listar releases ni paginar.
 * 2. **Revalidación por `ETag`.** `HeadersInterceptor` manda `Cache-Control: no-cache` en
 *    todas las peticiones, así que OkHttp revalida contra el servidor en lugar de servir una
 *    copia vieja; GitHub responde `304 Not Modified` (sin cuerpo) cuando el release no ha
 *    cambiado, y la respuesta guardada se reutiliza. Es la forma estándar de que la
 *    comprobación cueste bytes casi cero entre versiones.
 * 3. **Degradación a caché.** Los interceptores compartidos intentan la caché en disco antes
 *    de propagar un fallo de red, de modo que una comprobación sin conexión todavía puede
 *    contar lo último que se supo del release.
 *
 * @param repository slug `owner/repo`. Viaja con `encoded = true` para que la barra del
 *   camino no se escape (`repos/LiebeBlack/b/releases/latest`). El slug por defecto apunta al
 *   repositorio real de la app (`LiebeBlack/b`); cambiarlo es cosa de `local.properties`
 *   (`github.repository`), y un slug que no exista se cuenta como fallo con su causa real
 *   (HTTP 404), no como «sin conexión».
 */
interface GithubReleasesService {

    @GET("repos/{repository}/releases/latest")
    suspend fun getLatestRelease(
        @Path(value = "repository", encoded = true) repository: String,
    ): GithubReleaseDto
}
