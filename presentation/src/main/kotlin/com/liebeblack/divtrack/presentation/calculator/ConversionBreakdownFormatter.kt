package com.liebeblack.divtrack.presentation.calculator

import android.content.Context
import com.liebeblack.divtrack.domain.model.ConversionDirection
import com.liebeblack.divtrack.domain.model.RateSource
import com.liebeblack.divtrack.presentation.R

/**
 * Construye el texto que se copia al portapapeles.
 *
 * El reporte multilínea separa dirección, entrada, tasa, desglose e importe final para
 * que se lea bien tanto pegado en un chat como en una nota.
 *
 * Se hace en la capa de UI y no en el ViewModel para poder usar recursos localizados
 * (es-VE / en) sin que el ViewModel toque `Context`.
 */
internal fun buildBreakdownText(context: Context, summary: BreakdownSummary): String {
    val sourceLabel = context.getString(
        when (summary.source) {
            RateSource.OFICIAL -> R.string.source_oficial_long
            RateSource.PARALELO -> R.string.source_paralelo_long
        },
    )
    val amount = when (summary.direction) {
        ConversionDirection.USD_TO_BS ->
            context.getString(R.string.copy_amount_usd, summary.amountText)

        ConversionDirection.BS_TO_USD ->
            context.getString(R.string.copy_amount_bs, summary.amountText)
    }
    val direction = when (summary.direction) {
        ConversionDirection.USD_TO_BS -> R.string.copy_direction_usd_to_bs
        ConversionDirection.BS_TO_USD -> R.string.copy_direction_bs_to_usd
    }
    // El resumen trae el estado y el importe ya formateado. Se copia el IGTF real cuando
    // está activo y "No aplicado" cuando está apagado: nada de adivinar por el texto.
    val taxLine = if (summary.igtfEnabled) {
        context.getString(R.string.copy_igtf_estimated, summary.igtfText)
    } else {
        context.getString(R.string.copy_igtf_not_applied)
    }

    return listOf(
        context.getString(R.string.copy_title),
        context.getString(direction),
        amount,
        context.getString(R.string.copy_rate, sourceLabel, summary.rateText),
        taxLine,
        context.getString(R.string.copy_total, summary.totalUsdText, summary.totalBsText),
        context.getString(R.string.copy_signature),
    ).joinToString(separator = "\n")
}
