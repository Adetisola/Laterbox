package com.laterbox.app.update

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.laterbox.app.ui.components.LaterboxIcons
import com.laterbox.app.ui.components.LaterboxPrimaryButton
import com.laterbox.app.ui.components.LaterboxSecondaryButton
import com.laterbox.app.ui.components.LaterboxShapeTokens
import com.laterbox.app.ui.theme.LaterboxTheme
import kotlinx.coroutines.launch
import java.io.File

private enum class UpdateStep {
    PROMPT,
    DOWNLOADING,
    VERIFYING,
    PERMISSION_NEEDED,
    READY_TO_INSTALL,
    FAILED
}

@Composable
fun AppUpdateDialog(
    updateInfo: AppUpdateInfo,
    onDismiss: () -> Unit,
    onPostpone: (version: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    var currentStep by remember { mutableStateOf(UpdateStep.PROMPT) }
    var downloadPercent by remember { mutableIntStateOf(0) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf("") }

    fun startDownloadAndInstall() {
        currentStep = UpdateStep.DOWNLOADING
        downloadPercent = 0

        coroutineScope.launch {
            val file = ApkInstallerHelper.downloadApk(
                context = context,
                downloadUrl = updateInfo.downloadUrl,
                versionName = updateInfo.versionName,
                onProgress = { progress ->
                    when (progress) {
                        is ApkInstallerHelper.DownloadProgress.Downloading -> {
                            downloadPercent = progress.percent
                        }
                        is ApkInstallerHelper.DownloadProgress.Completed -> {
                            downloadedFile = progress.file
                            currentStep = UpdateStep.VERIFYING
                        }
                        is ApkInstallerHelper.DownloadProgress.Error -> {
                            errorMessage = progress.message
                            currentStep = UpdateStep.FAILED
                        }
                    }
                }
            )

            if (file != null) {
                val isVerified = ApkInstallerHelper.verifyApk(context, file)
                if (!isVerified) {
                    errorMessage = "Package verification failed. Signature does not match."
                    currentStep = UpdateStep.FAILED
                    return@launch
                }

                if (!ApkInstallerHelper.canInstallPackages(context)) {
                    currentStep = UpdateStep.PERMISSION_NEEDED
                } else {
                    val launched = ApkInstallerHelper.launchInstaller(context, file)
                    if (launched) {
                        onDismiss()
                    } else {
                        currentStep = UpdateStep.READY_TO_INSTALL
                    }
                }
            }
        }
    }

    Dialog(onDismissRequest = {
        if (currentStep != UpdateStep.DOWNLOADING) {
            onDismiss()
        }
    }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(LaterboxShapeTokens.Card)
                .background(colors.surfaceElevated)
                .border(spacing.hairline, colors.borderSubtle, LaterboxShapeTokens.Card)
                .padding(spacing.lg)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header: Icon + Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(LaterboxShapeTokens.Chip)
                            .background(colors.surfacePrimary)
                            .border(spacing.hairline, colors.borderSubtle, LaterboxShapeTokens.Chip),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = LaterboxIcons.Sparkles),
                            contentDescription = null,
                            tint = colors.accentPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(spacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Update Available",
                            style = typography.sheetTitle,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Version ${updateInfo.versionName}",
                            style = typography.bodySecondary,
                            color = colors.accentPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(spacing.md))

                when (currentStep) {
                    UpdateStep.PROMPT -> {
                        // Release Notes
                        if (updateInfo.releaseNotes.isNotBlank()) {
                            Text(
                                text = "What's New:",
                                style = typography.fieldLabel,
                                color = colors.textSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(spacing.xs))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp)
                                    .clip(LaterboxShapeTokens.InputField)
                                    .background(colors.surfacePrimary)
                                    .border(spacing.hairline, colors.borderSubtle, LaterboxShapeTokens.InputField)
                                    .padding(spacing.sm)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = updateInfo.releaseNotes.trim(),
                                    style = typography.bodySecondary,
                                    color = colors.textSecondary,
                                    lineHeight = 18.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(spacing.md))
                        }

                        // Actions: Update Now, Later, GitHub
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(spacing.xs)
                        ) {
                            LaterboxPrimaryButton(
                                text = "Update Now",
                                onClick = { startDownloadAndInstall() },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "View on GitHub",
                                    style = typography.fieldLabel,
                                    color = colors.textTertiary,
                                    modifier = Modifier
                                        .clickable {
                                            ApkInstallerHelper.openFallbackReleasePage(context, updateInfo.releasePageUrl)
                                        }
                                        .padding(vertical = spacing.xs)
                                )
                                LaterboxSecondaryButton(
                                    text = "Later",
                                    onClick = {
                                        onPostpone(updateInfo.versionName)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }

                    UpdateStep.DOWNLOADING -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Downloading update... ($downloadPercent%)",
                                style = typography.bodyPrimary,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(spacing.sm))
                            LinearProgressIndicator(
                                progress = { downloadPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(LaterboxShapeTokens.Chip),
                                color = colors.accentPrimary,
                                trackColor = colors.surfacePrimary
                            )
                            Spacer(modifier = Modifier.height(spacing.md))
                            Text(
                                text = "Please keep Laterbox open while downloading.",
                                style = typography.bodySecondary,
                                color = colors.textTertiary
                            )
                        }
                    }

                    UpdateStep.VERIFYING -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = colors.accentPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(spacing.sm))
                            Text(
                                text = "Verifying package integrity...",
                                style = typography.bodyPrimary,
                                color = colors.textPrimary
                            )
                        }
                    }

                    UpdateStep.PERMISSION_NEEDED -> {
                        Text(
                            text = "To install the update, Laterbox needs permission to install packages from this source.",
                            style = typography.bodyPrimary,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(spacing.md))
                        LaterboxPrimaryButton(
                            text = "Grant Permission",
                            onClick = {
                                ApkInstallerHelper.openInstallPermissionSettings(context)
                                currentStep = UpdateStep.READY_TO_INSTALL
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    UpdateStep.READY_TO_INSTALL -> {
                        Text(
                            text = "Package ready for installation.",
                            style = typography.bodyPrimary,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(spacing.md))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                        ) {
                            LaterboxSecondaryButton(
                                text = "Cancel",
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f)
                            )
                            LaterboxPrimaryButton(
                                text = "Install",
                                onClick = {
                                    downloadedFile?.let { file ->
                                        val ok = ApkInstallerHelper.launchInstaller(context, file)
                                        if (ok) onDismiss()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    UpdateStep.FAILED -> {
                        Text(
                            text = "Update installation could not be completed directly: $errorMessage",
                            style = typography.bodySecondary,
                            color = colors.statusDanger
                        )
                        Spacer(modifier = Modifier.height(spacing.md))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                        ) {
                            LaterboxSecondaryButton(
                                text = "Dismiss",
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f)
                            )
                            LaterboxPrimaryButton(
                                text = "Open GitHub",
                                onClick = {
                                    ApkInstallerHelper.openFallbackReleasePage(context, updateInfo.releasePageUrl)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}
