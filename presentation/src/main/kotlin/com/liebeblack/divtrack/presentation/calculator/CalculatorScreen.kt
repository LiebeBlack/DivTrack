package com.liebeblack.divtrack.presentation.calculator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liebeblack.divtrack.domain.model.RateSource
import com.liebeblack.divtrack.presentation.R
import com.liebeblack.divtrack.presentation.common.CollectEffects
import com.liebeblack.divtrack.presentation.common.TopSnackbarHost
import com.liebeblack.divtrack.presentation.common.asString
import com.liebeblack.divtrack.presentation.common.copyToClipboard
import com.liebeblack.divtrack.presentation.common.labelRes
import com.liebeblack.divtrack.presentation.components.AmountField
import com.liebeblack.divtrack.presentation.components.IgtfSwitchRow
import com.liebeblack.divtrack.presentation.components.SegmentedSelector
import com.liebeblack.divtrack.presentation.theme.DivTrackThemeTokens
import com.liebeblack.divtrack.presentation.theme.Spacing
import com.liebeblack.divtrack.presentation.theme.tabular

/** Etiqueta del portapapeles que verán otras apps al pegar. */
private const val CLIPBOARD_LABEL = "DivTrack"

/** Plazos que ofrece la simulación de crédito; [DEFAULT_INSTALLMENTS] va marcado al abrir. */
private val INSTALLMENT_OPTIONS = listOf(3, 6, 12, 24)

