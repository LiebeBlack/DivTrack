package com.liebeblack.divtrack.core.network.provider

import com.liebeblack.divtrack.core.common.time.TimeProvider
import com.liebeblack.divtrack.core.common.utils.SourceKeys
import com.liebeblack.divtrack.core.network.dto.DolarVzlaBcvDto
import com.liebeblack.divtrack.core.network.dto.DolarVzlaBcvQuoteDto
import com.liebeblack.divtrack.core.network.service.DolarVzlaService
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mapeo del proveedor DolarVZLA: qué parte del JSON se convierte en tasa y qué se descarta.
 *
 * El contrato que se fija aquí es el que el registro usa para resolver por frescura: el
 * valor viaja en Bs por dólar y la fecha del cierre viaja como medianoche de Venezuela,
 * la misma convención con la que DolarAPI fecha el oficial (así la comparación es entre
 * iguales y gana la fecha más reciente, no la que tenga la zona más a favor).
 */
class DolarVzlaProviderTest {

    @Test
    fun `cierre valido se publica como oficial con la fecha en hora de Caracas`() = runBlocking {
        val result = provider(
            dto = DolarVzlaBcvDto(
                current = DolarVzlaBcvQuoteDto(date = "2026-10-13", usd = 876.7976),
            ),
        ).fetchLatest()

        val rate = result.rates.single()
        assertEquals(SourceKeys.OFICIAL, rate.sourceKey)
        assertEquals(876.7976, rate.value, 0.000001)
        // 2026-10-13 00:00 en Caracas (UTC-4) = 04:00 UTC. Leída en UTC, la misma fecha se
        // pintaría en la app como 12 de octubre y entraría al registro como un día más
        // vieja de lo que es.
        assertEquals(Instant.parse("2026-10-13T04:00:00Z").toEpochMilli(), rate.updatedAtMillis)
    }

    @Test
    fun `sin bloque current no se inventa ninguna tasa`() = runBlocking {
        val result = provider(DolarVzlaBcvDto(current = null)).fetchLatest()

        // `rates` vacío lo cuenta el registro como fallo de este proveedor y la pasada sigue
        // con la cola: no aportar nada es preferible a aportar un cero que rompa los cálculos.
        assertTrue(result.rates.isEmpty())
    }

    @Test
    fun `usd en cero o negativo se descarta en lugar de publicarse`() = runBlocking {
        val enCero = provider(
            DolarVzlaBcvDto(DolarVzlaBcvQuoteDto(date = "2026-10-13", usd = 0.0)),
        ).fetchLatest()
        val negativo = provider(
            DolarVzlaBcvDto(DolarVzlaBcvQuoteDto(date = "2026-10-13", usd = -5.0)),
        ).fetchLatest()

        assertTrue(enCero.rates.isEmpty())
        assertTrue(negativo.rates.isEmpty())
    }

    @Test
    fun `valor valido con fecha ilegible viaja sin marca de tiempo`() = runBlocking {
        val rate = provider(
            DolarVzlaBcvDto(DolarVzlaBcvQuoteDto(date = "ayer", usd = 880.0)),
        ).fetchLatest().rates.single()

        assertEquals(880.0, rate.value, 0.000001)
        // Edad desconocida, no "de 1970": el registro no la marca rancio y la UI no inventa
        // una fecha que el proveedor no dio.
        assertNull(rate.updatedAtMillis)
    }

    private fun provider(dto: DolarVzlaBcvDto): DolarVzlaProvider = DolarVzlaProvider(
        service = object : DolarVzlaService {
            override suspend fun getCurrentBcvRate(): DolarVzlaBcvDto = dto
        },
        timeProvider = FakeClock,
    )

    private object FakeClock : TimeProvider {
        override fun nowMillis(): Long = FAKE_NOW_MILLIS
        override fun nowInstant(): Instant = Instant.ofEpochMilli(FAKE_NOW_MILLIS)
        override fun today(): LocalDate = LocalDate.of(2026, 10, 13)
        override fun zone(): ZoneId = ZoneId.of("America/Caracas")
    }

    private companion object {
        /** 2026-10-13 00:00 en Venezuela (= 04:00 UTC): el mismo cierre que el fixture. */
        const val FAKE_NOW_MILLIS: Long = 1_791_864_000_000L
    }
}
