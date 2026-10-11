package com.liebeblack.divtrack.core.network.service

import com.liebeblack.divtrack.core.network.dto.DolarApiRateDto
import retrofit2.http.GET

/**
 * DolarAPI Venezuela. Los paths son relativos a `BuildConfig.DOLARAPI_BASE_URL`
 * (inyectado desde `local.properties`, sin URLs en el código).
 */
interface DolarApiService {

    /**
     * Dólar **oficial** del BCV: el endpoint dedicado, que es como se le pide al proveedor
     * el producto de esta app.
     *
     * Es la llamada principal del proveedor primario: responde el mismo valor que la lista,
     * pero en una sola tasa y sin el resto del payload, así que es más difícil que un
     * problema con el paralelo arrastre al oficial.
     *
     * Respuesta real verificada en vivo (2026-10-11): `{"moneda":"USD","fuente":"oficial",
     * "nombre":"Dólar","compra":null,"venta":null,"promedio":875.6505,
     * "fechaActualizacion":"2026-10-09T00:00:00-04:00"}`.
     */
    @GET("v1/dolares/oficial")
    suspend fun getOfficial(): DolarApiRateDto

    /**
     * Todas las tasas publicadas (oficial + paralelo) en una sola llamada.
     *
     * Aporta el **paralelo** —que no tiene endpoint dedicado— y sirve de respaldo del
     * oficial si la llamada dedicada falla. El histórico por fuente se eliminó con la
     * pantalla de Histórico, así que aquí no queda ninguna llamada de series diarias.
     */
    @GET("v1/dolares")
    suspend fun getDollars(): List<DolarApiRateDto>
}
