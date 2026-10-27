package com.liebeblack.divtrack.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liebeblack.divtrack.domain.model.TrendDirection
import com.liebeblack.divtrack.presentation.R
import com.liebeblack.divtrack.presentation.theme.DivTrackThemeTokens
import com.liebeblack.divtrack.presentation.theme.Spacing
import com.liebeblack.divtrack.presentation.theme.tabular

/**
 * Tarjeta de una tasa con geometría simétrica: franja de acento superior, todo el
 * contenido centrado y pie apilado que envuelve en vez de cortarse.
 *
 * Por qué cambió el diseño: la barra lateral de color comprimía horizontalmente el pie
 * ("Fuente: X" + "Actualizado Y" competían por el ancho y quedaban cortados o bajados),
 * y el bloque interior quedaba alineado a la izquierda dentro de una tarjeta que la
 * pantalla centra. La franja superior da el mismo código de color sin costo de ancho.
 *
 * [isHero] da máxima jerarquía a la tasa oficial con cifra Black grande y centrada; el
 * paralelo conserva una escala secundaria.
 *
 * Composable sin estado (primitivas + lambda): Compose puede saltarse la recomposición
 * cuando nada cambia, que es exactamente lo que buscamos en gama baja.
 */
@Composable
fun RateCard(
    title: String,
    valueText: String,
    trend: TrendDirection,
    deltaText: String?,
    providerText: String,
    updatedAtText: String?,
    accentColor: Color,
    modifier: Modifier = Modifier,
    isStale: Boolean = false,
    isHero: Boolean = false,
    isLoading: Boolean = false,
) {
    val colors = DivTrackThemeTokens.colors
    val trendColor = when (trend) {
        TrendDirection.UP -> colors.trendUp
        TrendDirection.DOWN -> colors.trendDown
        TrendDirection.FLAT -> colors.trendFlat
    }
    val emphasisColor = if (isHero) accentColor else colors.parallelMuted

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (isHero) {
            colors.officialSurface
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(
            width = if (isHero) 1.5.dp else 1.dp,
            color = if (isHero) {
                colors.officialBorder
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isHero) 8.dp else 3.dp)
                    .background(if (isHero) accentColor else colors.parallelMuted.copy(alpha = 0.55f)),
            )

            Column(
                modifier = Modifier.padding(
                    horizontal = Spacing.lg,
                    vertical = if (isHero) Spacing.xl else Spacing.md,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.fillMaxWidth(),
                    style = if (isHero) {
                        MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp)
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    color = emphasisColor,
                    fontWeight = if (isHero) FontWeight.ExtraBold else FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(if (isHero) Spacing.lg else Spacing.md))

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val fontScale = LocalDensity.current.fontScale
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(if (isHero) 40.dp else 32.dp)
                                .align(Alignment.Center),
                            color = emphasisColor,
                            strokeWidth = if (isHero) 4.dp else 3.dp,
                        )
                    } else if (isHero) {
                        // ── Cifra principal oficial: máxima jerarquía visual ──
                        val heroFontSize = remember(maxWidth, valueText, fontScale) {
                            val preferredFontSize = when {
                                fontScale >= 1.3f -> 40f
                                maxWidth >= 340.dp -> 72f
                                maxWidth >= 280.dp -> 60f
                                else -> 48f
                            }
                            val widthLimitedFontSize = if (valueText.isNotEmpty()) {
                                maxWidth.value * 0.94f / (valueText.length * 0.52f * fontScale)
                            } else {
                                preferredFontSize
                            }
                            minOf(preferredFontSize, widthLimitedFontSize)
                                .coerceAtLeast(28f)
                        }

                        Text(
                            text = valueText,
                            style = MaterialTheme.typography.displayLarge.tabular().copy(
                                fontSize = heroFontSize.sp,
                                lineHeight = (heroFontSize * 1.1f).sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-1.5).sp,
                            ),
                            color = emphasisColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        // ── Tasa paralela: escala secundaria ──
                        val parallelStyle = remember(valueText, emphasisColor) {
                            MaterialTheme.typography.displaySmall.tabular().copy(
                                fontSize = 26.sp,
                                lineHeight = 32.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text = valueText,
                            style = parallelStyle,
                            color = emphasisColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (isHero) Spacing.md else Spacing.sm))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    if (deltaText != null) {
                        Surface(
                            color = (if (isHero) trendColor else colors.parallelMuted)
                                .copy(alpha = 0.12f),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = Spacing.sm,
                                    vertical = Spacing.xs,
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            ) {
                                TrendArrow(
                                    direction = trend,
                                    arrowSize = if (isHero) 14.dp else 10.dp,
                                    tint = if (isHero) trendColor else colors.parallelMuted,
                                )
                                Text(
                                    text = deltaText,
                                    style = if (isHero) {
                                        MaterialTheme.typography.labelLarge
                                    } else {
                                        MaterialTheme.typography.labelMedium
                                    },
                                    color = if (isHero) trendColor else colors.parallelMuted,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    Text(
                        text = providerText,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isHero) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            colors.parallelMuted
                        },
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (updatedAtText != null || isStale) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        text = listOfNotNull(
                            updatedAtText,
                            if (isStale) stringResource(R.string.rate_stale_warning) else null,
                        ).joinToString(separator = " · "),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isStale) {
                            MaterialTheme.colorScheme.tertiary
                        } else if (isHero) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            colors.parallelMuted
                        },
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
