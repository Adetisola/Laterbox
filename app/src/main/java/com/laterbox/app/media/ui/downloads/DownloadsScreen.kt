package com.laterbox.app.media.ui.downloads

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.laterbox.app.media.domain.model.DownloadState
import com.laterbox.app.media.domain.model.MediaAsset
import com.laterbox.app.media.domain.model.MediaType
import com.laterbox.app.media.ui.viewer.DocumentOpener
import com.laterbox.app.media.ui.viewer.ImageViewer
import com.laterbox.app.media.ui.viewer.MediaPlayerScreen
import com.laterbox.app.ui.components.LaterboxFilterChip
import com.laterbox.app.ui.components.LaterboxIconButton
import com.laterbox.app.ui.components.LaterboxIcons
import com.laterbox.app.ui.components.LaterboxShapeTokens
import com.laterbox.app.ui.theme.LaterboxTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenLinkDetail: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing
    val keyboardController = LocalSoftwareKeyboardController.current
    val uiState by viewModel.uiState.collectAsState()

    var activeViewerAsset by remember { mutableStateOf<MediaAsset?>(null) }
    var isSearchVisible by remember { mutableStateOf(false) }

    val activeQueueCount = uiState.queueTasks.count {
        it.status in listOf(DownloadState.QUEUED, DownloadState.DOWNLOADING, DownloadState.PROCESSING, DownloadState.WAITING_FOR_WIFI)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundPrimary)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Row: standardized matching Inbox, Archive, Collections
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.base, vertical = spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Downloads",
                        style = typography.displayLarge,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Offline media & downloaded files",
                        style = typography.metaText,
                        color = colors.textSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    LaterboxIconButton(
                        iconRes = LaterboxIcons.Search,
                        contentDescription = "Search downloads",
                        selected = isSearchVisible,
                        onClick = { isSearchVisible = !isSearchVisible }
                    )
                    Spacer(modifier = Modifier.width(spacing.xs))
                    LaterboxIconButton(
                        iconRes = LaterboxIcons.Settings,
                        contentDescription = "Settings",
                        onClick = onSettingsClick
                    )
                }
            }

            // Standardized Search Bar (matching Inbox / Archive styling)
            AnimatedVisibility(
                visible = isSearchVisible,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.base, vertical = spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(LaterboxShapeTokens.InputField)
                            .background(colors.surfacePrimary)
                            .border(spacing.hairline, colors.borderSubtle, LaterboxShapeTokens.InputField)
                            .padding(horizontal = spacing.md),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = LaterboxIcons.Search),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.width(spacing.sm))

                            Box(modifier = Modifier.weight(1f)) {
                                if (uiState.searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search title, format, platform...",
                                        style = typography.bodySecondary,
                                        color = colors.textSecondary.copy(alpha = 0.7f)
                                    )
                                }
                                BasicTextField(
                                    value = uiState.searchQuery,
                                    onValueChange = viewModel::onSearchQueryChange,
                                    singleLine = true,
                                    textStyle = typography.bodyPrimary.copy(color = colors.textPrimary),
                                    cursorBrush = SolidColor(colors.accentPrimary),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (uiState.searchQuery.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(LaterboxShapeTokens.IconButton)
                                        .clickable(
                                            role = Role.Button,
                                            onClickLabel = "Clear search",
                                            onClick = { viewModel.onSearchQueryChange("") }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = LaterboxIcons.Close),
                                        contentDescription = "Clear search",
                                        modifier = Modifier.size(16.dp),
                                        tint = colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Standardised Tab Navigation using FilterChips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = spacing.base, vertical = spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                LaterboxFilterChip(
                    text = "Library (${uiState.assets.size})",
                    isSelected = uiState.selectedTab == DownloadsTab.LIBRARY,
                    onClick = { viewModel.onTabSelected(DownloadsTab.LIBRARY) }
                )
                LaterboxFilterChip(
                    text = "Queue",
                    isSelected = uiState.selectedTab == DownloadsTab.QUEUE,
                    onClick = { viewModel.onTabSelected(DownloadsTab.QUEUE) },
                    badgeText = if (activeQueueCount > 0) "$activeQueueCount" else null
                )
                LaterboxFilterChip(
                    text = "Storage",
                    isSelected = uiState.selectedTab == DownloadsTab.STORAGE,
                    onClick = { viewModel.onTabSelected(DownloadsTab.STORAGE) }
                )
            }

            Spacer(modifier = Modifier.height(spacing.xxs))

            // Tab Content
            when (uiState.selectedTab) {
                DownloadsTab.LIBRARY -> {
                    LibraryTab(
                        assets = uiState.assets,
                        selectedMediaType = uiState.selectedMediaType,
                        onMediaTypeSelected = viewModel::onMediaTypeSelected,
                        readyToDownloadItems = uiState.readyToDownloadItems,
                        onOpenLinkDetail = onOpenLinkDetail,
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
                        onRetryTask = viewModel::onRetryTask,
                        onDismissTask = viewModel::onDismissTask
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
