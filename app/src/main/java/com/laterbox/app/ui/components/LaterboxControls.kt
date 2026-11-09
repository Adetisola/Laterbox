package com.laterbox.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.laterbox.app.ui.theme.LaterboxTheme

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
    val Dialog = RoundedCornerShape(16.dp)
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
    tint: Color = Color.Unspecified,
    containerColor: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    badge: (@Composable () -> Unit)? = null
) {
    val colors = LaterboxTheme.colors
    val resolvedTint = if (tint != Color.Unspecified) tint else colors.textPrimary
    val resolvedContainerColor = if (containerColor != Color.Unspecified) containerColor else colors.surfaceSecondary
    val resolvedBorderColor = if (borderColor != Color.Unspecified) borderColor else colors.borderSubtle

    val interactionSource = remember { MutableInteractionSource() }
    val effectiveContainerColor = when {
        !enabled -> resolvedContainerColor.copy(alpha = 0.4f)
        selected -> colors.accentContainer
        else -> resolvedContainerColor
    }
    val effectiveTint = when {
        !enabled -> resolvedTint.copy(alpha = 0.38f)
        selected -> colors.accentPrimary
        else -> resolvedTint
    }
    val effectiveBorderColor = if (selected) colors.accentPrimary else resolvedBorderColor

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

/**
 * High-emphasis primary action button using Laterbox editorial accent,
 * 48dp minimum touch height, and 12dp corner radius.
 */
@Composable
fun LaterboxPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    @DrawableRes leadingIcon: Int? = null
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    val containerColor = if (enabled && !isLoading) colors.accentPrimary else colors.accentPrimary.copy(alpha = 0.45f)
    val contentColor = colors.surfaceElevated

    Box(
        modifier = modifier
            .heightIn(min = spacing.minTouchTarget)
            .clip(LaterboxShapeTokens.Button)
            .background(containerColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                enabled = enabled && !isLoading,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = spacing.base, vertical = spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = contentColor,
                strokeWidth = 2.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (leadingIcon != null) {
                    Icon(
                        painter = painterResource(id = leadingIcon),
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(spacing.iconSmall)
                    )
                    Spacer(modifier = Modifier.width(spacing.xs))
                }
                Text(
                    text = text,
                    style = typography.buttonLabel,
                    color = contentColor
                )
            }
        }
    }
}

/**
 * Outlined secondary button with tactile border and minimum 48dp touch bounds.
 */
@Composable
fun LaterboxSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes leadingIcon: Int? = null
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    val textColor = if (enabled) colors.textPrimary else colors.textTertiary
    val borderColor = if (enabled) colors.borderSubtle else colors.borderSubtle.copy(alpha = 0.4f)
    val bgColor = colors.surfaceSecondary

    Box(
        modifier = modifier
            .heightIn(min = spacing.minTouchTarget)
            .clip(LaterboxShapeTokens.Button)
            .background(bgColor)
            .border(BorderStroke(spacing.hairline, borderColor), LaterboxShapeTokens.Button)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = spacing.base, vertical = spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                Icon(
                    painter = painterResource(id = leadingIcon),
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(spacing.iconSmall)
                )
                Spacer(modifier = Modifier.width(spacing.xs))
            }
            Text(
                text = text,
                style = typography.buttonLabel,
                color = textColor
            )
        }
    }
}

/**
 * Text button for low-emphasis actions with 48dp touch bounds.
 */
@Composable
fun LaterboxTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Color.Unspecified
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    val effectiveColor = when {
        !enabled -> colors.textTertiary
        color != Color.Unspecified -> color
        else -> colors.accentPrimary
    }

    Box(
        modifier = modifier
            .sizeIn(minWidth = spacing.minTouchTarget, minHeight = spacing.minTouchTarget)
            .clip(LaterboxShapeTokens.Button)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = typography.buttonLabel.copy(fontWeight = FontWeight.Medium),
            color = effectiveColor
        )
    }
}

/**
 * Informational / Status Banner for settings and save overlays.
 */
@Composable
fun LaterboxBanner(
    text: String,
    modifier: Modifier = Modifier,
    isWarning: Boolean = false,
    isSuccess: Boolean = false,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    val bgColor = when {
        isWarning -> colors.statusDanger.copy(alpha = 0.12f)
        isSuccess -> colors.statusSuccess.copy(alpha = 0.12f)
        else -> colors.accentContainer
    }
    val borderColor = when {
        isWarning -> colors.statusDanger.copy(alpha = 0.4f)
        isSuccess -> colors.statusSuccess.copy(alpha = 0.4f)
        else -> colors.accentPrimary.copy(alpha = 0.4f)
    }
    val textColor = when {
        isWarning -> colors.statusDanger
        isSuccess -> colors.statusSuccess
        else -> colors.accentPrimary
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(LaterboxShapeTokens.Card)
            .background(bgColor)
            .border(BorderStroke(spacing.hairline, borderColor), LaterboxShapeTokens.Card)
            .padding(spacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = text,
                style = typography.bodySecondary,
                color = textColor,
                modifier = Modifier.weight(1f)
            )
            if (actionText != null && onActionClick != null) {
                Spacer(modifier = Modifier.width(spacing.xs))
                LaterboxTextButton(
                    text = actionText,
                    onClick = onActionClick,
                    color = textColor
                )
            }
        }
    }
}

/**
 * Sheet surface matching 24dp top corners and elevated container background.
 */
@Composable
fun LaterboxSheetSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = LaterboxTheme.colors
    Surface(
        modifier = modifier,
        shape = LaterboxShapeTokens.Sheet,
        color = colors.surfaceElevated,
        tonalElevation = 6.dp,
        content = content
    )
}
