package com.laterbox.app.ui.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laterbox.app.data.preferences.ThemeMode
import com.laterbox.app.data.preferences.UserPreferences
import com.laterbox.app.ui.theme.LaterboxTheme
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAutoMarkAsReadChange: (Boolean) -> Unit,
    onAllowSystemBackupChange: (Boolean) -> Unit,
    onExportBackup: suspend (OutputStream) -> Result<Int>,
    onImportBackup: suspend (InputStream) -> Result<com.laterbox.app.data.backup.BackupRestoreResult>,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showLicensesDialog by remember { mutableStateOf(false) }

    // Storage Access Framework Launchers
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val outputStream = context.contentResolver.openOutputStream(uri)
                    if (outputStream != null) {
                        val result = onExportBackup(outputStream)
                        if (result.isSuccess) {
                            Toast.makeText(context, "Exported ${result.getOrNull()} links successfully", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Export failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val result = onImportBackup(inputStream)
                        if (result.isSuccess) {
                            val r = result.getOrThrow()
                            Toast.makeText(
                                context,
                                "Restored ${r.restoredLinksCount} links, ${r.restoredCollectionsCount} collections (${r.skippedDuplicatesCount} duplicates skipped)",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(context, "Import failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Import error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
            // Header with back navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.base, vertical = spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button (min 48dp touch target)
                Box(
                    modifier = Modifier
                        .size(spacing.minTouchTarget)
                        .clip(RoundedCornerShape(spacing.radiusSmall))
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Back to inbox",
                            onClick = onBackClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "←",
                        style = typography.uiMedium.copy(fontSize = 20.sp),
                        color = colors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.width(spacing.xs))

                Column {
                    Text(
                        text = "Settings",
                        style = typography.displayLarge.copy(fontSize = 22.sp),
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Preferences & privacy",
                        style = typography.metaText,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.sm))

            // Section: Appearance
            SectionHeader(title = "Appearance")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.base)
                    .clip(RoundedCornerShape(spacing.radiusMedium))
                    .background(colors.surfacePrimary)
                    .border(spacing.hairline, colors.borderSubtle, RoundedCornerShape(spacing.radiusMedium))
                    .padding(spacing.md)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(
                        text = "Theme",
                        style = typography.uiMedium,
                        color = colors.textPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                    ) {
                        ThemeOptionChip(
                            label = "System",
                            isSelected = preferences.themeMode == ThemeMode.SYSTEM,
                            onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionChip(
                            label = "Ivory (Light)",
                            isSelected = preferences.themeMode == ThemeMode.LIGHT,
                            onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionChip(
                            label = "Charcoal (Dark)",
                            isSelected = preferences.themeMode == ThemeMode.DARK,
                            onClick = { onThemeModeChange(ThemeMode.DARK) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.lg))

            // Section: Reading & Behavior
            SectionHeader(title = "Reading & Behavior")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.base)
                    .clip(RoundedCornerShape(spacing.radiusMedium))
                    .background(colors.surfacePrimary)
                    .border(spacing.hairline, colors.borderSubtle, RoundedCornerShape(spacing.radiusMedium))
                    .padding(spacing.md)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    // Auto-mark as read
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-mark as read",
                                style = typography.uiMedium,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Mark links as read automatically when opened",
                                style = typography.bodySecondary.copy(fontSize = 12.sp),
                                color = colors.textSecondary
                            )
                        }

                        Switch(
                            checked = preferences.autoMarkAsRead,
                            onCheckedChange = onAutoMarkAsReadChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.surfaceElevated,
                                checkedTrackColor = colors.accentPrimary
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(spacing.hairline)
                            .background(colors.borderSubtle)
                    )

                    // System backup toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Android OS Auto-Backup",
                                style = typography.uiMedium,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Allow encrypted system cloud backup for local database",
                                style = typography.bodySecondary.copy(fontSize = 12.sp),
                                color = colors.textSecondary
                            )
                        }

                        Switch(
                            checked = preferences.allowSystemBackup,
                            onCheckedChange = onAllowSystemBackupChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.surfaceElevated,
                                checkedTrackColor = colors.accentPrimary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.lg))

            // Section: Data & Backup (Local-First)
            SectionHeader(title = "Data & Storage (Local-First)")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.base)
                    .clip(RoundedCornerShape(spacing.radiusMedium))
                    .background(colors.surfacePrimary)
                    .border(spacing.hairline, colors.borderSubtle, RoundedCornerShape(spacing.radiusMedium))
                    .padding(spacing.md)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(
                        text = "Backup & Restore",
                        style = typography.uiMedium,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Export your entire library as an open JSON file or restore from a previous backup. No proprietary lock-in.",
                        style = typography.bodySecondary.copy(fontSize = 12.sp),
                        color = colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(spacing.xs))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        // Export button (min 48dp touch target)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(spacing.minTouchTarget)
                                .clip(RoundedCornerShape(spacing.radiusSmall))
                                .background(colors.surfaceSecondary)
                                .border(spacing.hairline, colors.borderSubtle, RoundedCornerShape(spacing.radiusSmall))
                                .clickable {
                                    val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                    exportLauncher.launch("laterbox_backup_$dateStr.json")
                                }
                                .padding(horizontal = spacing.sm),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Export JSON",
                                style = typography.uiMedium.copy(fontSize = 13.sp),
                                color = colors.accentPrimary
                            )
                        }

                        // Import button (min 48dp touch target)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(spacing.minTouchTarget)
                                .clip(RoundedCornerShape(spacing.radiusSmall))
                                .background(colors.surfaceSecondary)
                                .border(spacing.hairline, colors.borderSubtle, RoundedCornerShape(spacing.radiusSmall))
                                .clickable {
                                    importLauncher.launch(arrayOf("application/json", "text/*"))
                                }
                                .padding(horizontal = spacing.sm),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Import JSON",
                                style = typography.uiMedium.copy(fontSize = 13.sp),
                                color = colors.textPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.lg))

            // Section: About & Privacy
            SectionHeader(title = "About & Privacy")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.base)
                    .clip(RoundedCornerShape(spacing.radiusMedium))
                    .background(colors.surfacePrimary)
                    .border(spacing.hairline, colors.borderSubtle, RoundedCornerShape(spacing.radiusMedium))
                    .padding(spacing.md)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(
                        text = "Laterbox v1.0.0",
                        style = typography.uiMedium,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "A calm, local-first reading inbox. Your links and notes remain entirely on your device. No user accounts, no telemetry, and no third-party tracking.",
                        style = typography.bodySecondary,
                        color = colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(spacing.xs))

                    // Open source licenses button (min 48dp touch target)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(spacing.minTouchTarget)
                            .clip(RoundedCornerShape(spacing.radiusSmall))
                            .background(colors.surfaceSecondary)
                            .clickable { showLicensesDialog = true }
                            .padding(horizontal = spacing.md),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "Open Source Licenses & Typography",
                            style = typography.uiMedium.copy(fontSize = 13.sp),
                            color = colors.accentPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
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
                            text = "Libraries:\n• AndroidX Jetpack & Jetpack Compose (Apache 2.0)\n• Room SQLite Database (Apache 2.0)\n• Coil Image Loading (Apache 2.0)\n• Jsoup HTML Parser (MIT License)\n• Kotlin & Coroutines (Apache 2.0)",
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

@Composable
private fun SectionHeader(title: String) {
    val typography = LaterboxTheme.typography
    val colors = LaterboxTheme.colors
    val spacing = LaterboxTheme.spacing

    Text(
        text = title,
        style = typography.metaText,
        color = colors.textSecondary,
        modifier = Modifier.padding(horizontal = spacing.base, vertical = spacing.xs)
    )
}

@Composable
private fun ThemeOptionChip(
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
            .height(36.dp)
            .clip(RoundedCornerShape(spacing.radiusSmall))
            .background(bg)
            .border(spacing.hairline, borderColor, RoundedCornerShape(spacing.radiusSmall))
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = typography.metaText.copy(fontSize = 11.sp),
            color = textColor,
            maxLines = 1
        )
    }
}
