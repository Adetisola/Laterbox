package com.laterbox.app.media.ui.linkdetail

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laterbox.app.media.domain.model.CandidateStatus
import com.laterbox.app.media.domain.model.DownloadState
import com.laterbox.app.media.domain.model.DownloadTask
import com.laterbox.app.media.domain.model.FailureCode
import com.laterbox.app.media.domain.model.InspectionResult
import com.laterbox.app.media.domain.model.MatchConfidence
import com.laterbox.app.media.domain.model.MediaAsset
import com.laterbox.app.media.domain.model.MediaCandidate
import com.laterbox.app.media.domain.model.MediaType
import com.laterbox.app.media.domain.model.MusicMatchCandidate
import com.laterbox.app.media.ui.downloads.formatBytes
import com.laterbox.app.ui.components.LaterboxBadge
import com.laterbox.app.ui.components.LaterboxBadgeVariant
import com.laterbox.app.ui.components.LaterboxCard
import com.laterbox.app.ui.components.LaterboxIcons
import com.laterbox.app.ui.components.LaterboxPrimaryButton
import com.laterbox.app.ui.components.LaterboxSecondaryButton
import com.laterbox.app.ui.components.LaterboxShapeTokens
import com.laterbox.app.ui.theme.LaterboxTheme
import com.laterbox.app.ui.util.rememberLaterboxHaptics
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

