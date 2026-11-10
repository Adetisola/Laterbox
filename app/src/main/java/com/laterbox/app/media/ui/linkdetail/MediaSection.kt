package com.laterbox.app.media.ui.linkdetail

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import com.laterbox.app.media.domain.model.DownloadState
import com.laterbox.app.media.domain.model.DownloadTask
import com.laterbox.app.media.domain.model.InspectionResult
import com.laterbox.app.media.domain.model.MediaAsset
import com.laterbox.app.media.domain.model.MediaCandidate
import com.laterbox.app.media.domain.model.MediaType
import com.laterbox.app.media.ui.downloads.formatBytes
import com.laterbox.app.ui.components.LaterboxIcons
import com.laterbox.app.ui.components.LaterboxShapeTokens
import com.laterbox.app.ui.theme.LaterboxTheme

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
            pendingCandidateToDownload = candidate
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
                        onCancel = { onCancelTask(task.id) }
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
        } else if (inspectionResult == null && assets.isEmpty() && activeTasks.isEmpty()) {
            OutlinedButton(
                onClick = onInspectClick,
                modifier = Modifier.fillMaxWidth(),
                shape = LaterboxShapeTokens.Card
            ) {
                Icon(
                    painter = painterResource(LaterboxIcons.Download),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.accentPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Inspect for downloadable media", color = colors.accentPrimary)
            }
        } else if (inspectionResult != null) {
            when (inspectionResult) {
                is InspectionResult.Available -> {
                    Text(
                        text = "Available Formats",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        inspectionResult.candidates.forEach { candidate ->
                            CandidateCard(
                                candidate = candidate,
                                onDownload = { handleStartDownload(candidate) }
                            )
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Inspection failed (${inspectionResult.code.name})",
                            style = MaterialTheme.typography.bodySmall,
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
                    painter = painterResource(LaterboxIcons.Copy),
                    contentDescription = "Export",
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
    onCancel: () -> Unit
) {
    val colors = LaterboxTheme.colors

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
                IconButton(onClick = onCancel, modifier = Modifier.size(24.dp)) {
                    Icon(
                        painter = painterResource(LaterboxIcons.Close),
                        contentDescription = "Cancel",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            when (task.status) {
                DownloadState.DOWNLOADING, DownloadState.PROCESSING -> {
                    LinearProgressIndicator(
                        progress = { task.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = colors.accentPrimary,
                        trackColor = colors.surfaceSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (task.status == DownloadState.PROCESSING) "Processing..." else "${task.progress}% · ${formatBytes(task.downloadedBytes)}",
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

                else -> {
                    Text(
                        text = task.status.name.lowercase().replaceFirstChar { it.uppercase() },
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
                Text(
                    text = candidate.formatLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                val sizeText = candidate.estimatedBytes?.let { formatBytes(it) } ?: "Size unknown"
                Text(
                    text = "${candidate.mediaType.name} · $sizeText",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary
                )
            }

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
        }
    }
}
