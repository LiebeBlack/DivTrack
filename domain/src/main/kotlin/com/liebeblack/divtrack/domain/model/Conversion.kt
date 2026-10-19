package com.liebeblack.divtrack.domain.model

/** Sentido de la conversión en la calculadora. */
enum class ConversionDirection { USD_TO_BS, BS_TO_USD }

/**
 * Resultado de una conversión bidireccional.
 *
 * Solo hay dos importes: el que el usuario escribió y su equivalente a la tasa aplicada.
 * No hay impuestos, comisiones ni redondeo: el redondeo es una decisión de presentación y
 * vive en la capa de UI.
 */
data class Conversion(
    val direction: ConversionDirection,
    val source: RateSource,
    val rate: Double,
    val usdAmount: Double,
    val bsAmount: Double,
) {
    val hasAmount: Boolean get() = bsAmount > 0.0 || usdAmount > 0.0
}
