package com.laterbox.app.ui.settings

import android.app.StatusBarManager
import android.content.ComponentName
import android.os.Build
import android.widget.Toast
import android.graphics.drawable.Icon as AndroidIcon
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.laterbox.app.R
import com.laterbox.app.capture.accessibility.AccessibilityServiceStateHelper
import com.laterbox.app.data.preferences.UserPreferences
import com.laterbox.app.quicksave.QuickSaveTileService
import com.laterbox.app.ui.components.LaterboxBanner
import com.laterbox.app.ui.components.LaterboxPrimaryButton
import com.laterbox.app.ui.components.LaterboxSecondaryButton
import com.laterbox.app.ui.theme.LaterboxTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureShortcutsSettingsScreen(
    preferences: UserPreferences,
    onAccessibilityCaptureEnabledChange: (Boolean) -> Unit,
    onClipboardSuggestionEnabledChange: (Boolean) -> Unit,
    onToggleExcludedPackage: (String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing
    val context = LocalContext.current

    var showAccessibilityOnboarding by remember { mutableStateOf(false) }
    var showExcludedAppsDialog by remember { mutableStateOf(false) }
    val accessibilitySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isAccessibilityEnabledInSystem = remember(preferences.accessibilityCaptureEnabled) {
        AccessibilityServiceStateHelper.isAccessibilityServiceEnabled(context)
    }

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
                title = "Capture & Shortcuts",
                onBackClick = onBackClick
            )

            Spacer(modifier = Modifier.height(spacing.xs))

            SettingsSectionHeader(title = "Quick Capture")
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    // 1. Accessibility Link Selection Shortcut
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Link Selection Shortcut",
                                style = typography.uiMedium,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Offers a small floating shortcut when you highlight a web link in supported apps.",
                                style = typography.metaText,
                                color = colors.textSecondary
                            )
                        }
                        Switch(
                            checked = preferences.accessibilityCaptureEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && !isAccessibilityEnabledInSystem) {
                                    showAccessibilityOnboarding = true
                                } else {
                                    onAccessibilityCaptureEnabledChange(enabled)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.surfaceElevated,
                                checkedTrackColor = colors.accentPrimary
                            )
                        )
                    }

                    if (preferences.accessibilityCaptureEnabled) {
                        if (!isAccessibilityEnabledInSystem) {
                            LaterboxBanner(
                                text = "Android Accessibility permission is required for floating selection capture.",
                                isWarning = true
                            )
                            LaterboxSecondaryButton(
                                text = "Grant Permission in Settings",
                                onClick = {
                                    AccessibilityServiceStateHelper.openAccessibilitySettings(context)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = "✓ Active in Android Accessibility",
                                style = typography.metaText,
                                color = colors.accentPrimary
                            )
                        }

                        LaterboxSecondaryButton(
                            text = "Excluded Apps (${preferences.excludedCapturePackages.size})",
                            onClick = { showExcludedAppsDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    SettingsDivider()

                    // 2. Foreground Clipboard Suggestion
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Clipboard Suggestion Banner",
                                style = typography.uiMedium,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Suggests saving when you open Laterbox if a webpage link is on your clipboard.",
                                style = typography.metaText,
                                color = colors.textSecondary
                            )
                        }
                        Switch(
                            checked = preferences.clipboardSuggestionEnabled,
                            onCheckedChange = onClipboardSuggestionEnabledChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.surfaceElevated,
                                checkedTrackColor = colors.accentPrimary
                            )
                        )
                    }

                    SettingsDivider()

                    // 3. Native Text Selection Menu Info
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        Text(
                            text = "Native Text Selection Menu",
                            style = typography.uiMedium,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Highlight text in any application and tap 'Save to Laterbox' in the text toolbar. Always available without extra permissions.",
                            style = typography.metaText,
                            color = colors.textSecondary
                        )
                    }

                    SettingsDivider()

                    // 4. Quick Settings Tile
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(
                            text = "Quick Settings Tile",
                            style = typography.uiMedium,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Save any copied link instantly from the Android Quick Settings drawer without switching apps.",
                            style = typography.metaText,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(spacing.xxs))
                        LaterboxPrimaryButton(
                            text = "Add Tile to Quick Settings",
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val statusBarManager = context.getSystemService(StatusBarManager::class.java)
                                    val componentName = ComponentName(context, QuickSaveTileService::class.java)
                                    statusBarManager?.requestAddTileService(
                                        componentName,
                                        context.getString(R.string.save_to_laterbox),
                                        AndroidIcon.createWithResource(context, R.drawable.ic_tile_save),
                                        { it.run() },
                                        { result ->
                                            if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED) {
                                                Toast.makeText(context, "Tile added to Quick Settings!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Swipe down Quick Settings, tap the pencil edit icon, and drag 'Save to Laterbox' into your tiles.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }

        if (showAccessibilityOnboarding) {
            AccessibilityOnboardingSheet(
                sheetState = accessibilitySheetState,
                onDismissRequest = { showAccessibilityOnboarding = false },
                onEnableClicked = {
                    onAccessibilityCaptureEnabledChange(true)
                }
            )
        }

        if (showExcludedAppsDialog) {
            ExcludedAppsDialog(
                excludedPackages = preferences.excludedCapturePackages,
                onTogglePackage = onToggleExcludedPackage,
                onDismissRequest = { showExcludedAppsDialog = false }
            )
        }
    }
}
