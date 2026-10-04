package com.liebeblack.divtrack.presentation.common

import androidx.annotation.StringRes
import com.liebeblack.divtrack.core.common.error.DataError
import com.liebeblack.divtrack.domain.model.RateSource
import com.liebeblack.divtrack.domain.model.ThemeMode
import com.liebeblack.divtrack.domain.model.TrendDirection
import com.liebeblack.divtrack.presentation.R

/**
 * Mapeos dominio -> recursos. Mantener los textos fuera del dominio es lo que permite
 * localizar la app (es-VE por defecto, inglés en `values-en`) sin tocar lógica.
 */

@StringRes
fun RateSource.labelRes(): Int = when (this) {
    RateSource.OFICIAL -> R.string.source_oficial
    RateSource.PARALELO -> R.string.source_paralelo
}

@StringRes
fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@StringRes
fun TrendDirection.contentDescriptionRes(): Int = when (this) {
    TrendDirection.UP -> R.string.trend_up
    TrendDirection.DOWN -> R.string.trend_down
    TrendDirection.FLAT -> R.string.trend_flat
}

/**
 * Traduce el fallo de datos a algo que el usuario pueda leer **y accionar**.
 *
 * Antes cualquier error acababa en el mismo texto ("Sin conexión"), así que un 404 del
 * proveedor o un timeout se contaban como si el teléfono estuviera desconectado: el usuario
 * revisaba su wifi mientras el problema estaba en la API. Ahora el texto dice qué pasó de
 * verdad, y el código HTTP viaja con el mensaje para poder reclamar al proveedor correcto.
 */
fun DataError.toUiText(): UiText = when (this) {
    is DataError.Network -> UiText.Res(R.string.error_no_connection)

    is DataError.Timeout -> UiText.Res(R.string.error_timeout)

    is DataError.Http -> when (code) {
        in 500..599 -> UiText.ResArgs(R.string.error_provider_down, listOf(code))
        429 -> UiText.Res(R.string.error_rate_limited)
        404 -> UiText.Res(R.string.error_provider_changed)
        else -> UiText.ResArgs(R.string.error_http, listOf(code))
    }

    is DataError.Parse -> UiText.Res(R.string.error_parse)

    is DataError.EmptyCache -> UiText.Res(R.string.msg_offline_no_data)

    is DataError.Storage -> UiText.Res(R.string.error_storage)

    is DataError.Unknown -> UiText.Res(R.string.error_unknown)
}

