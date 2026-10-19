package com.liebeblack.divtrack.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Selector de elección única que reorganiza sus opciones en varias filas según el ancho.
 *
 * Un solo componente cubre los usos de la app (fuente, tema e intervalo), manteniendo
 * objetivos táctiles cómodos en teléfonos estrechos y con texto ampliado.
 *
 * `label` es `@Composable` porque cada llamada resuelve un recurso de texto
 * (`stringResource`), y eso solo se puede hacer desde contexto de composición.
 */
@Composable
fun <T> SegmentedSelector(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                // 48 dp es el objetivo táctil mínimo recomendado; el chip por defecto es
                // más bajo y con texto ampliado se vuelve difícil de pulsar.
                modifier = Modifier.heightIn(min = 48.dp),
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTrailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = option == selected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                ),
                label = {
                    Text(
                        text = label(option),
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
            )
        }
    }
}
