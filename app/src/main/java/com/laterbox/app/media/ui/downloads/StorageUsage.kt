package com.laterbox.app.media.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.laterbox.app.media.domain.model.MediaAsset
import com.laterbox.app.media.domain.model.MediaType
import com.laterbox.app.ui.components.LaterboxShapeTokens
import com.laterbox.app.ui.theme.LaterboxTheme

@Composable
fun StorageTab(
    totalStorageBytes: Long,
    storageBreakdown: Map<MediaType, Long>,
    assets: List<MediaAsset>,
    selectedAssetIds: Set<String>,
    isSelectionMode: Boolean,
    onToggleAssetSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelectedAssets: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing
    val sortedAssets = assets.sortedByDescending { it.fileSize }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Storage Overview Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = LaterboxShapeTokens.Card,
                    colors = CardDefaults.cardColors(containerColor = colors.surfacePrimary),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Storage Used",
                            style = typography.labelMedium,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatBytes(totalStorageBytes),
                            style = typography.editorialTitle.copy(fontWeight = FontWeight.Bold),
                            color = colors.textPrimary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Segmented bar
                        if (totalStorageBytes > 0) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(colors.surfaceSecondary)
                            ) {
                                storageBreakdown.forEach { (type, bytes) ->
                                    val weight = (bytes.toFloat() / totalStorageBytes.toFloat()).coerceAtLeast(0.01f)
                                    Box(
                                        modifier = Modifier
                                            .weight(weight)
                                            .fillMaxHeight()
                                            .background(colorForMediaType(type))
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Legend
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                storageBreakdown.forEach { (type, bytes) ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(colorForMediaType(type))
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${type.name.lowercase().replaceFirstChar { it.uppercase() }} (${formatBytes(bytes)})",
                                            style = typography.caption,
                                            color = colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Largest Files Header with multi-selection controls
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Files by Size (${sortedAssets.size})",
                        style = typography.cardTitle,
                        color = colors.textPrimary
                    )

                    if (isSelectionMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = onClearSelection) {
                                Text("Cancel", style = typography.uiMedium, color = colors.textSecondary)
                            }
                            Button(
                                onClick = onDeleteSelectedAssets,
                                colors = ButtonDefaults.buttonColors(containerColor = colors.statusDanger)
                            ) {
                                Text("Delete (${selectedAssetIds.size})", style = typography.buttonLabel)
                            }
                        }
                    }
                }
            }

            // List of files sorted by size
            items(sortedAssets, key = { it.id }) { asset ->
                val isSelected = selectedAssetIds.contains(asset.id)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = LaterboxShapeTokens.Card,
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) colors.surfaceSecondary else colors.surfacePrimary
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleAssetSelection(asset.id) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = colors.accentPrimary,
                                checkmarkColor = colors.surfacePrimary
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = asset.title ?: asset.localPath.substringAfterLast('/'),
                                style = typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${asset.mediaType.name} · ${asset.qualityLabel ?: "Standard"}",
                                style = typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }

                        Text(
                            text = formatBytes(asset.fileSize),
                            style = typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary
                        )
                    }
                }
            }
        }
    }
}

private fun colorForMediaType(type: MediaType): Color {
    return when (type) {
        MediaType.VIDEO -> Color(0xFFE53935)
        MediaType.IMAGE -> Color(0xFF1E88E5)
        MediaType.AUDIO -> Color(0xFF43A047)
        MediaType.DOCUMENT -> Color(0xFFFB8C00)
        MediaType.OTHER -> Color(0xFF8E24AA)
    }
}
