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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import java.util.Locale

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
    var downloadedBytes by remember { mutableLongStateOf(0L) }
    var totalDownloadBytes by remember { mutableLongStateOf(updateInfo.apkSize) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf("") }
    var saveStatusMessage by remember { mutableStateOf<String?>(null) }

    // Check if an APK for this update is already cached on disk and verified
    LaunchedEffect(updateInfo.versionName) {
        val existing = ApkInstallerHelper.getExistingDownloadedApk(context, updateInfo.versionName)
        if (existing != null) {
            downloadedFile = existing
            currentStep = UpdateStep.READY_TO_INSTALL
        }
    }

    fun startDownloadAndInstall() {
        currentStep = UpdateStep.DOWNLOADING
        downloadPercent = 0
        saveStatusMessage = null

        coroutineScope.launch {
            val file = ApkInstallerHelper.downloadApk(
                context = context,
                downloadUrl = updateInfo.downloadUrl,
                versionName = updateInfo.versionName,
                onProgress = { progress ->
                    when (progress) {
                        is ApkInstallerHelper.DownloadProgress.Downloading -> {
                            downloadPercent = progress.percent
                            downloadedBytes = progress.bytesDownloaded
                            if (progress.totalBytes > 0) {
                                totalDownloadBytes = progress.totalBytes
                            }
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
                downloadedFile = file
                val verification = ApkInstallerHelper.verifyApkDetailed(context, file)
                when (verification) {
                    is ApkInstallerHelper.ApkVerificationResult.Valid -> {
                        if (!ApkInstallerHelper.canInstallPackages(context)) {
                            currentStep = UpdateStep.PERMISSION_NEEDED
                        } else {
                            currentStep = UpdateStep.READY_TO_INSTALL
                            ApkInstallerHelper.launchInstaller(context, file)
                        }
                    }
                    is ApkInstallerHelper.ApkVerificationResult.SignatureMismatch -> {
                        errorMessage = verification.message
                        currentStep = UpdateStep.FAILED
                    }
                    is ApkInstallerHelper.ApkVerificationResult.InvalidPackage -> {
                        errorMessage = "Package mismatch: expected ${verification.expected}, found ${verification.actual}"
                        currentStep = UpdateStep.FAILED
                    }
                    is ApkInstallerHelper.ApkVerificationResult.Error -> {
                        errorMessage = verification.message
                        currentStep = UpdateStep.FAILED
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
                            val progressLabel = if (totalDownloadBytes > 0) {
                                val downloadedMb = String.format(Locale.US, "%.1f", downloadedBytes / (1024f * 1024f))
                                val totalMb = String.format(Locale.US, "%.1f", totalDownloadBytes / (1024f * 1024f))
                                "Downloading update... $downloadedMb MB / $totalMb MB ($downloadPercent%)"
                            } else {
                                "Downloading update... ($downloadPercent%)"
                            }

                            Text(
                                text = progressLabel,
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
                                text = "Resumable download — keeping your progress safe if connection drops.",
                                style = typography.caption,
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
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(spacing.xs))
                        Text(
                            text = "Version ${updateInfo.versionName} is downloaded and verified.",
                            style = typography.bodySecondary,
                            color = colors.textSecondary
                        )

                        if (saveStatusMessage != null) {
                            Spacer(modifier = Modifier.height(spacing.xs))
                            Text(
                                text = saveStatusMessage ?: "",
                                style = typography.caption,
                                color = colors.accentPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(spacing.md))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(spacing.xs)
                        ) {
                            LaterboxPrimaryButton(
                                text = "Install Update",
                                onClick = {
                                    downloadedFile?.let { file ->
                                        ApkInstallerHelper.launchInstaller(context, file)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                            ) {
                                LaterboxSecondaryButton(
                                    text = "Save to Downloads",
                                    onClick = {
                                        downloadedFile?.let { file ->
                                            val uri = ApkInstallerHelper.saveApkToDownloads(context, file)
                                            saveStatusMessage = if (uri != null) {
                                                "Saved to Downloads: ${file.name}"
                                            } else {
                                                "Could not save to Downloads folder"
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                LaterboxSecondaryButton(
                                    text = "Share APK",
                                    onClick = {
                                        downloadedFile?.let { file ->
                                            ApkInstallerHelper.shareApk(context, file)
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Re-download",
                                    style = typography.caption,
                                    color = colors.textTertiary,
                                    modifier = Modifier
                                        .clickable {
                                            downloadedFile?.delete()
                                            downloadedFile = null
                                            startDownloadAndInstall()
                                        }
                                        .padding(vertical = spacing.xs)
                                )
                                LaterboxSecondaryButton(
                                    text = "Dismiss",
                                    onClick = onDismiss
                                )
                            }
                        }
                    }

                    UpdateStep.FAILED -> {
                        Text(
                            text = errorMessage,
                            style = typography.bodySecondary,
                            color = colors.statusDanger
                        )

                        if (errorMessage.contains("Signature mismatch", ignoreCase = true)) {
                            Spacer(modifier = Modifier.height(spacing.xs))
                            Text(
                                text = "Tip: You can save the APK to your Downloads folder now, uninstall your existing app, and then install the APK from your Downloads.",
                                style = typography.caption,
                                color = colors.textSecondary
                            )
                        }

                        if (saveStatusMessage != null) {
                            Spacer(modifier = Modifier.height(spacing.xs))
                            Text(
                                text = saveStatusMessage ?: "",
                                style = typography.caption,
                                color = colors.accentPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(spacing.md))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(spacing.xs)
                        ) {
                            if (downloadedFile != null) {
                                LaterboxPrimaryButton(
                                    text = "Save APK to Downloads",
                                    onClick = {
                                        downloadedFile?.let { file ->
                                            val uri = ApkInstallerHelper.saveApkToDownloads(context, file)
                                            saveStatusMessage = if (uri != null) {
                                                "Saved to Downloads: ${file.name}"
                                            } else {
                                                "Could not save to Downloads folder"
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                LaterboxPrimaryButton(
                                    text = "Retry Download",
                                    onClick = { startDownloadAndInstall() },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

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
                                            onDismiss()
                                        }
                                        .padding(vertical = spacing.xs)
                                )
                                LaterboxSecondaryButton(
                                    text = "Dismiss",
                                    onClick = onDismiss
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
