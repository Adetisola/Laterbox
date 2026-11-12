package com.laterbox.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.laterbox.app.ui.components.LaterboxIconButton
import com.laterbox.app.ui.components.LaterboxIcons
import com.laterbox.app.ui.theme.LaterboxTheme
import java.util.Locale

@Composable
fun SettingsSubpageHeader(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.base, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LaterboxIconButton(
            iconRes = LaterboxIcons.ArrowLeft,
            contentDescription = "Back",
            onClick = onBackClick
        )

        Spacer(modifier = Modifier.width(spacing.sm))

        Text(
            text = title,
            style = typography.wordmark,
            color = colors.textPrimary
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier) {
    val typography = LaterboxTheme.typography
    val colors = LaterboxTheme.colors
    val spacing = LaterboxTheme.spacing

    Text(
        text = title,
        style = typography.metaText,
        color = colors.textSecondary,
        modifier = modifier.padding(horizontal = spacing.base, vertical = spacing.xs)
    )
}

@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = LaterboxTheme.colors
    val spacing = LaterboxTheme.spacing

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.base)
            .clip(RoundedCornerShape(spacing.radiusMedium))
            .background(colors.surfacePrimary)
            .border(spacing.hairline, colors.borderSubtle, RoundedCornerShape(spacing.radiusMedium))
            .padding(spacing.md)
    ) {
        content()
    }
}

@Composable
fun ThemeOptionChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    val bg = if (isSelected) colors.surfaceSecondary else colors.backgroundPrimary
    val borderColor = if (isSelected) colors.accentPrimary else colors.borderSubtle
    val textColor = if (isSelected) colors.accentPrimary else colors.textSecondary

    Box(
        modifier = modifier
            .height(spacing.chipHeight)
            .clip(RoundedCornerShape(spacing.radiusSmall))
            .background(bg)
            .border(spacing.hairline, borderColor, RoundedCornerShape(spacing.radiusSmall))
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = typography.metaText,
            color = textColor,
            maxLines = 1
        )
    }
}

@Composable
fun SettingsDivider(
    color: Color = LaterboxTheme.colors.borderSubtle,
    modifier: Modifier = Modifier
) {
    val spacing = LaterboxTheme.spacing
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(spacing.hairline)
            .background(color)
    )
}

@Composable
fun ScheduleRow(
    title: String,
    description: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = typography.uiMedium,
                    color = colors.textPrimary
                )
                Text(
                    text = description,
                    style = typography.metaText,
                    color = colors.textSecondary
                )
            }

            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.surfaceElevated,
                    checkedTrackColor = colors.accentPrimary
                )
            )
        }

        if (enabled) {
            Spacer(modifier = Modifier.height(spacing.xxs))
            content()
        }
    }
}

fun formatTime(hour: Int, minute: Int): String {
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val amPm = if (hour >= 12) "PM" else "AM"
    return String.format(Locale.getDefault(), "%d:%02d %s", displayHour, minute, amPm)
}

sealed class TimePickerTarget(val title: String, val hour: Int, val minute: Int) {
    class DailyDigest(h: Int, m: Int) : TimePickerTarget("Daily Digest Time", h, m)
    class Morning(h: Int, m: Int) : TimePickerTarget("Morning Reminder Time", h, m)
    class Evening(h: Int, m: Int) : TimePickerTarget("Evening Reminder Time", h, m)
    class Weekend(h: Int, m: Int) : TimePickerTarget("Weekend Roundup Time", h, m)
    class Custom(h: Int, m: Int) : TimePickerTarget("Custom Schedule Time", h, m)
}
