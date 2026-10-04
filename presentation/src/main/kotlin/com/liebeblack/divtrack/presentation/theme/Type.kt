package com.liebeblack.divtrack.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.liebeblack.divtrack.presentation.R

/**
 * Manrope (OFL) como fuente de marca y `fontFeatureSettings = "tnum"` en cifras para que
 * los dígitos no cambien de ancho al actualizar una tasa.
 *
 * **Por qué cada instancia lleva `variationSettings`.** El recurso
 * `manrope_variable.ttf` es una fuente variable (único eje `wght`, con mínimo en **200 =
 * ExtraLight** y máximo en 800). Compose carga el archivo sin estilo —
 * `ResourcesCompat.getFont(context, resId)` — y solo aplica los ejes que viajan en el
 * `Font` (`AndroidFontLoader` → `setFontVariationSettings`). Declarar únicamente el
 * `FontWeight` sirve para que el matcher elija una entrada, pero **no mueve el trazo**: sin
 * ejes explícitos, todas las entradas pintaban la instancia por defecto, ExtraLight, y la
 * app entera se veía de letra fina (peor aún en pantallas de gama baja). Con el eje fijado
 * aquí, el peso que se ve en pantalla es el que pide la escala tipográfica.
 *
 * La licencia está en `licenses/Manrope-OFL.txt` (no se empaqueta en el APK).
 */
private fun manrope(weight: Int): Font = Font(
    resId = R.font.manrope_variable,
    weight = FontWeight(weight),
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Manrope = FontFamily(
    manrope(400),
    manrope(500),
    manrope(600),
    manrope(700),
    // Manrope no llega a 900: ExtraBold es su peso máximo real (y el techo del eje `wght`).
    manrope(800),
)

private const val TABULAR_FIGURES = "tnum"

/** Estilo de cifra grande reutilizado por el dashboard y la calculadora. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = TABULAR_FIGURES)

val DivTrackTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 44.sp,
        lineHeight = 50.sp,
        letterSpacing = (-1.2).sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    displayMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 38.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.8).sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    displaySmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.4).sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    headlineMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    headlineSmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    titleLarge = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.4.sp,
    ),
    // El cuerpo va en Medium (500), no en Normal: Manrope en trazo fino pierde definición a
    // estos tamaños sobre fondos planos, y estas líneas son las que más se leen en la app.
    bodyLarge = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.3.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    labelSmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.4.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
)
