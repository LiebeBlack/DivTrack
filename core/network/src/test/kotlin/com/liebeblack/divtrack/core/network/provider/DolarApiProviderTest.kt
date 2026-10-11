package com.liebeblack.divtrack.core.network.provider

import com.liebeblack.divtrack.core.common.time.TimeProvider
import com.liebeblack.divtrack.core.common.utils.SourceKeys
import com.liebeblack.divtrack.core.network.dto.DolarApiRateDto
import com.liebeblack.divtrack.core.network.service.DolarApiService
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * Reparto de llamadas del proveedor primario: el oficial lo pide al endpoint **dedicado**
 * (`v1/dolares/oficial`) y el paralelo a la **lista** (`v1/dolares`), con respaldo cruzado.
 *
 * Lo que se fija aquí es el contrato que la app tiene con su fuente principal: una llamada
 * caída no puede arrastrar a la otra, y el valor del dedicado es el que manda cuando los
 * dos responden.
 */
class DolarApiProviderTest {

    @Test
    fun `el dedicado aporta el oficial y la lista el paralelo, sin duplicar`() = runBlocking {
        val result = provider(
            official = Result.success(
                dto(fuente = "oficial", promedio = 876.0, fecha = "2026-10-13T00:00:00-04:00"),
            ),
            list = Result.success(
                listOf(
                    // El mismo oficial, más viejo: no debe publicarse dos veces.
                    dto(fuente = "oficial", promedio = 875.6505, fecha = "2026-10-09T00:00:00-04:00"),
                    dto(fuente = "paralelo", promedio = 955.706247, fecha = "2026-10-13T12:01:34.203Z"),
                ),
            ),
        ).fetchLatest()

        assertEquals(2, result.rates.size)
        val oficial = result.rates.single { rate -> rate.sourceKey == SourceKeys.OFICIAL }
        // Manda el endpoint dedicado: es el que se pidió a propósito, no el de la lista.
        assertEquals(876.0, oficial.value, 0.000001)
        // Medianoche de Venezuela del 13 (04:00 UTC), como lo fecha DolarAPI para el oficial.
        assertEquals(Instant.parse("2026-10-13T04:00:00Z").toEpochMilli(), oficial.updatedAtMillis)
        val paralelo = result.rates.single { rate -> rate.sourceKey == SourceKeys.PARALELO }
        assertEquals(955.706247, paralelo.value, 0.000001)
    }

    @Test
    fun `si la dedicada falla, la lista respalda el oficial`() = runBlocking {
        val result = provider(
            official = Result.failure(IOException("dedicada caída")),
            list = Result.success(
                listOf(dto(fuente = "oficial", promedio = 875.6505, fecha = "2026-10-09T00:00:00-04:00")),
            ),
        ).fetchLatest()

        assertEquals(875.6505, result.rates.single().value, 0.000001)
    }

    @Test
    fun `si la lista falla, el oficial llega igual`() = runBlocking {
        val result = provider(
            official = Result.success(
                dto(fuente = "oficial", promedio = 876.0, fecha = "2026-10-13T00:00:00-04:00"),
            ),
            list = Result.failure(IOException("lista caída")),
        ).fetchLatest()

        assertEquals(1, result.rates.size)
        assertEquals(876.0, result.rates.single().value, 0.000001)
    }

    @Test
    fun `si fallan las dos se propaga el fallo del oficial`() = runBlocking {
        val provider = provider(
            official = Result.failure(IOException("dedicada caída")),
            list = Result.failure(IOException("lista caída")),
        )

        try {
            provider.fetchLatest()
            fail("Debía lanzar el fallo del oficial, no devolver tasas vacías")
        } catch (failure: IOException) {
            assertEquals("dedicada caída", failure.message)
        }
    }

    @Test
    fun `un elemento sin fuente se descarta sin arrastrar a los demás`() = runBlocking {
        val result = provider(
            official = Result.failure(IOException("dedicada caída")),
            list = Result.success(
                listOf(
                    dto(fuente = null, promedio = 900.0),
                    dto(fuente = "paralelo", promedio = 955.706247),
                ),
            ),
        ).fetchLatest()

        // Sin clave de fuente no se puede decir a qué tasa pertenece el valor: se descarta
        // ese elemento y el paralelo sigue llegando.
        assertEquals(1, result.rates.size)
        assertEquals(SourceKeys.PARALELO, result.rates.single().sourceKey)
    }

    private fun provider(
        official: Result<DolarApiRateDto>,
        list: Result<List<DolarApiRateDto>>,
    ): DolarApiProvider = DolarApiProvider(
        service = FakeService(official = official, list = list),
        timeProvider = FakeClock,
    )

    private fun dto(fuente: String?, promedio: Double, fecha: String? = null): DolarApiRateDto =
        DolarApiRateDto(moneda = "USD", fuente = fuente, nombre = "Dólar", promedio = promedio, fechaActualizacion = fecha)

    private class FakeService(
        private val official: Result<DolarApiRateDto>,
        private val list: Result<List<DolarApiRateDto>>,
    ) : DolarApiService {
        override suspend fun getOfficial(): DolarApiRateDto = official.getOrThrow()
        override suspend fun getDollars(): List<DolarApiRateDto> = list.getOrThrow()
    }

    private object FakeClock : TimeProvider {
        override fun nowMillis(): Long = FAKE_NOW_MILLIS
        override fun nowInstant(): Instant = Instant.ofEpochMilli(FAKE_NOW_MILLIS)
        override fun today(): LocalDate = LocalDate.of(2026, 10, 13)
        override fun zone(): ZoneId = ZoneId.of("America/Caracas")
    }

    private companion object {
        /** 2026-10-13 00:00 en Venezuela (= 04:00 UTC): el mismo cierre del fixture. */
        const val FAKE_NOW_MILLIS: Long = 1_791_864_000_000L
    }
}
