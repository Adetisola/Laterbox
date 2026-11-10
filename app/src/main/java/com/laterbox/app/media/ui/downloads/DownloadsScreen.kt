package com.laterbox.app.media.ui.downloads

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.laterbox.app.media.domain.model.DownloadState
import com.laterbox.app.media.domain.model.MediaAsset
import com.laterbox.app.media.domain.model.MediaType
import com.laterbox.app.media.ui.viewer.DocumentOpener
import com.laterbox.app.media.ui.viewer.ImageViewer
import com.laterbox.app.media.ui.viewer.MediaPlayerScreen
import com.laterbox.app.ui.components.LaterboxIcons
import com.laterbox.app.ui.components.LaterboxShapeTokens
import com.laterbox.app.ui.theme.LaterboxTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LaterboxTheme.colors
    val uiState by viewModel.uiState.collectAsState()

    var activeViewerAsset by remember { mutableStateOf<MediaAsset?>(null) }
    var isSearchVisible by remember { mutableStateOf(false) }

    val activeQueueCount = uiState.queueTasks.count {
        it.status in listOf(DownloadState.QUEUED, DownloadState.DOWNLOADING, DownloadState.PROCESSING, DownloadState.WAITING_FOR_WIFI)
    }

    Box(modifier = modifier.fillMaxSize().background(colors.backgroundPrimary)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Downloads",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = { isSearchVisible = !isSearchVisible }) {
                    Icon(
                        painter = painterResource(LaterboxIcons.Search),
                        contentDescription = "Search downloads",
                        tint = colors.textPrimary
                    )
                }

                IconButton(onClick = onSettingsClick) {
                    Icon(
                        painter = painterResource(LaterboxIcons.Settings),
                        contentDescription = "Settings",
                        tint = colors.textPrimary
                    )
                }
            }

            // Optional Search Bar
            if (isSearchVisible) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("Search title, format, or platform...") },
                    singleLine = true,
                    shape = LaterboxShapeTokens.Card,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surfacePrimary,
                        unfocusedContainerColor = colors.surfacePrimary,
                        focusedBorderColor = colors.accentPrimary,
                        unfocusedBorderColor = colors.borderSubtle
                    )
                )
            }

            // Tab Row
            TabRow(
                selectedTabIndex = uiState.selectedTab.ordinal,
                containerColor = colors.backgroundPrimary,
                contentColor = colors.textPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[uiState.selectedTab.ordinal]),
                        color = colors.accentPrimary
                    )
                }
            ) {
                Tab(
                    selected = uiState.selectedTab == DownloadsTab.LIBRARY,
                    onClick = { viewModel.onTabSelected(DownloadsTab.LIBRARY) },
                    text = { Text("Library (${uiState.assets.size})") }
                )
                Tab(
                    selected = uiState.selectedTab == DownloadsTab.QUEUE,
                    onClick = { viewModel.onTabSelected(DownloadsTab.QUEUE) },
                    text = {
                        if (activeQueueCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge(containerColor = colors.accentPrimary) {
                                        Text("$activeQueueCount")
                                    }
                                }
                            ) {
                                Text("Queue")
                            }
                        } else {
                            Text("Queue")
                        }
                    }
                )
                Tab(
                    selected = uiState.selectedTab == DownloadsTab.STORAGE,
                    onClick = { viewModel.onTabSelected(DownloadsTab.STORAGE) },
                    text = { Text("Storage") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tab Content
            when (uiState.selectedTab) {
                DownloadsTab.LIBRARY -> {
                    LibraryTab(
                        assets = uiState.assets,
                        selectedMediaType = uiState.selectedMediaType,
                        onMediaTypeSelected = viewModel::onMediaTypeSelected,
                        onOpenAsset = { asset ->
                            when (asset.mediaType) {
                                MediaType.IMAGE -> activeViewerAsset = asset
                                MediaType.VIDEO, MediaType.AUDIO -> activeViewerAsset = asset
                                MediaType.DOCUMENT, MediaType.OTHER -> DocumentOpener.openDocument(context, asset)
                            }
                        },
                        onExportAsset = { asset ->
                            viewModel.onExportAsset(asset.id) { success ->
                                Toast.makeText(
                                    context,
                                    if (success) "Exported successfully" else "Failed to export",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onDeleteAsset = { asset ->
                            viewModel.onDeleteAsset(asset.id)
                        }
                    )
                }

                DownloadsTab.QUEUE -> {
                    QueueTab(
                        tasks = uiState.queueTasks,
                        onOverrideCellular = viewModel::onOverrideCellular,
                        onPauseTask = viewModel::onPauseTask,
                        onResumeTask = viewModel::onResumeTask,
                        onCancelTask = viewModel::onCancelTask,
                        onRetryTask = viewModel::onRetryTask
                    )
                }

                DownloadsTab.STORAGE -> {
                    StorageTab(
                        totalStorageBytes = uiState.totalStorageBytes,
                        storageBreakdown = uiState.storageBreakdown,
                        assets = uiState.assets,
                        selectedAssetIds = uiState.selectedAssetIds,
                        isSelectionMode = uiState.isSelectionMode,
                        onToggleAssetSelection = viewModel::onToggleAssetSelection,
                        onClearSelection = viewModel::onClearSelection,
                        onDeleteSelectedAssets = viewModel::onDeleteSelectedAssets
                    )
                }
            }
        }

        // Active Viewer Overlay
        activeViewerAsset?.let { asset ->
            when (asset.mediaType) {
                MediaType.IMAGE -> {
                    ImageViewer(
                        asset = asset,
                        onDismiss = { activeViewerAsset = null },
                        onExport = {
                            viewModel.onExportAsset(asset.id) { success ->
                                Toast.makeText(
                                    context,
                                    if (success) "Exported to gallery" else "Export failed",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
                MediaType.VIDEO, MediaType.AUDIO -> {
                    MediaPlayerScreen(
                        asset = asset,
                        onDismiss = { activeViewerAsset = null },
                        onExport = {
                            viewModel.onExportAsset(asset.id) { success ->
                                Toast.makeText(
                                    context,
                                    if (success) "Exported to storage" else "Export failed",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
                else -> {
                    activeViewerAsset = null
                }
            }
        }
    }
}
