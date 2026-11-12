package com.laterbox.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laterbox.app.data.backup.BackupRestoreResult
import com.laterbox.app.data.preferences.UserPreferences
import com.laterbox.app.ui.components.LaterboxSecondaryButton
import com.laterbox.app.ui.theme.LaterboxTheme
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DataStorageSettingsScreen(
    preferences: UserPreferences,
    onAllowSystemBackupChange: (Boolean) -> Unit,
    onExportBackup: suspend (OutputStream) -> Result<Int>,
    onImportBackup: suspend (InputStream) -> Result<BackupRestoreResult>,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        val result = onExportBackup(outputStream)
                        if (result.isSuccess) {
                            val count = result.getOrNull() ?: 0
                            Toast.makeText(context, "Exported $count items successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Export failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val result = onImportBackup(inputStream)
                        if (result.isSuccess) {
                            val stats = result.getOrNull()
                            val msg = stats?.let {
                                "Restored: ${it.restoredLinksCount} links, ${it.restoredCollectionsCount} collections"
                            } ?: "Backup restored successfully"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Import failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
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
            SettingsSubpageHeader(
                title = "Data & Storage",
                onBackClick = onBackClick
            )

            Spacer(modifier = Modifier.height(spacing.xs))

            SettingsSectionHeader(title = "Data & Storage (Local-First)")
            SettingsCard {
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
                        LaterboxSecondaryButton(
                            text = "Export JSON",
                            onClick = {
                                val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                exportLauncher.launch("laterbox_backup_$dateStr.json")
                            },
                            modifier = Modifier.weight(1f)
                        )

                        LaterboxSecondaryButton(
                            text = "Import JSON",
                            onClick = {
                                importLauncher.launch(arrayOf("application/json", "text/*"))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    SettingsDivider()

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

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
