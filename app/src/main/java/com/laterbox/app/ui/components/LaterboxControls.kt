package com.laterbox.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Cohesive shape tokens across Laterbox:
 * - 12dp: Buttons, chips, icon backdrops, text fields
 * - 16dp: Link cards, collapsed FAB
 * - 20dp: Floating bottom navigation pill
 * - 24dp: Bottom sheets
 */
object LaterboxShapeTokens {
    val Button = RoundedCornerShape(12.dp)
    val Chip = RoundedCornerShape(12.dp)
    val IconButton = RoundedCornerShape(12.dp)
    val InputField = RoundedCornerShape(12.dp)
    val Card = RoundedCornerShape(16.dp)
    val Fab = RoundedCornerShape(16.dp)
    val PillNav = RoundedCornerShape(20.dp)
    val Sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
}

/**
 * Standardised icon button for Laterbox with an obvious, tactile backdrop,
 * 12dp corner radius, 48dp minimum touch bounds, and 22dp Hugeicons rendering.
 */
@Composable
fun LaterboxIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    tint: Color = LaterboxTheme.colors.textPrimary,
    containerColor: Color = LaterboxTheme.colors.surfaceCard,
    borderColor: Color = LaterboxTheme.colors.borderSubtle,
    badge: (@Composable () -> Unit)? = null
) {
    val colors = LaterboxTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val effectiveContainerColor = when {
        !enabled -> containerColor.copy(alpha = 0.4f)
        selected -> colors.accentContainer
        else -> containerColor
    }
    val effectiveTint = when {
        !enabled -> tint.copy(alpha = 0.38f)
        selected -> colors.accentPrimary
        else -> tint
    }
    val effectiveBorderColor = if (selected) colors.accentPrimary else borderColor

    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(LaterboxShapeTokens.IconButton)
                .background(effectiveContainerColor)
                .border(
                    BorderStroke(1.dp, effectiveBorderColor),
                    LaterboxShapeTokens.IconButton
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = contentDescription,
                modifier = Modifier.size(22.dp),
                tint = effectiveTint
            )
            if (badge != null) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    badge()
                }
            }
        }
    }
}
