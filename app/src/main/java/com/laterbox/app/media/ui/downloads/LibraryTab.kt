package com.laterbox.app.media.ui.downloads

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.laterbox.app.media.domain.model.AssetFileStatus
import com.laterbox.app.media.domain.model.MediaAsset
import com.laterbox.app.media.domain.model.MediaType
import com.laterbox.app.ui.components.LaterboxIcons
import com.laterbox.app.ui.components.LaterboxShapeTokens
import com.laterbox.app.ui.theme.LaterboxTheme
import java.io.File

@Composable
fun LibraryTab(
    assets: List<MediaAsset>,
    selectedMediaType: MediaType?,
    onMediaTypeSelected: (MediaType?) -> Unit,
    onOpenAsset: (MediaAsset) -> Unit,
    onExportAsset: (MediaAsset) -> Unit,
    onDeleteAsset: (MediaAsset) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors

    Column(modifier = modifier.fillMaxSize()) {
        // Media type filter chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedMediaType == null,
                    onClick = { onMediaTypeSelected(null) },
                    label = { Text("All") },
                    shape = LaterboxShapeTokens.PillNav,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.accentPrimary.copy(alpha = 0.15f),
                        selectedLabelColor = colors.accentPrimary
                    )
                )
            }
            items(MediaType.entries.filter { it != MediaType.OTHER }) { type ->
                FilterChip(
                    selected = selectedMediaType == type,
                    onClick = { onMediaTypeSelected(type) },
                    label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    shape = LaterboxShapeTokens.PillNav,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.accentPrimary.copy(alpha = 0.15f),
                        selectedLabelColor = colors.accentPrimary
                    )
                )
            }
        }

        if (assets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(LaterboxIcons.Folder),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = colors.textSecondary.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No downloaded media found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(assets, key = { it.id }) { asset ->
                    LibraryAssetCard(
                        asset = asset,
                        onOpen = { onOpenAsset(asset) },
                        onExport = { onExportAsset(asset) },
                        onDelete = { onDeleteAsset(asset) }
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryAssetCard(
    asset: MediaAsset,
    onOpen: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LaterboxTheme.colors
    val file = File(context.filesDir, "media/${asset.localPath}")

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = LaterboxShapeTokens.Card,
        colors = CardDefaults.cardColors(containerColor = colors.surfacePrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail or Media Type Icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(LaterboxShapeTokens.Card)
                    .background(colors.surfaceSecondary),
                contentAlignment = Alignment.Center
            ) {
                if (asset.mediaType == MediaType.IMAGE && file.exists()) {
                    AsyncImage(
                        model = file,
                        contentDescription = asset.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
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
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = asset.title ?: file.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${asset.mediaType.name} · ${formatBytes(asset.fileSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                    if (asset.fileStatus == AssetFileStatus.MISSING) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Missing",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            IconButton(onClick = onExport) {
                Icon(
                    painter = painterResource(LaterboxIcons.Copy),
                    contentDescription = "Export to shared storage",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(LaterboxIcons.Delete),
                    contentDescription = "Delete",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
