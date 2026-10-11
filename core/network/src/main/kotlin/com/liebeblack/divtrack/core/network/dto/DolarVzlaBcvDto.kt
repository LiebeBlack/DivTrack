package com.liebeblack.divtrack.core.network.dto

import kotlinx.serialization.Serializable

/**
 * `GET https://rates.dolarvzla.com/bcv/current.json`
 *
 * Respuesta real verificada en vivo (2026-10-11):
 * ```json
 * {
 *   "current":            { "date": "2026-10-13", "usd": 876.7976, "eur": 982.00454402 },
 *   "previous":           { "date": "2026-10-09", "usd": 875.6505, "eur": 980.61472543 },
 *   "changePercentage":   { "usd": 0.13099975389724816, "eur": 0.14172932079829498 }
 * }
 * ```
 *
 * Es un archivo **estático** servido desde CDN: sin API key, sin rate limit y sin la
 * latencia de quien raspa bcv.org.ve en cada consulta (los bloques `previous` y
 * `changePercentage` los calcula el generador del sitio, no el cliente).
 *
 * La moneda de interés es `usd` (Bs por dólar, la misma unidad que el resto de la app).
 * `eur` y los bloques que no usa la app se modelan solo en la documentación de arriba:
 * `ignoreUnknownKeys` los descarta, y el test de parseo fija que así sea.
 *
 * Todo es anulable a propósito: si un día el sitio cambia la forma del JSON, la app
 * recibe una tasa parcial (o ninguna) y se queda con los demás proveedores, en vez de
 * que un campo obligatorio tumbe la respuesta entera.
 */
@Serializable
data class DolarVzlaBcvDto(
    /** Cotización vigente publicada por el BCV; `null` si la respuesta vino vacía. */
    val current: DolarVzlaBcvQuoteDto? = null,
)

/**
 * Una cotización del BCV: valor y **fecha** (no hora) del cierre.
 *
 * `date` viene como `YYYY-MM-DD`: el BCV publica un valor por día bancario. La app lo
 * convierte a medianoche de Caracas (misma regla que las fechas sin hora de otros
 * proveedores) para que la resolución por frescura compare fechas equivalentes.
 */
@Serializable
data class DolarVzlaBcvQuoteDto(
    /** Fecha ISO corta del cierre (`2026-10-13`). Anulable: una fecha ilegible da edad desconocida. */
    val date: String? = null,
    /** Tasa oficial en bolívares por dólar. Anulable: sin ella el proveedor no aporta nada. */
    val usd: Double? = null,
)
