package com.liebeblack.divtrack.domain.usecase

import com.liebeblack.divtrack.core.common.utils.NumberParsing
import com.liebeblack.divtrack.domain.model.Conversion
import com.liebeblack.divtrack.domain.model.ConversionDirection
import com.liebeblack.divtrack.domain.model.RateSource
import javax.inject.Inject

/**
 * Calculadora bidireccional. Aritmética pura, sin Android y sin estado: la UI la llama en
 * cada pulsación de tecla (el cálculo es instantáneo, no hay botón "Calcular").
 *
 * No aplica impuestos. La tasa es la única variable financiera: convertir en un sentido
 * multiplica y en el otro divide, sin pasos intermedios que puedan divergir.
 */
class CalculateConversionUseCase @Inject constructor() {

    /**
     * @param rate tasa vigente de [source] en Bs por USD. `null`, <= 0 o no finita desactiva
     * el cálculo.
     * @param rawAmount texto tal cual lo escribió el usuario (se parsea con tolerancia es-VE).
     * @return `null` si todavía no hay monto utilizable o no hay tasa: la UI muestra guiones.
     */
    operator fun invoke(
        direction: ConversionDirection,
        source: RateSource,
        rate: Double?,
        rawAmount: String,
    ): Conversion? {
        val amount = NumberParsing.parseAmountOrNull(rawAmount) ?: return null
        if (!amount.isFinite() || amount <= 0.0) return null

        val safeRate = rate?.takeIf { it > 0.0 && it.isFinite() } ?: return null

        return when (direction) {
            ConversionDirection.USD_TO_BS -> {
                val bsAmount = amount * safeRate
                if (!bsAmount.isFinite()) return null
                Conversion(
                    direction = direction,
                    source = source,
                    rate = safeRate,
                    usdAmount = amount,
                    bsAmount = bsAmount,
                )
            }

            ConversionDirection.BS_TO_USD -> {
                val usdAmount = amount / safeRate
                if (!usdAmount.isFinite()) return null
                Conversion(
                    direction = direction,
                    source = source,
                    rate = safeRate,
                    usdAmount = usdAmount,
                    bsAmount = amount,
                )
            }
        }
    }
}
