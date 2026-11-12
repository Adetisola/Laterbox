package com.laterbox.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.laterbox.app.data.preferences.ThemeMode
import com.laterbox.app.data.preferences.UserPreferences
import com.laterbox.app.ui.components.LaterboxIconButton
import com.laterbox.app.ui.components.LaterboxIcons
import com.laterbox.app.ui.components.LaterboxShapeTokens
import com.laterbox.app.ui.theme.LaterboxTheme

@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    onThemeModeChange: (ThemeMode) -> Unit,
    onNavigateToAppearanceProfile: () -> Unit,
    onNavigateToRemindersAi: () -> Unit,
    onNavigateToCaptureShortcuts: () -> Unit,
    onNavigateToReadingBehavior: () -> Unit,
    onNavigateToMediaDownloads: () -> Unit,
    onNavigateToDataStorage: () -> Unit,
    onNavigateToAboutPrivacy: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundPrimary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header: Arrow Back + Title
            Row(
                modifier = Modifier
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

                Column {
                    Text(
                        text = "Settings",
                        style = typography.displayLarge,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Preferences, media engine & privacy",
                        style = typography.metaText,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.sm))

            // Quick Controls: Theme Mode
            SettingsSectionHeader(title = "Quick Controls")
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(
                        text = "Theme Mode",
                        style = typography.uiMedium,
                        color = colors.textPrimary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                    ) {
                        ThemeOptionChip(
                            label = "System Default",
                            isSelected = preferences.themeMode == ThemeMode.SYSTEM,
                            onClick = { onThemeModeChange(ThemeMode.SYSTEM) }
                        )
                        ThemeOptionChip(
                            label = "Light (Ivory)",
                            isSelected = preferences.themeMode == ThemeMode.LIGHT,
                            onClick = { onThemeModeChange(ThemeMode.LIGHT) }
                        )
                        ThemeOptionChip(
                            label = "Dark (Charcoal)",
                            isSelected = preferences.themeMode == ThemeMode.DARK,
                            onClick = { onThemeModeChange(ThemeMode.DARK) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.md))

            // Category Navigation Cards
            SettingsSectionHeader(title = "Categories")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.base),
                verticalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                SettingsCategoryRow(
                    iconRes = LaterboxIcons.User,
                    title = "Appearance & Profile",
                    subtitle = "Theme, reader font size, personal greeting name",
                    onClick = onNavigateToAppearanceProfile
                )

                SettingsCategoryRow(
                    iconRes = LaterboxIcons.Sparkles,
                    title = "Reminders & AI",
                    subtitle = "Calendar schedules, notifications, Google AI Studio",
                    onClick = onNavigateToRemindersAi
                )

                SettingsCategoryRow(
                    iconRes = LaterboxIcons.Copy,
                    title = "Capture & Shortcuts",
                    subtitle = "Clipboard suggestion banner, Quick Settings tile, link shortcut",
                    onClick = onNavigateToCaptureShortcuts
                )

                SettingsCategoryRow(
                    iconRes = LaterboxIcons.ViewList,
                    title = "Reading & Behaviour",
                    subtitle = "Auto-mark as read, inbox reading workflow",
                    onClick = onNavigateToReadingBehavior
                )

                SettingsCategoryRow(
                    iconRes = LaterboxIcons.Download,
                    title = "Media & Downloads",
                    subtitle = "yt-dlp extractor, network policy, warm engine, export storage",
                    onClick = onNavigateToMediaDownloads
                )

                SettingsCategoryRow(
                    iconRes = LaterboxIcons.Folder,
                    title = "Data & Storage",
                    subtitle = "JSON backup & restore, Android OS auto-backup",
                    onClick = onNavigateToDataStorage
                )

                SettingsCategoryRow(
                    iconRes = LaterboxIcons.Settings,
                    title = "About & Privacy",
                    subtitle = "App version, update checker, licenses, local-first notice",
                    onClick = onNavigateToAboutPrivacy
                )
            }

            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}

@Composable
private fun SettingsCategoryRow(
    iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(spacing.radiusMedium))
            .background(colors.surfacePrimary)
            .border(spacing.hairline, colors.borderSubtle, RoundedCornerShape(spacing.radiusMedium))
            .clickable(
                role = Role.Button,
                onClickLabel = title,
                onClick = onClick
            )
            .padding(horizontal = spacing.md, vertical = spacing.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(LaterboxShapeTokens.IconButton)
                    .background(colors.surfaceSecondary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colors.accentPrimary
                )
            }

            Spacer(modifier = Modifier.width(spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = typography.uiMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary
                )
                Text(
                    text = subtitle,
                    style = typography.metaText,
                    color = colors.textSecondary,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(spacing.xs))

            Icon(
                painter = painterResource(id = LaterboxIcons.ChevronRight),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.textSecondary.copy(alpha = 0.6f)
            )
        }
    }
}
