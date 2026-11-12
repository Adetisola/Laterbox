package com.laterbox.app.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.laterbox.app.data.preferences.MediaDownloadNetworkPolicy
import com.laterbox.app.data.preferences.UserPreferences
import com.laterbox.app.media.data.adapters.ytdlp.FfmpegInitState
import com.laterbox.app.media.data.adapters.ytdlp.YtDlpDiagnosticReport
import com.laterbox.app.media.data.adapters.ytdlp.YtDlpInitState
import com.laterbox.app.ui.components.LaterboxBanner
import com.laterbox.app.ui.components.LaterboxIcons
import com.laterbox.app.ui.components.LaterboxShapeTokens
import com.laterbox.app.ui.theme.LaterboxTheme

@Composable
fun MediaDownloadsSettingsScreen(
    preferences: UserPreferences,
    onMediaDownloadNetworkPolicyChange: (MediaDownloadNetworkPolicy) -> Unit,
    onAutoInspectMediaOnSaveChange: (Boolean) -> Unit,
    onFastEngineEnabledChange: (Boolean) -> Unit,
    isUpdatingYtDlp: Boolean,
    onUpdateYtDlpClick: () -> Unit,
    ytDlpState: YtDlpInitState,
    ffmpegState: FfmpegInitState,
    onGetDiagnosticReport: () -> YtDlpDiagnosticReport?,
    onGetDiagnosticReportText: () -> String,
    onRetryInitializeEngine: () -> Unit,
    onSetCustomExportDirectory: (String, String) -> Unit,
    onClearCustomExportDirectory: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    var diagnosticReport by remember { mutableStateOf<YtDlpDiagnosticReport?>(null) }
    var diagnosticReportText by remember { mutableStateOf<String?>(null) }

    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: SecurityException) {}

            val docFile = DocumentFile.fromTreeUri(context, uri)
            val dirName = docFile?.name ?: "Selected Folder"
            onSetCustomExportDirectory(uri.toString(), dirName)
            Toast.makeText(context, "Export directory set to: $dirName", Toast.LENGTH_SHORT).show()
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
                title = "Media & Downloads",
                onBackClick = onBackClick
            )

            Spacer(modifier = Modifier.height(spacing.xs))

            SettingsSectionHeader(title = "Media Library & Downloads")
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    // Download network policy
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(
                            text = "Download Network Policy",
                            style = typography.uiMedium,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Choose which network connection to use for media downloads",
                            style = typography.bodySecondary.copy(fontSize = 12.sp),
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(spacing.xxs))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                        ) {
                            ThemeOptionChip(
                                label = "Wi-Fi Only",
                                isSelected = preferences.mediaDownloadNetworkPolicy == MediaDownloadNetworkPolicy.WIFI_ONLY,
                                onClick = { onMediaDownloadNetworkPolicyChange(MediaDownloadNetworkPolicy.WIFI_ONLY) }
                            )
                            ThemeOptionChip(
                                label = "Wi-Fi for >20 MB (Default)",
                                isSelected = preferences.mediaDownloadNetworkPolicy == MediaDownloadNetworkPolicy.WIFI_OVER_20MB,
                                onClick = { onMediaDownloadNetworkPolicyChange(MediaDownloadNetworkPolicy.WIFI_OVER_20MB) }
                            )
                            ThemeOptionChip(
                                label = "Any Network",
                                isSelected = preferences.mediaDownloadNetworkPolicy == MediaDownloadNetworkPolicy.ANY_NETWORK,
                                onClick = { onMediaDownloadNetworkPolicyChange(MediaDownloadNetworkPolicy.ANY_NETWORK) }
                            )
                        }
                    }

                    SettingsDivider()

                    // Auto-inspect links on save
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-inspect links on save",
                                style = typography.uiMedium,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Automatically detect downloadable media when links are saved",
                                style = typography.bodySecondary.copy(fontSize = 12.sp),
                                color = colors.textSecondary
                            )
                        }

                        Switch(
                            checked = preferences.autoInspectMediaOnSave,
                            onCheckedChange = onAutoInspectMediaOnSaveChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.surfaceElevated,
                                checkedTrackColor = colors.accentPrimary
                            )
                        )
                    }

                    SettingsDivider()

                    // Fast engine (warm background extractor process)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Fast engine",
                                style = typography.uiMedium,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Keep extractor warm in background for faster format discovery",
                                style = typography.bodySecondary.copy(fontSize = 12.sp),
                                color = colors.textSecondary
                            )
                        }

                        Switch(
                            checked = preferences.fastEngineEnabled,
                            onCheckedChange = onFastEngineEnabledChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.surfaceElevated,
                                checkedTrackColor = colors.accentPrimary
                            )
                        )
                    }

                    SettingsDivider()

                    // yt-dlp extractor engine status and diagnostics
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Media Extractor (yt-dlp)",
                                    style = typography.uiMedium,
                                    color = colors.textPrimary
                                )
                                val statusText = when (ytDlpState) {
                                    is YtDlpInitState.Initialized -> {
                                        val v = ytDlpState.version?.ifBlank { preferences.ytDlpVersion } ?: preferences.ytDlpVersion
                                        if (v.isNotBlank()) "Ready (v$v)" else "Ready (bundled engine)"
                                    }
                                    is YtDlpInitState.Initializing -> "Initializing engine…"
                                    is YtDlpInitState.Failed -> "Initialization failed"
                                    is YtDlpInitState.NotInitialized -> "Not initialized"
                                }
                                Text(
                                    text = statusText,
                                    style = typography.bodySecondary.copy(fontSize = 12.sp),
                                    color = if (ytDlpState is YtDlpInitState.Failed) colors.statusDanger else colors.textSecondary
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                OutlinedButton(
                                    onClick = {
                                        diagnosticReport = onGetDiagnosticReport()
                                        diagnosticReportText = onGetDiagnosticReportText()
                                        showDiagnosticsDialog = true
                                    },
                                    shape = LaterboxShapeTokens.Button
                                ) {
                                    Text(
                                        text = "Diagnostics",
                                        style = typography.uiMedium.copy(fontSize = 13.sp),
                                        color = colors.accentPrimary
                                    )
                                }

                                OutlinedButton(
                                    onClick = onUpdateYtDlpClick,
                                    enabled = !isUpdatingYtDlp,
                                    shape = LaterboxShapeTokens.Button
                                ) {
                                    if (isUpdatingYtDlp) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = colors.accentPrimary
                                        )
                                    } else {
                                        Text(
                                            text = "Check Update",
                                            style = typography.uiMedium.copy(fontSize = 13.sp),
                                            color = colors.accentPrimary
                                        )
                                    }
                                }
                            }
                        }

                        // FFmpeg status sub-row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Media Stream Muxer (FFmpeg)",
                                style = typography.metaText,
                                color = colors.textSecondary
                            )
                            val ffmpegStatusText = when (ffmpegState) {
                                is FfmpegInitState.Initialized -> "Ready (Audio/Video muxing enabled)"
                                is FfmpegInitState.Initializing -> "Initializing…"
                                is FfmpegInitState.Failed -> "Unavailable (single streams only)"
                                is FfmpegInitState.NotInitialized -> "Pending"
                            }
                            Text(
                                text = ffmpegStatusText,
                                style = typography.metaText,
                                color = if (ffmpegState is FfmpegInitState.Failed) colors.statusDanger else colors.textSecondary
                            )
                        }

                        if (ytDlpState is YtDlpInitState.Failed) {
                            val failure = (ytDlpState as YtDlpInitState.Failed).error
                            LaterboxBanner(
                                text = failure.message,
                                isWarning = true,
                                actionText = "Retry",
                                onActionClick = onRetryInitializeEngine
                            )
                        }

                        SettingsDivider()

                        // Export Location (SAF DocumentTree)
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            Text(
                                text = "Export Storage Location",
                                style = typography.uiMedium,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Choose where downloaded media files are copied when exported. By default, files are exported to the system Downloads/Laterbox folder.",
                                style = typography.bodySecondary.copy(fontSize = 12.sp),
                                color = colors.textSecondary
                            )

                            Spacer(modifier = Modifier.height(spacing.xxs))

                            val currentExportFolder = preferences.customExportDirectoryName ?: "Default (System MediaStore)"
                            Text(
                                text = "Current: $currentExportFolder",
                                style = typography.metaText.copy(fontWeight = FontWeight.Medium),
                                color = if (preferences.customExportDirectoryUri != null) colors.accentPrimary else colors.textSecondary
                            )

                            Spacer(modifier = Modifier.height(spacing.xxs))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                            ) {
                                OutlinedButton(
                                    onClick = { directoryPickerLauncher.launch(null) },
                                    shape = LaterboxShapeTokens.Button
                                ) {
                                    Icon(
                                        painter = painterResource(LaterboxIcons.Folder),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = colors.accentPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (preferences.customExportDirectoryUri != null) "Change Folder" else "Choose Folder",
                                        style = typography.uiMedium.copy(fontSize = 13.sp),
                                        color = colors.accentPrimary
                                    )
                                }

                                if (preferences.customExportDirectoryUri != null) {
                                    TextButton(
                                        onClick = onClearCustomExportDirectory
                                    ) {
                                        Text(
                                            text = "Reset to Default",
                                            style = typography.uiMedium.copy(fontSize = 13.sp),
                                            color = colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }

        // Diagnostics Dialog
        if (showDiagnosticsDialog) {
            AlertDialog(
                onDismissRequest = { showDiagnosticsDialog = false },
                containerColor = colors.surfaceElevated,
                title = {
                    Text(
                        text = "Media Engine Diagnostics",
                        style = typography.titleSmall,
                        color = colors.textPrimary
                    )
                },
                text = {
                    val reportText = diagnosticReportText ?: diagnosticReport?.toFormattedString() ?: "No diagnostics captured."
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        Text(
                            text = reportText,
                            style = typography.codeText.copy(fontSize = 11.sp),
                            color = colors.textPrimary
                        )
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        TextButton(
                            onClick = {
                                val reportText = diagnosticReportText ?: diagnosticReport?.toFormattedString() ?: ""
                                clipboardManager.setText(AnnotatedString(reportText))
                                Toast.makeText(context, "Diagnostics copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text(
                                text = "Copy Report",
                                style = typography.uiMedium,
                                color = colors.accentPrimary
                            )
                        }
                        TextButton(onClick = { showDiagnosticsDialog = false }) {
                            Text(
                                text = "Close",
                                style = typography.uiMedium,
                                color = colors.accentPrimary
                            )
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            onRetryInitializeEngine()
                            diagnosticReport = onGetDiagnosticReport()
                        }
                    ) {
                        Text(
                            text = "Retry Init",
                            style = typography.uiMedium,
                            color = colors.textSecondary
                        )
                    }
                }
            )
        }
    }
}
