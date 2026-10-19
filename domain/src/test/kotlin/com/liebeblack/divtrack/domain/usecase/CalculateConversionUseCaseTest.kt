package com.liebeblack.divtrack.domain.usecase

import com.liebeblack.divtrack.domain.model.ConversionDirection
import com.liebeblack.divtrack.domain.model.RateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La calculadora es aritmética pura: se prueba el ejemplo base, ambos sentidos y los casos
 * que deben devolver `null`. Sin impuestos, el resultado es exactamente el monto por la
 * tasa (o el monto dividido por ella).
 */
class CalculateConversionUseCaseTest {

    private val useCase = CalculateConversionUseCase()

    @Test
    fun `dolares a bolivares multiplica por la tasa`() {
        val conversion = useCase(
            direction = ConversionDirection.USD_TO_BS,
            source = RateSource.OFICIAL,
            rate = 36.5,
            rawAmount = "10",
        )!!

        assertEquals(10.0, conversion.usdAmount, DELTA)
        assertEquals(365.0, conversion.bsAmount, DELTA)
    }

    @Test
    fun `bolivares a dolares divide por la tasa`() {
        val conversion = useCase(
            direction = ConversionDirection.BS_TO_USD,
            source = RateSource.OFICIAL,
            rate = 36.5,
            rawAmount = "365",
        )!!

        assertEquals(365.0, conversion.bsAmount, DELTA)
        assertEquals(10.0, conversion.usdAmount, DELTA)
    }

    @Test
    fun `acepta coma decimal en el monto`() {
        val conversion = useCase(
            direction = ConversionDirection.USD_TO_BS,
            source = RateSource.PARALELO,
            rate = 954.55,
            rawAmount = "100,50",
        )!!

        assertEquals(100.5, conversion.usdAmount, DELTA)
        assertEquals(100.5 * 954.55, conversion.bsAmount, DELTA)
    }

    @Test
    fun `sin monto no hay conversion`() {
        assertNull(useCase(ConversionDirection.USD_TO_BS, RateSource.OFICIAL, 36.5, ""))
        assertNull(useCase(ConversionDirection.USD_TO_BS, RateSource.OFICIAL, 36.5, "0"))
    }

    @Test
    fun `un monto no finito no convierte`() {
        assertNull(
            useCase(
                direction = ConversionDirection.USD_TO_BS,
                source = RateSource.OFICIAL,
                rate = 36.5,
                rawAmount = "9".repeat(400),
            ),
        )
    }

    @Test
    fun `sin tasa no hay conversion`() {
        assertNull(useCase(ConversionDirection.USD_TO_BS, RateSource.OFICIAL, null, "10"))
        assertNull(useCase(ConversionDirection.USD_TO_BS, RateSource.OFICIAL, 0.0, "10"))
        assertNull(useCase(ConversionDirection.USD_TO_BS, RateSource.OFICIAL, Double.NaN, "10"))
    }

    private companion object {
        const val DELTA = 0.000001
    }
}
