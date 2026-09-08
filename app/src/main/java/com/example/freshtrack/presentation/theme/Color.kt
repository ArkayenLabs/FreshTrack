package com.example.freshtrack.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette, as tokens rather than as colours chosen at the call site.
 *
 * Two things shape it. Surfaces are warm neutrals — off-whites and a dark that
 * is nearly but not quite black — because a kitchen tool should feel closer to
 * paper than to a console. The accent is a leaf green rather than the Material
 * baseline, and there is deliberately no blue: the previous scheme paired a
 * green primary with a blue secondary, which is why the selected tab used to
 * sit in the palette like a borrowed part.
 *
 * Everything the old file held besides the urgency colours was unreferenced —
 * Theme.kt declared its colours inline — including a Medicine and a Cosmetics
 * category the product deliberately does not have.
 */

// ─── Light ──────────────────────────────────────────────────────────────────

val LightPrimary = Color(0xFF2C6844)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFAFF0C2)
val LightOnPrimaryContainer = Color(0xFF002110)

val LightSecondary = Color(0xFF506352)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFD3E8D2)
val LightOnSecondaryContainer = Color(0xFF0E1F11)

val LightTertiary = Color(0xFF8A5B21)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFFFDCBB)
val LightOnTertiaryContainer = Color(0xFF2D1600)

val LightError = Color(0xFFA4231C)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD5)
val LightOnErrorContainer = Color(0xFF410001)

val LightBackground = Color(0xFFFBF9F3)
val LightOnBackground = Color(0xFF1A1C19)
val LightSurface = Color(0xFFFBF9F3)
val LightOnSurface = Color(0xFF1A1C19)
val LightSurfaceVariant = Color(0xFFDDE5DA)
val LightOnSurfaceVariant = Color(0xFF414942)

val LightOutline = Color(0xFF717971)
val LightOutlineVariant = Color(0xFFC1C9BE)

val LightSurfaceDim = Color(0xFFDBD9D3)
val LightSurfaceBright = Color(0xFFFBF9F3)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF5F3ED)
val LightSurfaceContainer = Color(0xFFEFEDE7)
val LightSurfaceContainerHigh = Color(0xFFEAE7E2)
val LightSurfaceContainerHighest = Color(0xFFE4E2DC)

val LightInverseSurface = Color(0xFF2F312D)
val LightInverseOnSurface = Color(0xFFF0F2EB)
val LightInversePrimary = Color(0xFF93D5A7)

// ─── Dark ───────────────────────────────────────────────────────────────────

val DarkPrimary = Color(0xFF93D5A7)
val DarkOnPrimary = Color(0xFF00391F)
val DarkPrimaryContainer = Color(0xFF0D4F2E)
val DarkOnPrimaryContainer = Color(0xFFAFF0C2)

val DarkSecondary = Color(0xFFB7CCB6)
val DarkOnSecondary = Color(0xFF223425)
val DarkSecondaryContainer = Color(0xFF384B3B)
val DarkOnSecondaryContainer = Color(0xFFD3E8D2)

val DarkTertiary = Color(0xFFFFB874)
val DarkOnTertiary = Color(0xFF4B2800)
val DarkTertiaryContainer = Color(0xFF6A3D00)
val DarkOnTertiaryContainer = Color(0xFFFFDCBB)

val DarkError = Color(0xFFFFB4A9)
val DarkOnError = Color(0xFF690003)
val DarkErrorContainer = Color(0xFF930006)
val DarkOnErrorContainer = Color(0xFFFFDAD5)

val DarkBackground = Color(0xFF12140F)
val DarkOnBackground = Color(0xFFE2E3DD)
val DarkSurface = Color(0xFF12140F)
val DarkOnSurface = Color(0xFFE2E3DD)
val DarkSurfaceVariant = Color(0xFF414942)
val DarkOnSurfaceVariant = Color(0xFFC1C9BE)

val DarkOutline = Color(0xFF8B9389)
val DarkOutlineVariant = Color(0xFF414942)

val DarkSurfaceDim = Color(0xFF12140F)
val DarkSurfaceBright = Color(0xFF383A35)
val DarkSurfaceContainerLowest = Color(0xFF0D0F0B)
val DarkSurfaceContainerLow = Color(0xFF1A1C19)
val DarkSurfaceContainer = Color(0xFF1E201C)
val DarkSurfaceContainerHigh = Color(0xFF282B26)
val DarkSurfaceContainerHighest = Color(0xFF333630)

val DarkInverseSurface = Color(0xFFE2E3DD)
val DarkInverseOnSurface = Color(0xFF2F312D)
val DarkInversePrimary = Color(0xFF2C6844)

// ─── Urgency ────────────────────────────────────────────────────────────────

/**
 * How close a date is, as colour.
 *
 * These cannot be single constants shared by both themes. A badge draws text on
 * top of one of these, so the light theme needs dark fills under white text and
 * the dark theme needs light fills under dark text; one set used for both is
 * how a badge ends up unreadable in whichever theme it was not designed in.
 *
 * Colour is never the only signal — every place these are used also states the
 * timing in words, because a date is not something to convey by hue alone.
 */
data class UrgencyPalette(
    val safe: Color,
    val onSafe: Color,
    val warning: Color,
    val onWarning: Color,
    val critical: Color,
    val onCritical: Color,
    val expired: Color,
    val onExpired: Color
)

val LightUrgency = UrgencyPalette(
    safe = Color(0xFF2C6844),
    onSafe = Color(0xFFFFFFFF),
    warning = Color(0xFF8A5B21),
    onWarning = Color(0xFFFFFFFF),
    critical = Color(0xFFB4531B),
    onCritical = Color(0xFFFFFFFF),
    expired = Color(0xFFA4231C),
    onExpired = Color(0xFFFFFFFF)
)

val DarkUrgency = UrgencyPalette(
    safe = Color(0xFF93D5A7),
    onSafe = Color(0xFF00391F),
    warning = Color(0xFFE0A961),
    onWarning = Color(0xFF3D2600),
    critical = Color(0xFFFFB07A),
    onCritical = Color(0xFF4E1D00),
    expired = Color(0xFFFFB4A9),
    onExpired = Color(0xFF690003)
)

/** Neutral fallback for a category with no colour of its own. */
val CategoryOther = Color(0xFF8A9187)
