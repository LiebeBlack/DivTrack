package com.liebeblack.divtrack.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.liebeblack.divtrack.domain.model.ThemeMode
import com.liebeblack.divtrack.domain.model.TrendDirection
import com.liebeblack.divtrack.presentation.theme.DivTrackTheme
import com.liebeblack.divtrack.presentation.theme.DivTrackThemeTokens
import com.liebeblack.divtrack.presentation.theme.Spacing

/**
 * Previews de Android Studio.
 *
 * No son decoración: son la forma de revisar la mitad visual del proyecto sin instalar la
 * app (y de detectar un color que no contrasta antes de que llegue a un dispositivo).
 * Se cubren los estados que importan: claro, oscuro, tendencia y brecha.
 */
@Preview(name = "Tasa oficial héroe · claro", widthDp = 380, showBackground = true)
@Composable
private fun RateCardOfficialLightPreview() {
    DivTrackTheme(themeMode = ThemeMode.LIGHT) {
        Surface {
            RateCard(
                title = "Dólar Oficial (BCV)",
                valueText = "859,06 Bs.",
                trend = TrendDirection.UP,
                deltaText = "+0,12 %",
                providerText = "Fuente: DolarAPI",
                updatedAtText = "Actualizado 30 sep · 21:01",
                accentColor = DivTrackThemeTokens.colors.officialAccent,
                modifier = Modifier.padding(Spacing.lg),
                isHero = true,
            )
        }
    }
}

@Preview(name = "Tasa oficial héroe · oscuro", widthDp = 380, showBackground = true)
@Composable
private fun RateCardOfficialDarkPreview() {
    DivTrackTheme(themeMode = ThemeMode.DARK) {
        Surface {
            RateCard(
                title = "Dólar Oficial (BCV)",
                valueText = "859,06 Bs.",
                trend = TrendDirection.UP,
                deltaText = "+0,12 %",
                providerText = "Fuente: DolarAPI",
                updatedAtText = "Actualizado 30 sep · 21:01",
                accentColor = DivTrackThemeTokens.colors.officialAccent,
                modifier = Modifier.padding(Spacing.lg),
                isHero = true,
            )
        }
    }
}

@Preview(name = "Tasa paralelo · oscuro", widthDp = 380, showBackground = true)
@Composable
private fun RateCardParallelDarkPreview() {
    DivTrackTheme(themeMode = ThemeMode.DARK) {
        Surface {
            RateCard(
                title = "Dólar Paralelo (mercado)",
                valueText = "954,55 Bs.",
                trend = TrendDirection.DOWN,
                deltaText = "-0,57 %",
                providerText = "Fuente: Yadio",
                updatedAtText = "Actualizado 30 sep · 21:04",
                accentColor = DivTrackThemeTokens.colors.parallelAccent,
                modifier = Modifier.padding(Spacing.lg),
            )
        }
    }
}

@Preview(name = "Tasa paralelo · claro", widthDp = 380, showBackground = true)
@Composable
private fun RateCardParallelLightPreview() {
    DivTrackTheme(themeMode = ThemeMode.LIGHT) {
        Surface {
            RateCard(
                title = "Dólar Paralelo (mercado)",
                valueText = "954,55 Bs.",
                trend = TrendDirection.DOWN,
                deltaText = "-0,57 %",
                providerText = "Fuente: Yadio",
                updatedAtText = "Actualizado 30 sep · 21:04",
                accentColor = DivTrackThemeTokens.colors.parallelAccent,
                modifier = Modifier.padding(Spacing.lg),
            )
        }
    }
}

@Preview(name = "Brecha y tendencias", widthDp = 380, showBackground = true)
@Composable
private fun SpreadAndTrendsPreview() {
    DivTrackTheme(themeMode = ThemeMode.DARK) {
        Surface {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                SpreadChip(percentText = "+11,12 %", absoluteText = "95,49 Bs.")
                SpreadChip(percentText = null, absoluteText = null)

                TrendArrow(direction = TrendDirection.UP, arrowSize = 16.dp)
                TrendArrow(direction = TrendDirection.DOWN, arrowSize = 16.dp)
                TrendArrow(direction = TrendDirection.FLAT, arrowSize = 16.dp)

                Text(text = "Flechas: sube, baja y sin cambios")
            }
        }
    }
}

@Preview(name = "Estado de error · oscuro", widthDp = 380, showBackground = true)
@Composable
private fun ErrorStatePreview() {
    DivTrackTheme(themeMode = ThemeMode.DARK) {
        Surface {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                ErrorState(
                    message = "Sin conexión. Mostrando última actualización",
                    retryLabel = "Reintentar",
                    onRetry = {},
                )
            }
        }
    }
}