@Composable
fun CalculatorRoute(viewModel: CalculatorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val onIntent = remember(viewModel) { viewModel::onIntent }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is CalculatorEffect.CopyToClipboard -> {
                context.copyToClipboard(
                    label = CLIPBOARD_LABEL,
                    text = buildBreakdownText(context, effect.summary),
                )
                snackbarHostState.showSnackbar(message = context.getString(R.string.copy_done))
            }

            is CalculatorEffect.ShowMessage ->
                snackbarHostState.showSnackbar(message = effect.text.asString(context))
        }
    }

    CalculatorScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onIntent = onIntent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    state: CalculatorUiState,
    snackbarHostState: SnackbarHostState,
    onIntent: (CalculatorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DivTrackThemeTokens.colors

    // Solo las tasas que existen: sin el respaldo de `RateSource.ordered()` la pantalla no
    // puede ofrecer una opción que no está en Room y acabar en "sin tasas disponibles"
    // (el fallback reintroducía justo el bug que corrigió ADR 20).
    val sourceOptions = remember(state.rateOptions) {
        state.rateOptions.map { option -> option.source }
    }

    val accentColor = if (state.selectedSource == RateSource.OFICIAL) {
        colors.officialAccent
    } else {
        colors.parallelAccent
    }
    val resultValueColor = if (state.selectedSource == RateSource.OFICIAL) {
        colors.officialAccent
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(R.string.calculator_title)) },
                actions = {
                    // Misma mecánica que el dashboard: refresco manual visible y bloqueado
                    // mientras vuela la pasada, para que no se encolen peticiones a ciegas.
                    IconButton(
                        onClick = { onIntent(CalculatorIntent.Refresh) },
                        enabled = !state.isSyncing,
                    ) {
                        if (state.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Sync,
                                contentDescription = stringResource(R.string.action_refresh),
                            )
                        }
                    }
                    IconButton(onClick = { onIntent(CalculatorIntent.ClearAmount) }) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = stringResource(R.string.action_clear),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = padding.calculateTopPadding() + Spacing.sm,
                        bottom = padding.calculateBottomPadding() + Spacing.xl,
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                // Solo se ofrecen las tasas que existen de verdad en Room. Si un proveedor no
                // ha publicado el oficial todavía, no tiene sentido dejar elegirlo para acabar
                // viendo "sin tasas disponibles".
                SegmentedSelector(
                    options = sourceOptions,
                    selected = state.selectedSource,
                    onSelect = { source -> onIntent(CalculatorIntent.SelectSource(source)) },
                    label = { source -> stringResource(source.labelRes()) },
                    modifier = Modifier.fillMaxWidth(),
                )

                // La fila "Tasa" incluye la edad del dato: una tasa de ayer bien explicada
                // evita el clásico "las cuentas no salen" por convertir con un precio vencido.
                Text(
                    text = buildString {
                        append(stringResource(R.string.calculator_rate_line, state.selectedRateText))
                        state.selectedRateAgeText?.takeIf { it.isNotEmpty() }?.let { age ->
                            append(" · ")
                            append(stringResource(R.string.rate_updated_at, age))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                AmountField(
                    value = state.inputText,
                    onValueChange = { value -> onIntent(CalculatorIntent.AmountChanged(value)) },
                    label = if (state.isUsdToBs) {
                        stringResource(R.string.calculator_amount_usd)
                    } else {
                        stringResource(R.string.calculator_amount_bs)
                    },
                    prefix = if (state.isUsdToBs) "$" else "Bs.",
                    accentColor = accentColor,
                )

                TextButton(
                    onClick = { onIntent(CalculatorIntent.SwapDirection) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(text = stringResource(R.string.action_swap_direction))
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(
                        1.dp,
                        accentColor.copy(alpha = 0.28f),
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                    ) {
                        ResultRow(
                            label = stringResource(R.string.result_mirror_bs),
                            value = state.totalBsText,
                            valueColor = resultValueColor,
                            emphasize = true,
                        )
                        ResultRow(
                            label = stringResource(R.string.result_mirror_usd),
                            value = state.totalUsdText,
                            valueColor = resultValueColor,
                            emphasize = true,
                        )
                        ResultRow(
                            label = stringResource(R.string.result_igtf, state.igtfRateText),
                            value = state.igtfBsText,
                        )
                    }
                }

                IgtfSwitchRow(
                    checked = state.igtfEnabled,
                    onCheckedChange = { enabled -> onIntent(CalculatorIntent.ToggleIgtf(enabled)) },
                    igtfRateText = state.igtfRateText,
                )

                // Crédito en cuotas: el monto ya escrito, repartido entre el plazo elegido.
                // Usa el mismo total del desglose (tasa aplicada e IGTF incluido), así que
                // también funciona con la última tasa guardada cuando no hay red.
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.calculator_credit_title),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        SegmentedSelector(
                            options = INSTALLMENT_OPTIONS,
                            selected = state.installments,
                            onSelect = { count ->
                                onIntent(CalculatorIntent.SelectInstallments(count))
                            },
                            label = { count ->
                                stringResource(R.string.calculator_credit_chip, count)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(
                                R.string.calculator_credit_installment,
                                state.installments,
                                state.installmentBsText,
                                state.installmentUsdText,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.titleMedium.tabular(),
                            color = resultValueColor,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = stringResource(R.string.calculator_credit_hint),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Button(
                    onClick = { onIntent(CalculatorIntent.CopyBreakdown) },
                    enabled = state.canCopy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(imageVector = Icons.Filled.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(text = stringResource(R.string.action_copy_breakdown))
                }

                val hintText = when {
                    !state.hasRate -> stringResource(R.string.calculator_no_rate)
                    !state.isAmountValid -> stringResource(R.string.calculator_hint)
                    else -> null
                }

                if (hintText != null) {
                    Text(
                        text = hintText,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.hasRate) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        textAlign = TextAlign.Center,
                    )
                }
            }
            TopSnackbarHost(
                hostState = snackbarHostState,
                topPadding = padding.calculateTopPadding(),
            )
        }
    }
}

@Composable
private fun ResultRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasize: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val valueStyle = if (emphasize) {
        MaterialTheme.typography.headlineMedium.tabular()
    } else {
        MaterialTheme.typography.titleMedium.tabular()
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale
            val baseFontSize = if (emphasize) {
                when {
                    maxWidth < 280.dp -> 24f
                    maxWidth < 360.dp -> 26f
                    else -> 28f
                }
            } else {
                when {
                    maxWidth < 280.dp -> 18f
                    maxWidth < 360.dp -> 20f
                    else -> 22f
                }
            }
            val widthLimitedFontSize = if (value.isNotEmpty()) {
                maxWidth.value * 0.92f / (value.length * 0.52f * fontScale)
            } else {
                baseFontSize
            }
            val resultFontSize = minOf(baseFontSize, widthLimitedFontSize)
                .coerceAtLeast(16f)

            Text(
                text = value,
                modifier = Modifier.fillMaxWidth(),
                style = valueStyle.copy(
                    fontSize = resultFontSize.sp,
                    lineHeight = (resultFontSize * 1.15f).sp,
                    fontWeight = if (emphasize) FontWeight.ExtraBold else valueStyle.fontWeight,
                    letterSpacing = if (emphasize) (-0.8).sp else valueStyle.letterSpacing,
                ),
                color = valueColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
