package com.liebeblack.divtrack.core.network.service

import com.liebeblack.divtrack.core.network.dto.DolarVzlaBcvDto
import retrofit2.http.GET

/**
 * DolarVZLA (CDN estático de tasas del Banco Central de Venezuela).
 *
 * Los paths son relativos a `BuildConfig.DOLARVZLA_BASE_URL` (inyectado desde
 * `local.properties`, sin URLs en el código, igual que el resto de proveedores).
 */
interface DolarVzlaService {

    /**
     * Última tasa oficial BCV publicada, en bolívares por dólar.
     *
     * Un solo archivo JSON estático: no hay parámetros, ni clave, ni límite de uso. La
     * fuente de esos datos es el propio BCV, así que llega aunque DolarAPI deje de
     * refrescar su espejo del sitio.
     */
    @GET("bcv/current.json")
    suspend fun getCurrentBcvRate(): DolarVzlaBcvDto
}
