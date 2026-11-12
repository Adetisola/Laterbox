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
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laterbox.app.ui.theme.LaterboxTheme

typealias LaterboxShapeTokens = com.laterbox.app.ui.theme.LaterboxShapeTokens


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
    @DrawableRes leadingIcon: Int? = null,
    isDestructive: Boolean = false
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    val primaryColor = if (isDestructive) colors.statusDanger else colors.accentPrimary
    val containerColor = if (enabled && !isLoading) primaryColor else primaryColor.copy(alpha = 0.45f)
    val contentColor = colors.onAccentPrimary

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

/**
 * Standardised filter chip matching the Inbox reference design.
 * 12dp corner radius (LaterboxShapeTokens.Chip), 32dp height, hairline border.
 */
@Composable
fun LaterboxFilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeText: String? = null
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    val bg = if (isSelected) colors.accentPrimary else colors.surfacePrimary
    val textColor = if (isSelected) colors.surfaceElevated else colors.textSecondary
    val borderColor = if (isSelected) colors.accentPrimary else colors.borderSubtle

    Box(
        modifier = modifier
            .height(32.dp)
            .clip(LaterboxShapeTokens.Chip)
            .background(bg)
            .border(spacing.hairline, borderColor, LaterboxShapeTokens.Chip)
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.md),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                style = typography.uiMedium,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
            if (!badgeText.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) colors.surfaceElevated.copy(alpha = 0.25f) else colors.accentContainer)
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = typography.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = if (isSelected) colors.surfaceElevated else colors.accentPrimary
                    )
                }
            }
        }
    }
}

