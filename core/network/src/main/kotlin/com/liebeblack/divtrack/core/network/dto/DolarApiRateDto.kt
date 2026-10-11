package com.liebeblack.divtrack.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET https://ve.dolarapi.com/v1/dolares`
 *
 * Respuesta real verificada (2026-09-30):
 * ```json
 * [ { "moneda":"USD", "fuente":"oficial", "nombre":"Dólar",
 *     "compra":null, "venta":null, "promedio":859.0629,
 *     "fechaActualizacion":"2026-09-30T00:00:00-04:00" } ]
 * ```
 * El dólar oficial pública `promedio` (compra/venta vienen nulos), por eso [usableValue]
 * degrada con elegancia: promedio -> venta -> compra.
 */
@Serializable
data class DolarApiRateDto(
    val moneda: String? = null,
    /**
     * Clave de la tasa (`oficial` / `paralelo`). Es anulable a propósito: si el proveedor
     * omitiera el campo (o mandara `null`) y esto fuera no-anulable, kotlinx.serialization
     * tumbaría **toda** la respuesta y la app se quedaría sin la tasa oficial por un detalle
     * de un elemento. Con `null` el proveedor descarta ese elemento y sigue con el resto.
     */
    val fuente: String? = null,
    val nombre: String? = null,
    val compra: Double? = null,
    val venta: Double? = null,
    val promedio: Double? = null,
    @SerialName("fechaActualizacion") val fechaActualizacion: String? = null,
) {
    val usableValue: Double?
        get() = promedio ?: venta ?: compra
}
