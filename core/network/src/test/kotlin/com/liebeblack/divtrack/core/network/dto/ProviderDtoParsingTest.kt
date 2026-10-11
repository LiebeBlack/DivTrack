package com.liebeblack.divtrack.core.network.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parseo de los JSON **reales** capturados de cada proveedor (verificados en vivo contra
 * los endpoints). Cada fuente habla su propio dialecto; si alguno cambia de formato, el
 * test que lo fija se rompe y lo vemos antes que el usuario.
 */
class ProviderDtoParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun `dolarapi - lista con promedio para oficial y paralelo`() {
        val payload = """
            [
              {
                "moneda": "USD", "fuente": "oficial", "nombre": "Dólar",
                "compra": null, "venta": null, "promedio": 860.1753,
                "fechaActualizacion": "2026-10-01T00:00:00-04:00"
              },
              {
                "moneda": "USD", "fuente": "paralelo", "nombre": "Paralelo",
                "compra": null, "venta": null, "promedio": 955.706247,
                "fechaActualizacion": "2026-10-01T12:01:34.203Z"
              }
            ]
        """.trimIndent()

        val dtos = json.decodeFromString<List<DolarApiRateDto>>(payload)

        assertEquals(2, dtos.size)
        assertEquals(860.1753, dtos[0].usableValue!!, 0.000001)
        assertEquals(955.706247, dtos[1].usableValue!!, 0.000001)
        assertFalse(dtos[0].fechaActualizacion.isNullOrEmpty())
    }

    @Test
    fun `dolarapi - un elemento sin fuente no rompe el parseo de la lista`() {
        // Si `fuente` fuera obligatorio, un solo elemento raro tumbaría TODA la respuesta y la
        // app se quedaría sin el oficial. Se modela anulable y el proveedor lo descarta.
        val payload = """
            [
              { "moneda": "USD", "nombre": "Dólar", "promedio": 875.6505 },
              { "moneda": "USD", "fuente": "Oficial", "promedio": 876.0 }
            ]
        """.trimIndent()

        val dtos = json.decodeFromString<List<DolarApiRateDto>>(payload)

        assertEquals(2, dtos.size)
        assertNull(dtos[0].fuente)
        // La clave viaja tal cual: el proveedor la normaliza a minúsculas al mapear.
        assertEquals("Oficial", dtos[1].fuente)
    }

    @Test
    fun `dolarapi - degrada promedio luego venta luego compra`() {
        val soloVenta = json.decodeFromString<DolarApiRateDto>(
            """{ "fuente": "oficial", "compra": null, "venta": 100.5, "promedio": null }""",
        )
        val soloCompra = json.decodeFromString<DolarApiRateDto>(
            """{ "fuente": "oficial", "compra": 99.5, "venta": null, "promedio": null }""",
        )

        assertEquals(100.5, soloVenta.usableValue!!, 0.000001)
        assertEquals(99.5, soloCompra.usableValue!!, 0.000001)
    }

    @Test
    fun `yadio - mapa de divisas dentro de USD con su marca de tiempo`() {
        val payload = """
            { "BTC": 83590.41,
              "USD": { "BTC": 0.000011963095, "EUR": 0.885131, "USD": 1, "VES": 1009.79784 },
              "base": "USD",
              "timestamp": 1791750610972 }
        """.trimIndent()

        val dto = json.decodeFromString<YadioRatesDto>(payload)

        assertEquals(1009.79784, dto.ves!!, 0.000001)
        // El `timestamp` de Yadio es la marca de tiempo del dato (ms Unix) y viaja al registro:
        // sin ella, su lectura no competía por frescura con DolarAPI.
        assertEquals(1_791_750_610_972L, dto.updatedAtMillis!!)
    }

    @Test
    fun `yadio - sin par VES devuelve nulo en vez de romper`() {
        val dto = json.decodeFromString<YadioRatesDto>("""{ "USD": { "EUR": 0.885131 } }""")

        assertNull(dto.ves)
        // Sin marca de tiempo la edad es desconocida, no "de 1970": el registro no lo marca
        // rancio y la UI no inventa una fecha.
        assertNull(dto.updatedAtMillis)
    }

    @Test
    fun `yadio - un timestamp cero no se toma por una fecha valida`() {
        val dto = json.decodeFromString<YadioRatesDto>("""{ "USD": { "VES": 954.55 }, "timestamp": 0 }""")

        assertNull(dto.updatedAtMillis)
    }

    @Test
    fun `er-api - envoltorio con mapa plano y marca de tiempo en segundos`() {
        val payload = """
            { "result": "success",
              "time_last_update_unix": 1790812951,
              "base_code": "USD",
              "rates": { "USD": 1, "EUR": 0.881813, "VES": 860.1753 } }
        """.trimIndent()

        val dto = json.decodeFromString<ExchangeRateApiDto>(payload)

        assertFalse(dto.isFailure)
        assertEquals(860.1753, dto.rates[ExchangeRateApiDto.VES_KEY]!!, 0.000001)
        assertEquals(1_790_812_951_000L, dto.timeLastUpdateUnix!! * ExchangeRateApiDto.SECONDS_TO_MILLIS)
    }

    @Test
    fun `er-api - envoltorio de error se detecta sin explotar`() {
        val payload = """{ "result": "error", "error-type": "unsupported-code" }"""

        val dto = json.decodeFromString<ExchangeRateApiDto>(payload)

        assertTrue(dto.isFailure)
    }

    @Test
    fun `er-api - sin campo result no filtra (defensivo)`() {
        val dto = json.decodeFromString<ExchangeRateApiDto>("""{ "rates": { "VES": 860.0 } }""")

        assertFalse(dto.isFailure)
    }

    @Test
    fun `dolarvzla - cierre real del CDN con los bloques extra ignorados`() {
        val payload = """
            {
              "current": { "date": "2026-10-13", "usd": 876.7976, "eur": 982.00454402 },
              "previous": { "date": "2026-10-09", "usd": 875.6505, "eur": 980.61472543 },
              "changePercentage": { "usd": 0.13099975389724816, "eur": 0.14172932079829498 }
            }
        """.trimIndent()

        val dto = json.decodeFromString<DolarVzlaBcvDto>(payload)

        assertEquals(876.7976, dto.current?.usd!!, 0.000001)
        // La fecha llega como texto corto (`YYYY-MM-DD`): el proveedor la convierte a
        // medianoche de Venezuela antes de meterla en la comparación por frescura.
        assertEquals("2026-10-13", dto.current?.date)
    }

    @Test
    fun `dolarvzla - sin bloque current no rompe el parseo`() {
        // Un HTML de mantenimiento o un JSON vacío no deben tumbar el módulo: el proveedor
        // devuelve `rates` vacío, el registro lo cuenta como fallo y sigue con la cola.
        val dto = json.decodeFromString<DolarVzlaBcvDto>("""{ "previous": { "date": "2026-10-09" } }""")

        assertNull(dto.current)
    }

    @Test
    fun `dolarvzla - bloque current sin usd deja el valor en nulo`() {
        val dto = json.decodeFromString<DolarVzlaBcvDto>("""{ "current": { "date": "2026-10-13" } }""")

        assertNull(dto.current?.usd)
    }
}
