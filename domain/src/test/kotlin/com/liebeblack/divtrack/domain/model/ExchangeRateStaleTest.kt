package com.liebeblack.divtrack.domain.model

import com.liebeblack.divtrack.core.common.utils.AppConstants
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Detección de "banco que dejó de publicar": la fuente responde 200 con JSON perfecto
 * sirviendo el cierre de ayer. Es exactamente el caso que se quiere destapar.
 */
class ExchangeRateStaleTest {

    /** Umbrales reales del proyecto: el test mide la regla, no una copia de los números. */
    private val officialThresholdMillis = MILLIS_PER_HOUR * AppConstants.STALE_RATE_HOURS

    /** "Ahora" del test: justo por encima del umbral del oficial. */
    private val now = officialThresholdMillis + 10L

    @Test
    fun `el oficial mas viejo que su umbral es rancio`() {
        val rate = rate(RateSource.OFICIAL, ageMillis = officialThresholdMillis + 10L)

        assertTrue(rate.isStale(nowMillis = now))
    }

    @Test
    fun `el oficial dentro del umbral no es rancio`() {
        val rate = rate(RateSource.OFICIAL, ageMillis = officialThresholdMillis - 1)

        assertFalse(rate.isStale(nowMillis = now))
    }

    @Test
    fun `el fin de semana del banco no dispara el aviso`() {
        // El cierre del viernes, visto un domingo por la tarde: 48 h sin publicar y sigue
        // siendo lo normal en el BCV. Con un umbral de 24 h esto mostraba una falsa alarma
        // cada fin de semana.
        val rate = rate(RateSource.OFICIAL, ageMillis = 48L * MILLIS_PER_HOUR)

        assertFalse(rate.isStale(nowMillis = now))
    }

    @Test
    fun `el paralelo tiene un umbral mas estricto`() {
        // Fresco para el oficial (días), ya rancio para el paralelo (12 h).
        val rate = rate(RateSource.PARALELO, ageMillis = officialThresholdMillis - 1)

        assertTrue(rate.isStale(nowMillis = now))
    }

    @Test
    fun `sin marca de tiempo no se supone rancio`() {
        val rate = rate(RateSource.OFICIAL, ageMillis = null)

        // Yadio no publica marca: suponer rancio mostraría un aviso falso.
        assertFalse(rate.isStale(nowMillis = now))
    }

    @Test
    fun `el paralelo dentro de su umbral no es rancio`() {
        val rate = rate(RateSource.PARALELO, ageMillis = 2L * MILLIS_PER_HOUR)

        assertFalse(rate.isStale(nowMillis = now))
    }

    private fun rate(source: RateSource, ageMillis: Long?): ExchangeRate = ExchangeRate(
        source = source,
        value = 860.18,
        previousClose = null,
        providerId = "DolarAPI",
        updatedAtMillis = ageMillis?.let { now - it },
        fetchedAtMillis = 0L,
    )

    private companion object {
        const val MILLIS_PER_HOUR = 3_600_000L
    }
}