@Composable
fun MediaSection(
    linkUrl: String,
    assets: List<MediaAsset>,
    activeTasks: List<DownloadTask>,
    inspectionResult: InspectionResult?,
    isInspecting: Boolean,
    onInspectClick: () -> Unit,
    onRetryClick: () -> Unit = onInspectClick,
    onCancelInspection: () -> Unit = {},
    onDownloadCandidate: (MediaCandidate) -> Unit,
    onOpenAsset: (MediaAsset) -> Unit,
    onExportAsset: (MediaAsset) -> Unit,
    onDeleteAsset: (MediaAsset) -> Unit,
    onOverrideCellular: (String) -> Unit,
    onCancelTask: (String) -> Unit,
    onDismissTask: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    var pendingCandidateToDownload by remember { mutableStateOf<MediaCandidate?>(null) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Continue download regardless of whether notification permission was granted or denied
        pendingCandidateToDownload?.let { onDownloadCandidate(it) }
        pendingCandidateToDownload = null
    }

    val handleStartDownload: (MediaCandidate) -> Unit = { candidate ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                onDownloadCandidate(candidate)
            } else {
                pendingCandidateToDownload = candidate
                try {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } catch (t: Throwable) {
                    onDownloadCandidate(candidate)
                }
            }
        } else {
            onDownloadCandidate(candidate)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "Media Library",
            style = typography.titleLarge,
            color = colors.textPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 1. Downloaded Assets for this link
        if (assets.isNotEmpty()) {
            Text(
                text = "Downloaded (${assets.size})",
                style = typography.labelMedium,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                assets.forEach { asset ->
                    DownloadedAssetRow(
                        asset = asset,
                        onOpen = { onOpenAsset(asset) },
                        onExport = { onExportAsset(asset) },
                        onDelete = { onDeleteAsset(asset) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 2. Active Download Tasks for this link
        if (activeTasks.isNotEmpty()) {
            Text(
                text = "Active Downloads",
                style = typography.labelMedium,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                activeTasks.forEach { task ->
                    ActiveTaskRow(
                        task = task,
                        onOverrideCellular = { onOverrideCellular(task.id) },
                        onCancel = { onCancelTask(task.id) },
                        onDismiss = { onDismissTask(task.id) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 3. Inspection State & Candidates
        val isYouTube = remember(linkUrl) {
            try {
                val host = java.net.URI(linkUrl).host?.lowercase() ?: ""
                host.contains("youtube.com") || host.contains("youtu.be")
            } catch (_: Exception) {
                false
            }
        }

        if (isInspecting) {
            val hasCandidates = inspectionResult is InspectionResult.Available && inspectionResult.candidates.isNotEmpty()
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = LaterboxShapeTokens.Card,
                color = colors.surfaceSecondary
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = colors.accentPrimary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (hasCandidates) "Discovering additional formats..." else if (inspectionResult != null) "Retrying inspection..." else "Checking media availability...",
                            style = typography.bodyPrimary.copy(fontWeight = FontWeight.Medium),
                            color = colors.textPrimary
                        )
                        if (isYouTube) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (hasCandidates) "Discovered formats ready below. Extracting complete formats..." else "YouTube extraction may take up to 90 seconds",
                                style = typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = onCancelInspection,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Cancel",
                            style = typography.labelMedium,
                            color = colors.accentPrimary
                        )
                    }
                }
            }

            if (inspectionResult is InspectionResult.Available && inspectionResult.candidates.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                AvailableFormatsContent(
                    inspectionResult = inspectionResult,
                    colors = colors,
                    typography = typography,
                    onDownloadCandidate = handleStartDownload,
                    onRetryClick = onRetryClick,
                    isInspecting = true
                )
            }
        } else if (inspectionResult == null) {
            LaterboxSecondaryButton(
                text = if (assets.isNotEmpty()) "Check for additional media" else "Inspect for downloadable media",
                leadingIcon = LaterboxIcons.Download,
                onClick = onInspectClick,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            when (inspectionResult) {
                is InspectionResult.Available -> {
                    AvailableFormatsContent(
                        inspectionResult = inspectionResult,
                        colors = colors,
                        typography = typography,
                        onDownloadCandidate = handleStartDownload,
                        onRetryClick = onRetryClick,
                        isInspecting = false
                    )
                }

                is InspectionResult.MetadataOnly -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = LaterboxShapeTokens.Card,
                        color = colors.surfaceSecondary
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Metadata-only link",
                                style = typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "This platform does not permit direct file downloads.",
                                style = typography.bodySmall,
                                color = colors.textSecondary
                            )
                            if (inspectionResult.title != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        val query = Uri.encode("${inspectionResult.title} ${inspectionResult.artist.orEmpty()}")
                                        val searchUri = Uri.parse("https://www.youtube.com/results?search_query=$query")
                                        context.startActivity(Intent(Intent.ACTION_VIEW, searchUri))
                                    },
                                    shape = LaterboxShapeTokens.PillNav
                                ) {
                                    Text("Search on YouTube", fontSize = 12.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            LaterboxSecondaryButton(
                                text = "Retry inspection",
                                leadingIcon = LaterboxIcons.Refresh,
                                onClick = onRetryClick,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                is InspectionResult.NeedsAuth -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = LaterboxShapeTokens.Card,
                        color = colors.surfaceSecondary
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Login Required",
                                style = typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = inspectionResult.reason,
                                style = typography.bodySmall,
                                color = colors.textSecondary
                            )

                            // Preview candidate if available
                            inspectionResult.previewCandidate?.let { previewCandidate ->
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Preview image only (no video/audio found):",
                                    style = typography.badgeLabel,
                                    color = colors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                CandidateCard(
                                    candidate = previewCandidate,
                                    onDownload = { handleStartDownload(previewCandidate) }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            LaterboxSecondaryButton(
                                text = "Retry inspection",
                                leadingIcon = LaterboxIcons.Refresh,
                                onClick = onRetryClick,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                is InspectionResult.Unsupported -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = LaterboxShapeTokens.Card,
                        color = colors.surfaceSecondary
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(LaterboxIcons.Download),
                                    contentDescription = null,
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "No downloadable media detected",
                                    style = typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                    color = colors.textPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "No media formats were discovered on this webpage.",
                                style = typography.bodySmall,
                                color = colors.textSecondary
                            )

                            // Preview candidate if available
                            inspectionResult.previewCandidate?.let { previewCandidate ->
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Preview image only (no video/audio found):",
                                    style = typography.badgeLabel,
                                    color = colors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                CandidateCard(
                                    candidate = previewCandidate,
                                    onDownload = { handleStartDownload(previewCandidate) }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            LaterboxSecondaryButton(
                                text = "Retry inspection",
                                leadingIcon = LaterboxIcons.Refresh,
                                onClick = onRetryClick,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                is InspectionResult.Failed -> {
                    val isWaitingConnection = inspectionResult.code in listOf(FailureCode.NETWORK_ERROR, FailureCode.CONNECTION_TIMEOUT)
                    val headerTitle = if (isWaitingConnection) "Waiting for connection" else "Inspection failed (${inspectionResult.code.name})"
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = LaterboxShapeTokens.Card,
                        color = colors.surfaceSecondary
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(LaterboxIcons.Refresh),
                                    contentDescription = null,
                                    tint = if (isWaitingConnection) colors.accentPrimary else colors.statusDanger,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = headerTitle,
                                    style = typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                    color = if (isWaitingConnection) colors.textPrimary else colors.statusDanger
                                )
                            }
                            val failureMessage = if (isWaitingConnection) {
                                "Connection unavailable or temporary DNS failure. Inspection will retry automatically when connection restores."
                            } else {
                                inspectionResult.message
                            }
                            if (!failureMessage.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = failureMessage,
                                    style = typography.bodySmall,
                                    color = colors.textSecondary,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Preview candidate if available
                            inspectionResult.previewCandidate?.let { previewCandidate ->
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Preview image only (no video/audio found):",
                                    style = typography.badgeLabel,
                                    color = colors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                CandidateCard(
                                    candidate = previewCandidate,
                                    onDownload = { handleStartDownload(previewCandidate) }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            LaterboxSecondaryButton(
                                text = "Retry inspection",
                                leadingIcon = LaterboxIcons.Refresh,
                                onClick = onRetryClick,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvailableFormatsContent(
    inspectionResult: InspectionResult.Available,
    colors: com.laterbox.app.ui.theme.LaterboxColors,
    typography: com.laterbox.app.ui.theme.LaterboxTypography,
    onDownloadCandidate: (MediaCandidate) -> Unit,
    onRetryClick: () -> Unit,
    isInspecting: Boolean
) {
    if (inspectionResult.musicMatches.isNotEmpty()) {
        MusicMatchCard(
            available = inspectionResult,
            onDownloadCandidate = onDownloadCandidate
        )
    } else {
        if (inspectionResult.partialFailures.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = LaterboxShapeTokens.Card,
                color = colors.statusDanger.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(LaterboxIcons.Refresh),
                        contentDescription = null,
                        tint = colors.statusDanger,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Some media could not be extracted (${inspectionResult.partialFailures.size} issue(s))",
                        style = typography.bodySmall,
                        color = colors.statusDanger
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Partial extraction notice when complete extraction has finished or timed out
        if (!inspectionResult.isComplete && !isInspecting) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = LaterboxShapeTokens.Card,
                color = colors.surfaceSecondary
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(LaterboxIcons.Refresh),
                        contentDescription = null,
                        tint = colors.accentPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Partial extraction",
                            style = typography.uiLabel.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Discovered formats are ready below. Complete extraction timed out or was interrupted.",
                            style = typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }

        Text(
            text = if (isInspecting) "Discovered Formats (extraction in progress)" else "Available Formats",
            style = typography.labelMedium,
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))

        // Bulk image download button when multiple images exist
        val imageCandidates = inspectionResult.candidates.filter { it.mediaType == MediaType.IMAGE }
        if (imageCandidates.size > 1) {
            OutlinedButton(
                onClick = { imageCandidates.forEach { onDownloadCandidate(it) } },
                modifier = Modifier.fillMaxWidth(),
                shape = LaterboxShapeTokens.PillNav
            ) {
                Icon(
                    painter = painterResource(LaterboxIcons.Download),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colors.accentPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Download all images (${imageCandidates.size})", color = colors.accentPrimary)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            inspectionResult.candidates.forEach { candidate ->
                CandidateCard(
                    candidate = candidate,
                    onDownload = { onDownloadCandidate(candidate) }
                )
            }
        }
    }

    if (!isInspecting) {
        Spacer(modifier = Modifier.height(12.dp))
        LaterboxSecondaryButton(
            text = if (!inspectionResult.isComplete) "Retry / Find More Formats" else "Retry inspection",
            leadingIcon = LaterboxIcons.Refresh,
            onClick = onRetryClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DownloadedAssetRow(
    asset: MediaAsset,
    onOpen: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = LaterboxShapeTokens.Card,
        color = colors.surfacePrimary,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(
                    when (asset.mediaType) {
                        MediaType.IMAGE -> LaterboxIcons.Globe
                        MediaType.VIDEO -> LaterboxIcons.ViewCard
                        MediaType.AUDIO -> LaterboxIcons.Bell
                        MediaType.DOCUMENT -> LaterboxIcons.Folder
                        else -> LaterboxIcons.Folder
                    }
                ),
                contentDescription = null,
                tint = colors.accentPrimary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = asset.title ?: asset.localPath.substringAfterLast('/'),
                    style = typography.bodyPrimary.copy(fontWeight = FontWeight.Medium),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${asset.mediaType.name} · ${formatBytes(asset.fileSize)}",
                    style = typography.caption,
                    color = colors.textSecondary
                )
            }

            IconButton(onClick = onExport, modifier = Modifier.size(32.dp)) {
                Icon(
                    painter = painterResource(LaterboxIcons.Download),
                    contentDescription = "Export to shared storage",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    painter = painterResource(LaterboxIcons.Delete),
                    contentDescription = "Delete",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ActiveTaskRow(
    task: DownloadTask,
    onOverrideCellular: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val isTerminal = task.status == DownloadState.CANCELLED || task.status == DownloadState.FAILED

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = LaterboxShapeTokens.Card,
        color = colors.surfacePrimary,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${task.mediaType.name} (${task.qualityLabel ?: "Standard"})",
                    style = typography.bodyPrimary.copy(fontWeight = FontWeight.Medium),
                    color = colors.textPrimary
                )
                IconButton(onClick = if (isTerminal) onDismiss else onCancel, modifier = Modifier.size(24.dp)) {
                    Icon(
                        painter = painterResource(LaterboxIcons.Close),
                        contentDescription = if (isTerminal) "Dismiss" else "Cancel",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            when (task.status) {
                DownloadState.DOWNLOADING, DownloadState.PROCESSING -> {
                    val stage = task.stage
                    val isIndeterminate = stage?.isIndeterminate == true || task.progress <= 0
                    if (isIndeterminate) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = colors.accentPrimary,
                            trackColor = colors.surfaceSecondary
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { task.progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = colors.accentPrimary,
                            trackColor = colors.surfaceSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    val progressLabel = if (stage != null) {
                        if (task.progress > 0 && !stage.isIndeterminate) {
                            "${stage.label} (${task.progress}% · ${formatBytes(task.downloadedBytes)})"
                        } else {
                            stage.label
                        }
                    } else if (task.status == DownloadState.PROCESSING) {
                        "Processing..."
                    } else {
                        "${task.progress}% · ${formatBytes(task.downloadedBytes)}"
                    }
                    Text(
                        text = progressLabel,
                        style = typography.caption,
                        color = colors.textSecondary
                    )
                }

                DownloadState.WAITING_FOR_WIFI -> {
                    Text(
                        text = "Waiting for Wi-Fi (>20 MB)",
                        style = typography.bodySmall,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Download anyway",
                        style = typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.accentPrimary,
                        modifier = Modifier.clickable(onClick = onOverrideCellular)
                    )
                }

                DownloadState.CANCELLED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Download cancelled",
                            style = typography.bodySmall,
                            color = colors.textSecondary
                        )
                        Text(
                            text = "Dismiss",
                            style = typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.accentPrimary,
                            modifier = Modifier.clickable(onClick = onDismiss)
                        )
                    }
                }

                DownloadState.FAILED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Failed: ${task.errorMessage ?: task.errorCode?.userMessage ?: "Download error"}",
                            style = typography.bodySmall,
                            color = colors.statusDanger,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Dismiss",
                            style = typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.accentPrimary,
                            modifier = Modifier.clickable(onClick = onDismiss)
                        )
                    }
                }

                else -> {
                    Text(
                        text = task.stage?.label ?: task.status.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun CandidateCard(
    candidate: MediaCandidate,
    onDownload: () -> Unit
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing
    val haptics = rememberLaterboxHaptics()
    val isDownloadable = candidate.status == CandidateStatus.AVAILABLE

    LaterboxCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = candidate.formatLabel,
                        style = typography.bodyPrimary.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (candidate.isThumbnail) {
                        Spacer(modifier = Modifier.width(spacing.xs))
                        LaterboxBadge(
                            text = "Thumbnail",
                            variant = LaterboxBadgeVariant.NEUTRAL
                        )
                    }
                }

                if (candidate.isOgFallback) {
                    Spacer(modifier = Modifier.height(spacing.xxs))
                    Text(
                        text = "Original media could not be extracted from this platform. Save preview image instead.",
                        style = typography.bodySmall,
                        color = colors.textSecondary
                    )
                }

                if (candidate.audioTrackMissing) {
                    Spacer(modifier = Modifier.height(spacing.xxs))
                    Text(
                        text = "Source video contains no audio track",
                        style = typography.caption,
                        color = colors.textSecondary
                    )
                }

                if (!isDownloadable && !candidate.statusReason.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(spacing.xxs))
                    Text(
                        text = candidate.statusReason,
                        style = typography.bodySmall,
                        color = colors.statusDanger
                    )
                }

                Spacer(modifier = Modifier.height(spacing.xxs))
                val sizeText = candidate.estimatedBytes?.let { formatBytes(it) } ?: "Size unknown"
                val dimText = if (candidate.width != null && candidate.height != null) " · ${candidate.width}×${candidate.height}" else ""
                val fpsText = if (candidate.fps != null && candidate.fps > 0) " · ${candidate.fps.toInt()}fps" else ""
                Text(
                    text = "${candidate.mediaType.name} · $sizeText$dimText$fpsText",
                    style = typography.caption,
                    color = colors.textSecondary
                )
            }

            Spacer(modifier = Modifier.width(spacing.sm))

            if (isDownloadable) {
                LaterboxPrimaryButton(
                    text = "Download",
                    leadingIcon = LaterboxIcons.Download,
                    onClick = {
                        haptics.confirm()
                        onDownload()
                    }
                )
            } else {
                LaterboxBadge(
                    text = candidate.status.name.lowercase().replaceFirstChar { it.uppercase() },
                    variant = LaterboxBadgeVariant.NEUTRAL
                )
            }
        }
    }
}

@Composable
private fun MusicMatchCard(
    available: InspectionResult.Available,
    onDownloadCandidate: (MediaCandidate) -> Unit
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val topMatch = available.musicMatches.firstOrNull() ?: return
    var showAlternatives by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = LaterboxShapeTokens.Card,
        color = colors.surfacePrimary,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Artwork thumbnail
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceSecondary),
                    contentAlignment = Alignment.Center
                ) {
                    if (!available.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = available.thumbnailUrl,
                            contentDescription = available.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            painter = painterResource(LaterboxIcons.Bell),
                            contentDescription = null,
                            tint = colors.accentPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = available.title ?: "Music Track",
                        style = typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            append(available.artist ?: "Unknown Artist")
                            if (!available.album.isNullOrBlank()) {
                                append(" • ${available.album}")
                            }
                        },
                        style = typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    MatchConfidenceBadge(confidence = topMatch.confidence, versionTag = topMatch.versionTag)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary One-Tap Download Button
            Button(
                onClick = { onDownloadCandidate(topMatch.downloadCandidate) },
                modifier = Modifier.fillMaxWidth(),
                shape = LaterboxShapeTokens.PillNav,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary)
            ) {
                Icon(
                    painter = painterResource(LaterboxIcons.Download),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (topMatch.confidence) {
                        MatchConfidence.STRONG -> "Download Audio"
                        MatchConfidence.POSSIBLE -> "Download Matched Audio"
                        MatchConfidence.VERSION_MISMATCH -> "Download Version"
                        else -> "Download Audio Candidate"
                    },
                    fontWeight = FontWeight.Medium
                )
            }

            // Expandable Alternative Candidates
            if (available.musicMatches.size > 1) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAlternatives = !showAlternatives }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showAlternatives) "Hide Alternative Matches" else "Review Alternative Matches (${available.musicMatches.size - 1})",
                        style = typography.labelMedium,
                        color = colors.accentPrimary
                    )
                }

                if (showAlternatives) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        available.musicMatches.drop(1).forEach { altMatch ->
                            AlternativeMatchRow(
                                match = altMatch,
                                onDownload = { onDownloadCandidate(altMatch.downloadCandidate) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchConfidenceBadge(
    confidence: MatchConfidence,
    versionTag: String? = null
) {
    val colors = LaterboxTheme.colors
    val (bgColor, textColor, label) = when (confidence) {
        MatchConfidence.STRONG -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "Strong Match")
        MatchConfidence.POSSIBLE -> Triple(Color(0xFFFFF8E1), Color(0xFFF57F17), "Possible Match")
        MatchConfidence.VERSION_MISMATCH -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), if (versionTag != null) "$versionTag Detected" else "Version Mismatch")
        MatchConfidence.WEAK -> Triple(colors.surfaceSecondary, colors.textSecondary, "Weak Match")
        MatchConfidence.UNAVAILABLE -> Triple(colors.surfaceSecondary, colors.textSecondary, "Unavailable")
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

@Composable
private fun AlternativeMatchRow(
    match: MusicMatchCandidate,
    onDownload: () -> Unit
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = colors.surfaceSecondary
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = match.title,
                    style = typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${match.artist} · ${match.sourceDescription}",
                    style = typography.caption,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onDownload,
                shape = LaterboxShapeTokens.PillNav,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Get", fontSize = 11.sp)
            }
        }
    }
}
