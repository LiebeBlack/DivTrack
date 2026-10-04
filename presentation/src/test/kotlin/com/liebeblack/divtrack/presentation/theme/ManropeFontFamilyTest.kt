package com.liebeblack.divtrack.presentation.theme

import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `manrope_variable.ttf` es una fuente **variable** cuyo eje `wght` arranca en 200
 * (ExtraLight). Compose carga el archivo tal cual y solo mueve el eje con lo que viaje en
 * `variationSettings`: una entrada declarada sin ellos se pinta en ExtraLight aunque su
 * `FontWeight` diga Bold. Es un fallo **silencioso** —compila, pasa el resto de tests y solo
 * se nota en pantalla, como "letra fina"— así que esta invariante se fija aquí.
 */
class ManropeFontFamilyTest {

    private val fonts = (Manrope as FontListFontFamily).fonts

    @Test
    fun `cada entrada declara su peso tambien en el eje wght`() {
        assertTrue("La familia Manrope no puede quedar vacía", fonts.isNotEmpty())

        fonts.forEach { font ->
            val resourceFont = font as? ResourceFont
            assertTrue(
                "Toda entrada de Manrope debe ser carga de recurso Android",
                resourceFont != null,
            )
            assertEquals(
                "El peso ${font.weight.weight} está declarado pero no viaja en el eje wght",
                FontVariation.Settings(FontVariation.weight(font.weight.weight)),
                resourceFont?.variationSettings,
            )
        }
    }

    @Test
    fun `la escala no pide un peso que Manrope no tiene`() {
        // Manrope llega hasta ExtraBold (800). Pedir Black (900) haría que el matcher cayera
        // en otra entrada y el trazo más grueso de la app quedaría a merced de la síntesis.
        val declaredWeights = fonts.map { font -> font.weight.weight }.toSet()
        val maxWeight = declaredWeights.max()

        val styles = listOf(
            "displayLarge" to DivTrackTypography.displayLarge,
            "displayMedium" to DivTrackTypography.displayMedium,
            "displaySmall" to DivTrackTypography.displaySmall,
            "headlineMedium" to DivTrackTypography.headlineMedium,
            "headlineSmall" to DivTrackTypography.headlineSmall,
            "titleLarge" to DivTrackTypography.titleLarge,
            "titleMedium" to DivTrackTypography.titleMedium,
            "titleSmall" to DivTrackTypography.titleSmall,
            "bodyLarge" to DivTrackTypography.bodyLarge,
            "bodyMedium" to DivTrackTypography.bodyMedium,
            "bodySmall" to DivTrackTypography.bodySmall,
            "labelLarge" to DivTrackTypography.labelLarge,
            "labelMedium" to DivTrackTypography.labelMedium,
            "labelSmall" to DivTrackTypography.labelSmall,
        )

        styles.forEach { (name, style) ->
            val weight = style.fontWeight ?: FontWeight.Normal
            assertTrue(
                "El estilo $name pide $weight y Manrope solo declara hasta $maxWeight",
                weight.weight <= maxWeight,
            )
        }
    }
}
