package com.laterbox.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standardised layout and dimension tokens for Laterbox.
 */
object LaterboxSpacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val s: Dp = 8.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val m: Dp = 16.dp
    val base: Dp = 16.dp
    val l: Dp = 20.dp
    val xl: Dp = 24.dp
    val lg: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 48.dp

    // Accessibility minimum touch target
    val minTouchTarget: Dp = 48.dp

    // Card border hairline width
    val hairline: Dp = 1.dp

    // Semantic screen & component layout spacing
    val screenHorizontalPadding: Dp = 16.dp
    val screenVerticalPadding: Dp = 12.dp
    val sheetHorizontalPadding: Dp = 16.dp
    val cardPadding: Dp = 16.dp
    val cardPaddingCompact: Dp = 12.dp
    val cardGap: Dp = 12.dp
    val itemGap: Dp = 8.dp

    // Component dimensions
    val iconSmall: Dp = 16.dp
    val iconMedium: Dp = 20.dp
    val iconLarge: Dp = 24.dp
    val chipHeight: Dp = 32.dp
    val buttonHeight: Dp = 48.dp
    val inputHeight: Dp = 48.dp
    val fabHeight: Dp = 56.dp
    val dragHandleWidth: Dp = 36.dp
    val dragHandleHeight: Dp = 4.dp
    val badgePaddingHorizontal: Dp = 6.dp
    val badgePaddingVertical: Dp = 2.dp

    // Radii (kept for backward compatibility, prefer LaterboxShapeTokens)
    val cardRadius: Dp = 16.dp
    val thumbnailRadius: Dp = 8.dp
    val sheetRadius: Dp = 24.dp
    val radiusSmall: Dp = 6.dp
    val radiusMedium: Dp = 12.dp
    val radiusLarge: Dp = 16.dp
    val radiusFull: Dp = 999.dp
}
