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
    modifier: Modifier = Modifier
) {
    if (tasks.isEmpty()) {
        EmptyStateView(
            title = "No active downloads",
            subtitle = "When you start downloading media from saved links, download progress will show here.",
            modifier = modifier.fillMaxSize()
        )
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tasks, key = { it.id }) { task ->
                QueueTaskCard(
                    task = task,
                    onOverrideCellular = { onOverrideCellular(task.id) },
                    onPauseTask = { onPauseTask(task.id) },
                    onResumeTask = { onResumeTask(task.id) },
                    onCancelTask = { onCancelTask(task.id) },
                    onRetryTask = { onRetryTask(task.id) },
                    onDismissTask = { onDismissTask(task.id) }
                )
            }
        }
    }
}

@Composable
fun QueueTaskCard(
    task: DownloadTask,
    onOverrideCellular: () -> Unit,
    onPauseTask: () -> Unit,
    onResumeTask: () -> Unit,
    onCancelTask: () -> Unit,
    onRetryTask: () -> Unit,
    onDismissTask: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val isTerminal = task.status == DownloadState.FAILED || task.status == DownloadState.CANCELLED

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = LaterboxShapeTokens.Card,
        colors = CardDefaults.cardColors(
            containerColor = colors.surfacePrimary
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
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${task.mediaType.name} · ${task.qualityLabel ?: "Standard"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }

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

            Spacer(modifier = Modifier.height(12.dp))

            when (task.status) {
                DownloadState.DOWNLOADING, DownloadState.PROCESSING -> {
                    LinearProgressIndicator(
                        progress = { task.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = colors.accentPrimary,
                        trackColor = colors.surfaceSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (task.status == DownloadState.PROCESSING) "Processing..." else "${task.progress}% · ${formatBytes(task.downloadedBytes)}",
                            style = MaterialTheme.typography.labelSmall,
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
                        style = MaterialTheme.typography.bodySmall,
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
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.accentPrimary
                        )
                    }
                }

                DownloadState.PAUSED -> {
                    Text(
                        text = "Paused (${task.progress}%)",
                        style = MaterialTheme.typography.bodySmall,
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
                    Text(
                        text = "Failed: ${task.errorCode ?: "Unknown error"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
                }

                DownloadState.QUEUED, DownloadState.INSPECTING, DownloadState.READY -> {
                    Text(
                        text = "Queued...",
                        style = MaterialTheme.typography.bodySmall,
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
