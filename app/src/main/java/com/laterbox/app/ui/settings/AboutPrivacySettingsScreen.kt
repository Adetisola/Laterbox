package com.laterbox.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.laterbox.app.BuildConfig
import com.laterbox.app.ui.components.LaterboxSecondaryButton
import com.laterbox.app.ui.theme.LaterboxTheme
import com.laterbox.app.update.AppUpdateDialog
import com.laterbox.app.update.AppUpdateInfo

@Composable
fun AboutPrivacySettingsScreen(
    isCheckingUpdate: Boolean,
    availableUpdateInfo: AppUpdateInfo?,
    onCheckForUpdates: () -> Unit,
    onDismissUpdateDialog: () -> Unit,
    onPostponeUpdate: (String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    var showLicensesDialog by remember { mutableStateOf(false) }

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
            SettingsSubpageHeader(
                title = "About & Privacy",
                onBackClick = onBackClick
            )

            Spacer(modifier = Modifier.height(spacing.xs))

            SettingsSectionHeader(title = "About Laterbox")
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(
                        text = "Laterbox v${BuildConfig.VERSION_NAME}",
                        style = typography.uiMedium,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "A calm, local-first reading inbox. Your links and notes remain entirely on your device. No user accounts, no telemetry, and no third-party tracking.",
                        style = typography.bodySecondary,
                        color = colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(spacing.xs))

                    Text(
                        text = "Third-Party Services & Privacy Notice",
                        style = typography.uiMedium,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "• On-Device Media Extraction: Media inspection and downloads run locally on your device via yt-dlp, QuickJS, and FFmpeg without intermediate proxy servers.\n" +
                               "• Public X/Twitter Previews: api.fxtwitter.com (FxEmbed) is contacted strictly to resolve public X post metadata and media when saved, without accounts or tracking.\n" +
                               "• Smart Reminders (Optional): If you configure your own Google AI Studio API key, link snippets are sent directly to Google AI Studio to extract reminder dates. No personal data is stored or tracked.",
                        style = typography.bodySecondary,
                        color = colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(spacing.xs))

                    LaterboxSecondaryButton(
                        text = if (isCheckingUpdate) "Checking for updates..." else "Check for updates",
                        enabled = !isCheckingUpdate,
                        onClick = onCheckForUpdates,
                        modifier = Modifier.fillMaxWidth()
                    )

                    LaterboxSecondaryButton(
                        text = "Open Source Licenses & Typography",
                        onClick = { showLicensesDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }

        // App Update Dialog
        if (availableUpdateInfo != null) {
            AppUpdateDialog(
                updateInfo = availableUpdateInfo,
                onDismiss = onDismissUpdateDialog,
                onPostpone = onPostponeUpdate
            )
        }

        // Licenses Dialog
        if (showLicensesDialog) {
            AlertDialog(
                onDismissRequest = { showLicensesDialog = false },
                containerColor = colors.surfaceElevated,
                title = {
                    Text(
                        text = "Open Source Licenses",
                        style = typography.titleSmall,
                        color = colors.textPrimary
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        Text(
                            text = "Typography:\n• Newsreader — SIL Open Font License 1.1 (Production Type)\n• Inter — SIL Open Font License 1.1 (Rasmus Andersson)\n• JetBrains Mono — SIL Open Font License 1.1 (JetBrains)",
                            style = typography.bodySecondary,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(spacing.xs))
                        Text(
                            text = "Libraries & Media Engines:\n• yt-dlp (The Unlicense)\n• QuickJS (MIT License)\n• FFmpeg (LGPL / GPL)\n• AndroidX Jetpack & Jetpack Compose (Apache 2.0)\n• Room SQLite Database (Apache 2.0)\n• Coil Image Loading (Apache 2.0)\n• Jsoup HTML Parser (MIT License)\n• Kotlin & Coroutines (Apache 2.0)",
                            style = typography.bodySecondary,
                            color = colors.textSecondary
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLicensesDialog = false }) {
                        Text(
                            text = "Close",
                            style = typography.uiMedium,
                            color = colors.accentPrimary
                        )
                    }
                }
            )
        }
    }
}
