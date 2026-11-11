package com.laterbox.app.media.ui.linkdetail

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
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
import com.laterbox.app.media.domain.model.InspectionResult
import com.laterbox.app.media.domain.model.MatchConfidence
import com.laterbox.app.media.domain.model.MediaAsset
import com.laterbox.app.media.domain.model.MediaCandidate
import com.laterbox.app.media.domain.model.MediaType
import com.laterbox.app.media.domain.model.MusicMatchCandidate
import com.laterbox.app.media.ui.downloads.formatBytes
import com.laterbox.app.ui.components.LaterboxIcons
import com.laterbox.app.ui.components.LaterboxSecondaryButton
import com.laterbox.app.ui.components.LaterboxShapeTokens
import com.laterbox.app.ui.theme.LaterboxTheme
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
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 1. Downloaded Assets for this link
        if (assets.isNotEmpty()) {
            Text(
                text = "Downloaded (${assets.size})",
                style = MaterialTheme.typography.labelMedium,
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
                style = MaterialTheme.typography.labelMedium,
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
        if (isInspecting) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = colors.accentPrimary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Checking media availability...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
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
                    if (inspectionResult.musicMatches.isNotEmpty()) {
                        MusicMatchCard(
                            available = inspectionResult,
                            onDownloadCandidate = handleStartDownload
                        )
                    } else {
                        if (inspectionResult.partialFailures.isNotEmpty()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = LaterboxShapeTokens.Card,
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(LaterboxIcons.Refresh),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Some media could not be extracted (${inspectionResult.partialFailures.size} issue(s))",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Available Formats",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textSecondary
                            )
                            Text(
                                text = "Re-inspect",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.accentPrimary,
                                modifier = Modifier.clickable(onClick = onInspectClick)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        // Bulk image download button when multiple images exist
                        val imageCandidates = inspectionResult.candidates.filter { it.mediaType == MediaType.IMAGE }
                        if (imageCandidates.size > 1) {
                            OutlinedButton(
                                onClick = { imageCandidates.forEach { handleStartDownload(it) } },
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
                                    onDownload = { handleStartDownload(candidate) }
                                )
                            }
                        }
                    }
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
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "This platform does not permit direct file downloads.",
                                style = MaterialTheme.typography.bodySmall,
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
                        }
                    }
                }

                is InspectionResult.NeedsAuth -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = LaterboxShapeTokens.Card,
                        color = colors.surfaceSecondary
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Login Required",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = inspectionResult.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                }

                is InspectionResult.Unsupported -> {
                    Text(
                        text = "No downloadable media detected for this link.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }

                is InspectionResult.Failed -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Inspection failed (${inspectionResult.code.name})",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.error
                            )
                            if (inspectionResult.retryable) {
                                Text(
                                    text = "Retry",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.accentPrimary,
                                    modifier = Modifier.clickable(onClick = onInspectClick)
                                )
                            }
                        }
                        val failureMessage = inspectionResult.message
                        if (!failureMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = failureMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
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
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${asset.mediaType.name} · ${formatBytes(asset.fileSize)}",
                    style = MaterialTheme.typography.labelSmall,
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
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
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
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                }

                DownloadState.WAITING_FOR_WIFI -> {
                    Text(
                        text = "Waiting for Wi-Fi (>20 MB)",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Download anyway",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
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
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary
                        )
                        Text(
                            text = "Dismiss",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
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
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Dismiss",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.accentPrimary,
                            modifier = Modifier.clickable(onClick = onDismiss)
                        )
                    }
                }

                else -> {
                    Text(
                        text = task.stage?.label ?: task.status.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
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
    val isDownloadable = candidate.status == CandidateStatus.AVAILABLE

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = LaterboxShapeTokens.Card,
        color = colors.surfacePrimary,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = candidate.formatLabel,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (candidate.isThumbnail) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = LaterboxShapeTokens.PillNav,
                            color = colors.surfaceSecondary
                        ) {
                            Text(
                                text = "Thumbnail",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (candidate.isOgFallback) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Original media could not be extracted from this platform. Save preview image instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }

                if (candidate.audioTrackMissing) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Source video contains no audio track",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                }

                if (!isDownloadable && !candidate.statusReason.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = candidate.statusReason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
                val sizeText = candidate.estimatedBytes?.let { formatBytes(it) } ?: "Size unknown"
                val dimText = if (candidate.width != null && candidate.height != null) " · ${candidate.width}×${candidate.height}" else ""
                val fpsText = if (candidate.fps != null && candidate.fps > 0) " · ${candidate.fps.toInt()}fps" else ""
                Text(
                    text = "${candidate.mediaType.name} · $sizeText$dimText$fpsText",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isDownloadable) {
                Button(
                    onClick = onDownload,
                    shape = LaterboxShapeTokens.PillNav,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary)
                ) {
                    Icon(
                        painter = painterResource(LaterboxIcons.Download),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download")
                }
            } else {
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    shape = LaterboxShapeTokens.PillNav
                ) {
                    Text(candidate.status.name.lowercase().replaceFirstChar { it.uppercase() })
                }
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
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
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
                        style = MaterialTheme.typography.bodySmall,
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
                        style = MaterialTheme.typography.labelMedium,
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
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${match.artist} · ${match.sourceDescription}",
                    style = MaterialTheme.typography.labelSmall,
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
