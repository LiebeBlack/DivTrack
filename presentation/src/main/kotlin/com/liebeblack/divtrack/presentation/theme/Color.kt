package com.liebeblack.divtrack.presentation.theme

import androidx.compose.ui.graphics.Color

/** Esmeralda premium y crema cálida, con superficies simples para renderizar bien en gama baja. */

// --- Superficies (dark-first) ---
val Obsidian = Color(0xFF06130E)
val ObsidianElevated = Color(0xFF0D2118)
val ObsidianBorder = Color(0xFF1C3D2C)
val ObsidianMuted = Color(0xFFB0C0B6)

// --- Superficies (light) ---
val CloudWhite = Color(0xFFFFFFFF)
val CloudSurface = Color(0xFFFAF9F6)
val CloudBorder = Color(0xFFC4BCA8)
val CloudShade = Color(0xFFE5E0D4)
val CloudCanvas = Color(0xFFF1EFE7)
val SlateText = Color(0xFF1F2923)

/** Borde de campos y separadores con contraste suficiente para localizar el control. */
val OutlineLight = Color(0xFF7C7566)

// --- Acentos de marca ---
val Mint = Color(0xFF39F0A5)
val MintDeep = Color(0xFF008F5B)
val MintContrast = Color(0xFF006B46)
val Amber = Color(0xFFFFB020)
val ParallelMutedDark = Color(0xFFAAA092)
val ParallelMutedLight = Color(0xFF746A5D)

/** Texto principal sobre oscuro: más blanco que [CloudWhite] para máximo contraste. */
val Frost = Color(0xFFF5F8FB)

// --- Semánticos ---
// Verdes/rojos de mercado para DARK. En claro se usan los *Deep* (abajo): el verde
// brillante como texto pequeño sobre blanco no alcanza el contraste AA (2,2:1).
val TrendUp = Color(0xFF16C784)
val TrendDown = Color(0xFFEA3943)
val TrendFlat = Color(0xFF8A94A6)
val TrendUpDeep = Color(0xFF0B6B45)
val TrendDownDeep = Color(0xFFB3261E)
val TrendFlatDeep = Color(0xFF5A6575)

/** Ámbar legible sobre blanco: el [Amber] brillante solo sirve de acento, no de texto. */
val AmberDeep = Color(0xFF92400E)
val OfficialAccent = Mint
val ParallelAccent = Amber

// Nota de legibilidad: las franjas de acento de las tarjetas (Mint/Amber) y los tokens
// semánticos brillantes son fondo sólido, nunca texto pequeño. El texto de variación
// en claro usa los tokens *Deep* (ver [DivTrackColors]).
