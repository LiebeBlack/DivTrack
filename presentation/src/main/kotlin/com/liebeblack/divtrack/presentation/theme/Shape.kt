package com.liebeblack.divtrack.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Radios generosos y estandarizados: la mitad del "look premium" de la app sale de aquí.
 *
 * En Compose este `Shapes` es el equivalente a un `ShapeAppearanceOverlay` de Views: una
 * única tabla de radios que consumen `MaterialTheme.shapes.*`, sin estilos XML duplicados.
 */
val DivTrackShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
