package com.laterbox.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class LaterboxColors(
    val backgroundPrimary: Color,
    val surfacePrimary: Color,
    val surfaceSecondary: Color,
    val surfaceElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val borderSubtle: Color,
    val borderFocused: Color,
    val accentPrimary: Color,
    val accentContainer: Color,
    val statusDanger: Color,
    val isDark: Boolean
)

// Light Mode: Warm Editorial Ivory
val LightLaterboxColors = LaterboxColors(
    backgroundPrimary = Color(0xFFF8F7F3),
    surfacePrimary = Color(0xFFFDFCF9),
    surfaceSecondary = Color(0xFFF1EFEA),
    surfaceElevated = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF191919),
    textSecondary = Color(0xFF66645E),
    textTertiary = Color(0xFF94918A),
    borderSubtle = Color(0xFFE6E4DD),
    borderFocused = Color(0xFF191919),
    accentPrimary = Color(0xFFC85A32),
    accentContainer = Color(0xFFF9EBE5),
    statusDanger = Color(0xFFB8322A),
    isDark = false
)

// Dark Mode: Independent Carbon Deep Charcoal
val DarkLaterboxColors = LaterboxColors(
    backgroundPrimary = Color(0xFF141414),
    surfacePrimary = Color(0xFF1C1C1B),
    surfaceSecondary = Color(0xFF242422),
    surfaceElevated = Color(0xFF222220),
    textPrimary = Color(0xFFEDEDEA),
    textSecondary = Color(0xFF9E9C96),
    textTertiary = Color(0xFF6E6C66),
    borderSubtle = Color(0xFF282826),
    borderFocused = Color(0xFFEDEDEA),
    accentPrimary = Color(0xFFE07048),
    accentContainer = Color(0xFF332019),
    statusDanger = Color(0xFFE55A50),
    isDark = true
)

val LocalLaterboxColors = staticCompositionLocalOf { LightLaterboxColors }
