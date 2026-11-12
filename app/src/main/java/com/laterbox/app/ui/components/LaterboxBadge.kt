package com.laterbox.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.laterbox.app.ui.theme.LaterboxTheme

enum class LaterboxBadgeVariant {
    ACCENT,   // accentPrimary with accentContainer background
    SUCCESS,  // statusSuccess with light green container
    WARNING,  // statusWarning with light amber container
    DANGER,   // statusDanger with light red container
    NEUTRAL   // textSecondary with surfaceSecondary container
}

/**
 * Standardised badge component for metadata labels, content types, and status cues.
 */
@Composable
fun LaterboxBadge(
    text: String,
    modifier: Modifier = Modifier,
    variant: LaterboxBadgeVariant = LaterboxBadgeVariant.ACCENT,
    shape: Shape = LaterboxTheme.shapes.Badge,
    leadingIcon: Painter? = null,
    isMonospace: Boolean = false,
    outlined: Boolean = false
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    val contentColor = when (variant) {
        LaterboxBadgeVariant.ACCENT -> colors.accentPrimary
        LaterboxBadgeVariant.SUCCESS -> colors.statusSuccess
        LaterboxBadgeVariant.WARNING -> colors.statusWarning
        LaterboxBadgeVariant.DANGER -> colors.statusDanger
        LaterboxBadgeVariant.NEUTRAL -> colors.textSecondary
    }

    val containerColor = when (variant) {
        LaterboxBadgeVariant.ACCENT -> colors.accentContainer
        LaterboxBadgeVariant.SUCCESS -> colors.statusSuccess.copy(alpha = 0.12f)
        LaterboxBadgeVariant.WARNING -> colors.statusWarning.copy(alpha = 0.12f)
        LaterboxBadgeVariant.DANGER -> colors.statusDanger.copy(alpha = 0.12f)
        LaterboxBadgeVariant.NEUTRAL -> colors.surfaceSecondary
    }

    val borderColor = if (outlined) contentColor.copy(alpha = 0.35f) else Color.Transparent
    val textStyle: TextStyle = if (isMonospace) typography.badgeText else typography.badgeLabel

    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .then(
                if (outlined) Modifier.border(spacing.hairline, borderColor, shape) else Modifier
            )
            .padding(horizontal = spacing.badgePaddingHorizontal, vertical = spacing.badgePaddingVertical),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                Icon(
                    painter = leadingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
            }
            Text(
                text = text,
                style = textStyle,
                color = contentColor
            )
        }
    }
}
