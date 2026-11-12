package com.laterbox.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.laterbox.app.ui.theme.LaterboxTheme

enum class LaterboxCardVariant {
    DEFAULT,   // surfacePrimary with subtle border
    SECONDARY, // surfaceSecondary with subtle border
    ELEVATED   // surfaceElevated with subtle border
}

/**
 * Standardised card container across Laterbox screens.
 * Uses 16dp rounded corners, hairline subtle border stroke, theme-aware surface colors,
 * and tactile press indication.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LaterboxCard(
    modifier: Modifier = Modifier,
    variant: LaterboxCardVariant = LaterboxCardVariant.DEFAULT,
    shape: Shape = LaterboxTheme.shapes.Card,
    containerColor: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    elevation: Dp = LaterboxTheme.elevations.flat,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = LaterboxTheme.colors
    val spacing = LaterboxTheme.spacing

    val resolvedContainerColor = when {
        containerColor != Color.Unspecified -> containerColor
        variant == LaterboxCardVariant.SECONDARY -> colors.surfaceSecondary
        variant == LaterboxCardVariant.ELEVATED -> colors.surfaceElevated
        else -> colors.surfacePrimary
    }

    val resolvedBorderColor = if (borderColor != Color.Unspecified) borderColor else colors.borderSubtle
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null && onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = ripple(),
                        role = Role.Button,
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                } else if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = ripple(),
                        role = Role.Button,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            ),
        shape = shape,
        color = resolvedContainerColor,
        border = BorderStroke(spacing.hairline, resolvedBorderColor),
        shadowElevation = elevation
    ) {
        Box(content = content)
    }
}
