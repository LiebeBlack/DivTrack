package com.liebeblack.divtrack.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET https://api.yadio.io/exrates/USD`
 *
 * Respuesta real verificada (2026-10-11, mapa de divisas):
 * ```json
 * { "BTC": 83590.41,
 *   "USD": { "BTC": 0.000011963095, "...": "...", "USD": 1, "VES": 1009.79784 },
 *   "base": "USD",
 *   "timestamp": 1791750610972 }
 * ```
 * Yadio es la fuente upstream que publica el paralelo, así que sirve de fallback natural
 * cuando DolarAPI no responde. Solo se consumen `USD["VES"]` y `timestamp`; el resto se
 * ignora (y con `ignoreUnknownKeys` un campo nuevo no puede romper la pasada).
 *
 * @param timestamp marca de tiempo de la cotización en **milisegundos** Unix, en la raíz de
 *   la respuesta. Se modela porque es el único proveedor de respaldo que publica la hora del
 *   dato: con ella, la resolución por frescura y el aviso de "fuente que dejó de publicar"
 *   funcionan igual que con DolarAPI. Si el campo faltara, `updatedAtMillis` queda `null`
 *   (desconocido) y el registro lo trata como "no se supone rancio".
 */
@Serializable
data class YadioRatesDto(
    // El valor del mapa es anulable a propósito: Yadio publica más de 150 divisas y basta con
    // que una traiga `null` para que un `Map<String, Double>` estricto tumbe la respuesta
    // entera —y con ella el paralelo, que es justo el dato que este proveedor aporta—.
    @SerialName("USD") val usd: Map<String, Double?> = emptyMap(),
    val timestamp: Long? = null,
) {
    val ves: Double? get() = usd[VES_KEY]

    /**
     * Marca de tiempo utilizable: se descartan los valores no positivos (un `0` sería una
     * fecha de 1970 y marcaría el dato como rancio por error).
     */
    val updatedAtMillis: Long? get() = timestamp?.takeIf { it > 0L }

    companion object {
        const val VES_KEY: String = "VES"
    }
}
