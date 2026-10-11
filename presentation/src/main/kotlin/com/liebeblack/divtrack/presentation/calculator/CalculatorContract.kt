package com.liebeblack.divtrack.presentation.calculator

import androidx.compose.runtime.Immutable
import com.liebeblack.divtrack.domain.model.ConversionDirection
import com.liebeblack.divtrack.domain.model.RateSource
import com.liebeblack.divtrack.presentation.common.UiText

/**
 * Tasa disponible que el selector ofrece al usuario. Solo se ofrecen las que existen: si el
 * proveedor no ha publicado una de las dos, no se puede elegir.
 */
@Immutable
data class RateOptionUi(
    val source: RateSource,
    val value: Double,
)

/**
 * Cuotas por defecto de la simulación de crédito: doce, el plazo más repetido en las
 * promociones del mercado venezolano. El usuario la cambia con un toque.
 */
const val DEFAULT_INSTALLMENTS: Int = 12

/**
 * Estado de la calculadora. Todos los textos ya vienen formateados en es-VE desde el
 * ViewModel: las composables solo pintan.
 */
@Immutable
data class CalculatorUiState(
    val inputText: String = "",
    val direction: ConversionDirection = ConversionDirection.USD_TO_BS,
    val selectedSource: RateSource = RateSource.OFICIAL,
    val rateOptions: List<RateOptionUi> = emptyList(),
    val selectedRateText: String = "—",

    /**
     * Edad de la tasa seleccionada tal y como la publicó el proveedor ("1 oct · 09:14").
     * `null` si la fuente no publica marca de tiempo (Yadio): no se inventa nada.
     */
    val selectedRateAgeText: String? = null,

    /** `true` mientras la pasada contra los proveedores está en vuelo. */
    val isSyncing: Boolean = false,
    val igtfEnabled: Boolean = false,
    val igtfRateText: String = "",
    val igtfBsText: String = "—",
    val totalBsText: String = "—",
    val totalUsdText: String = "—",

    /** Número de cuotas de la simulación de crédito (siempre ≥ 1). */
    val installments: Int = DEFAULT_INSTALLMENTS,

    /** Cuota mensual del monto actual en bolívares, con la tasa aplicada. */
    val installmentBsText: String = "—",

    /** Cuota mensual del monto actual en dólares, con la tasa aplicada. */
    val installmentUsdText: String = "—",
    val canCopy: Boolean = false,
    val hasRate: Boolean = true,
) {
    val isUsdToBs: Boolean get() = direction == ConversionDirection.USD_TO_BS

    val isAmountValid: Boolean get() = inputText.isNotBlank()
}

sealed interface CalculatorIntent {

    data class AmountChanged(val value: String) : CalculatorIntent

    data class SelectSource(val source: RateSource) : CalculatorIntent

    /** Cambia el número de cuotas de la simulación de crédito. */
    data class SelectInstallments(val count: Int) : CalculatorIntent

    /** Fuerza la pasada contra los proveedores y recalcula con lo que llegue. */
    data object Refresh : CalculatorIntent

    /**
     * La pantalla volvió al primer plano o a su pestaña.
     *
     * No es un refresco a ciegas: el ViewModel mira cuándo fue el último intento y si ese
     * intento falló, y solo sale a la red si merece la pena. Volver después de horas no puede
     * dejar la calculadora convirtiendo con la tasa de la mañana, y volver a los dos minutos no
     * debe gastar radio para nada.
     */
    data object OnResumed : CalculatorIntent

    data object SwapDirection : CalculatorIntent

    data class ToggleIgtf(val enabled: Boolean) : CalculatorIntent

    data object ClearAmount : CalculatorIntent

    data object CopyBreakdown : CalculatorIntent
}

sealed interface CalculatorEffect {

    data class CopyToClipboard(val summary: BreakdownSummary) : CalculatorEffect

    data class ShowMessage(val text: UiText) : CalculatorEffect
}

/** Datos crudos del desglose: la localización del texto final vive en la capa de UI. */
@Immutable
data class BreakdownSummary(
    val direction: ConversionDirection,
    val source: RateSource,
    val amountText: String,
    val rateText: String,
    val igtfEnabled: Boolean,
    val igtfText: String,
    val totalBsText: String,
    val totalUsdText: String,
)
