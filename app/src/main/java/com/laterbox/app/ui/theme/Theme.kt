package com.laterbox.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun LaterboxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkLaterboxColors else LightLaterboxColors
    val typography = androidx.compose.runtime.remember(fontScale) {
        DefaultLaterboxTypography.scaled(fontScale)
    }

    val materialColors = if (darkTheme) {
        darkColorScheme(
            primary = colors.accentPrimary,
            onPrimary = colors.surfaceElevated,
            primaryContainer = colors.accentContainer,
            onPrimaryContainer = colors.textPrimary,
            background = colors.backgroundPrimary,
            onBackground = colors.textPrimary,
            surface = colors.surfacePrimary,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceSecondary,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.borderSubtle
        )
    } else {
        lightColorScheme(
            primary = colors.accentPrimary,
            onPrimary = colors.surfaceElevated,
            primaryContainer = colors.accentContainer,
            onPrimaryContainer = colors.textPrimary,
            background = colors.backgroundPrimary,
            onBackground = colors.textPrimary,
            surface = colors.surfacePrimary,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceSecondary,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.borderSubtle
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colors.backgroundPrimary.toArgb()
                window.navigationBarColor = colors.surfacePrimary.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalLaterboxColors provides colors,
        LocalLaterboxTypography provides typography
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            content = content
        )
    }
}

object LaterboxTheme {
    val colors: LaterboxColors
        @Composable
        @ReadOnlyComposable
        get() = LocalLaterboxColors.current

    val typography: LaterboxTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalLaterboxTypography.current

    val spacing: LaterboxSpacing
        get() = LaterboxSpacing

    val shapes: LaterboxShapeTokens
        get() = LaterboxShapeTokens

    val elevations: LaterboxElevationTokens
        get() = LaterboxElevationTokens
}
