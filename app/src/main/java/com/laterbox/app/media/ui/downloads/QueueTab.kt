package com.laterbox.app.media.ui.downloads

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laterbox.app.media.domain.model.DownloadState
import com.laterbox.app.media.domain.model.DownloadTask
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Surface
import com.laterbox.app.ui.components.LaterboxIconButton
import com.laterbox.app.ui.components.EmptyStateView
import com.laterbox.app.ui.components.LaterboxIcons
import com.laterbox.app.ui.components.LaterboxShapeTokens
import com.laterbox.app.ui.theme.LaterboxTheme

@Composable
fun QueueTab(
    tasks: List<DownloadTask>,
    onOverrideCellular: (String) -> Unit,
    onPauseTask: (String) -> Unit,
    onResumeTask: (String) -> Unit,
    onCancelTask: (String) -> Unit,
    onRetryTask: (String) -> Unit,
    onDismissTask: (String) -> Unit,
    modifier: Modifier = Modifier,
    selectedTaskIds: Set<String> = emptySet(),
    isSelectionMode: Boolean = false,
    onToggleTaskSelection: (String) -> Unit = {},
    onSelectAllTasks: () -> Unit = {},
    onClearTaskSelection: () -> Unit = {},
    onCancelSelectedTasks: () -> Unit = {},
    onRetrySelectedTasks: () -> Unit = {},
    onDismissSelectedTasks: () -> Unit = {}
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    BackHandler(enabled = isSelectionMode) {
        onClearTaskSelection()
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (isSelectionMode) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.surfacePrimary,
                tonalElevation = 2.dp,
                border = BorderStroke(spacing.hairline, colors.borderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.base, vertical = spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LaterboxIconButton(
                        iconRes = LaterboxIcons.Close,
                        contentDescription = "Cancel selection",
                        onClick = onClearTaskSelection
                    )
                    Spacer(modifier = Modifier.width(spacing.sm))
                    Text(
                        text = "${selectedTaskIds.size} selected",
                        style = typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onSelectAllTasks) {
                        Text("Select all", style = typography.buttonLabel.copy(fontSize = 13.sp), color = colors.accentPrimary)
                    }
                    LaterboxIconButton(
                        iconRes = LaterboxIcons.Close,
                        contentDescription = "Cancel selected tasks",
                        onClick = onCancelSelectedTasks
                    )
                    Spacer(modifier = Modifier.width(spacing.xs))
                    LaterboxIconButton(
                        iconRes = LaterboxIcons.Refresh,
                        contentDescription = "Retry selected tasks",
                        onClick = onRetrySelectedTasks
                    )
                    Spacer(modifier = Modifier.width(spacing.xs))
                    LaterboxIconButton(
                        iconRes = LaterboxIcons.Delete,
                        contentDescription = "Dismiss selected tasks",
                        onClick = onDismissSelectedTasks
                    )
                }
            }
        }

        if (tasks.isEmpty()) {
            EmptyStateView(
                title = "No active downloads",
                subtitle = "When you start downloading media from saved links, download progress will show here.",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    val isSelected = selectedTaskIds.contains(task.id)
                    QueueTaskCard(
                        task = task,
                        onOverrideCellular = { onOverrideCellular(task.id) },
                        onPauseTask = { onPauseTask(task.id) },
                        onResumeTask = { onResumeTask(task.id) },
                        onCancelTask = { onCancelTask(task.id) },
                        onRetryTask = { onRetryTask(task.id) },
                        onDismissTask = { onDismissTask(task.id) },
                        isSelected = isSelected,
                        isInSelectionMode = isSelectionMode,
                        onClick = {
                            if (isSelectionMode) {
                                onToggleTaskSelection(task.id)
                            }
                        },
                        onLongClick = { onToggleTaskSelection(task.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueueTaskCard(
    task: DownloadTask,
    onOverrideCellular: () -> Unit,
    onPauseTask: () -> Unit,
    onResumeTask: () -> Unit,
    onCancelTask: () -> Unit,
    onRetryTask: () -> Unit,
    onDismissTask: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isInSelectionMode: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing
    val isTerminal = task.status == DownloadState.FAILED || task.status == DownloadState.CANCELLED

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClick?.invoke() },
                onLongClick = { onLongClick?.invoke() }
            ),
        shape = LaterboxShapeTokens.Card,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) colors.accentContainer.copy(alpha = 0.45f) else colors.surfacePrimary
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else spacing.hairline,
            color = if (isSelected) colors.accentPrimary else colors.borderSubtle
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title ?: task.sourceUrl,
                        style = typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${task.mediaType.name} · ${task.qualityLabel ?: "Standard"}",
                        style = typography.bodySmall,
                        color = colors.textSecondary
                    )
                }

                if (!isInSelectionMode) {
                    IconButton(
                        onClick = {
                            if (isTerminal) onDismissTask() else onCancelTask()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(LaterboxIcons.Close),
                            contentDescription = if (isTerminal) "Dismiss" else "Cancel",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (task.status) {
                DownloadState.DOWNLOADING, DownloadState.PROCESSING -> {
                    val stage = task.stage
                    val isIndeterminate = stage?.isIndeterminate == true || (task.progress <= 0 && task.status != DownloadState.DOWNLOADING)
                    if (isIndeterminate) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = colors.accentPrimary,
                            trackColor = colors.surfaceSecondary
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { (task.progress.coerceIn(0, 100)) / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = colors.accentPrimary,
                            trackColor = colors.surfaceSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val stageLabel = stage?.label ?: if (task.status == DownloadState.PROCESSING) "Processing..." else "Downloading..."
                        val detail = if (!task.stageDetail.isNullOrBlank()) " · ${task.stageDetail}" else ""
                        val progressText = if (isIndeterminate) {
                            "$stageLabel$detail"
                        } else {
                            "$stageLabel · ${task.progress}% · ${formatBytes(task.downloadedBytes)}$detail"
                        }
                        Text(
                            text = progressText,
                            style = typography.caption,
                            color = colors.textSecondary
                        )
                        TextButton(onClick = onPauseTask) {
                            Text(text = "Pause", color = colors.accentPrimary, fontSize = 12.sp)
                        }
                    }
                }

                DownloadState.WAITING_FOR_WIFI -> {
                    Text(
                        text = "Waiting for Wi-Fi (over 20 MB threshold)",
                        style = typography.bodySmall,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onOverrideCellular,
                        modifier = Modifier.fillMaxWidth(),
                        shape = LaterboxShapeTokens.PillNav
                    ) {
                        Text(
                            text = "Download anyway (use cellular)",
                            style = typography.uiMedium,
                            color = colors.accentPrimary
                        )
                    }
                }

                DownloadState.PAUSED -> {
                    Text(
                        text = "Paused (${task.progress}%)",
                        style = typography.bodySmall,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onResumeTask,
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.accentPrimary)
                    ) {
                        Text(text = "Resume")
                    }
                }

                DownloadState.FAILED -> {
                    val errorMsg = task.errorMessage ?: task.errorCode?.userMessage ?: "Download error"
                    Text(
                        text = "Failed: $errorMsg",
                        style = typography.bodySmall,
                        color = colors.statusDanger
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onRetryTask,
                            shape = LaterboxShapeTokens.PillNav
                        ) {
                            Icon(
                                painter = painterResource(LaterboxIcons.Refresh),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = colors.accentPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Retry", color = colors.accentPrimary)
                        }
                        TextButton(onClick = onDismissTask) {
                            Text(text = "Dismiss", color = colors.textSecondary)
                        }
                    }
                }

                DownloadState.CANCELLED -> {
                    Text(
                        text = "Download cancelled",
                        style = typography.bodySmall,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onRetryTask,
                            shape = LaterboxShapeTokens.PillNav
                        ) {
                            Icon(
                                painter = painterResource(LaterboxIcons.Refresh),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = colors.accentPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Retry", color = colors.accentPrimary)
                        }
                        TextButton(onClick = onDismissTask) {
                            Text(text = "Dismiss", color = colors.textSecondary)
                        }
                    }
                }

                DownloadState.QUEUED, DownloadState.INSPECTING, DownloadState.READY -> {
                    Text(
                        text = task.stage?.label ?: "Queued...",
                        style = typography.bodySmall,
                        color = colors.textSecondary
                    )
                }

                else -> {}
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    if (digitGroups >= units.size) digitGroups = units.size - 1
    val formatted = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return "%.1f %s".format(formatted, units[digitGroups])
}
