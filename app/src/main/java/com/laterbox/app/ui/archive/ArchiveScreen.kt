package com.laterbox.app.ui.archive

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laterbox.app.domain.model.SavedLink
import com.laterbox.app.ui.components.DateGroupHeader
import com.laterbox.app.ui.components.DateGrouper
import com.laterbox.app.ui.components.EmptyStateView
import com.laterbox.app.ui.components.InboxSkeletonList
import com.laterbox.app.ui.components.LaterboxConfirmDialog
import com.laterbox.app.ui.components.LaterboxIconButton
import com.laterbox.app.ui.components.LaterboxIcons
import com.laterbox.app.ui.components.LaterboxSearchField
import com.laterbox.app.ui.components.LaterboxShapeTokens
import com.laterbox.app.ui.components.SavedLinkCard
import com.laterbox.app.ui.theme.LaterboxTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArchiveScreen(
    uiState: ArchiveUiState,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onLinkClick: (SavedLink) -> Unit,
    onQuickOpenClick: (SavedLink) -> Unit,
    onRestoreLink: (Long) -> Unit,
    onOpenDeleteDialog: (SavedLink) -> Unit,
    onCloseDeleteDialog: () -> Unit,
    onPermanentDelete: (Long) -> Unit,
    onSettingsClick: () -> Unit = {},
    onMenuClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing
    val keyboardController = LocalSoftwareKeyboardController.current

    var collapsedGroups by rememberSaveable { mutableStateOf(setOf<String>()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundPrimary)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.base, vertical = spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (onMenuClick != null) {
                        LaterboxIconButton(
                            iconRes = LaterboxIcons.Menu,
                            contentDescription = "Open navigation menu",
                            onClick = onMenuClick
                        )
                        Spacer(modifier = Modifier.width(spacing.sm))
                    }
                    Column {
                        Text(
                            text = "Archive",
                            style = typography.displayLarge,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Finished reading & stored records",
                            style = typography.metaText,
                            color = colors.textSecondary
                        )
                    }
                }

                LaterboxIconButton(
                    iconRes = LaterboxIcons.Settings,
                    contentDescription = "Settings",
                    onClick = onSettingsClick
                )
            }

            // Search Bar
            LaterboxSearchField(
                query = uiState.searchQuery,
                onQueryChange = onSearchQueryChange,
                placeholder = "Search archived links...",
                onClear = onClearSearch,
                modifier = Modifier.padding(horizontal = spacing.base, vertical = spacing.xs)
            )

            Spacer(modifier = Modifier.height(spacing.xs))

            // Main Content Area
            when {
                uiState.isLoading -> {
                    InboxSkeletonList(
                        isHero = false,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                uiState.isEmpty -> {
                    EmptyStateView(
                        title = "Archive is empty",
                        subtitle = "When you finish an article or clean up your inbox, archive items here to keep them safe.",
                        modifier = Modifier.weight(1f)
                    )
                }

                uiState.isSearchEmpty -> {
                    EmptyStateView(
                        title = "No matching links",
                        subtitle = "Nothing archived matching \"${uiState.searchQuery}\"",
                        actionText = "Clear search",
                        onActionClick = onClearSearch,
                        modifier = Modifier.weight(1f)
                    )
                }

                else -> {
                    val groupedLinks = remember(uiState.links) {
                        DateGrouper.groupLinks(uiState.links)
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = spacing.base,
                            end = spacing.base,
                            top = 0.dp,
                            bottom = 32.dp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        groupedLinks.forEach { group ->
                            stickyHeader(key = "header_${group.title}", contentType = "date_header") {
                                val isCollapsed = collapsedGroups.contains(group.title)
                                DateGroupHeader(
                                    title = group.title,
                                    count = group.links.size,
                                    isCollapsed = isCollapsed,
                                    onToggleCollapse = {
                                        collapsedGroups = if (isCollapsed) {
                                            collapsedGroups - group.title
                                        } else {
                                            collapsedGroups + group.title
                                        }
                                    }
                                )
                            }

                            if (!collapsedGroups.contains(group.title)) {
                                itemsIndexed(
                                    items = group.links,
                                    key = { _, it -> it.id },
                                    contentType = { _, _ -> "archived_card" }
                                ) { index, link ->
                                    val cardShape = if (index == 0) {
                                        RoundedCornerShape(
                                            topStart = 0.dp,
                                            topEnd = 16.dp,
                                            bottomStart = 16.dp,
                                            bottomEnd = 16.dp
                                        )
                                    } else {
                                        LaterboxShapeTokens.Card
                                    }
                                    Column(modifier = Modifier.padding(bottom = spacing.sm)) {
                                        SavedLinkCard(
                                            link = link,
                                            onClick = { onLinkClick(link) },
                                            onQuickOpenClick = { onQuickOpenClick(link) },
                                            shape = cardShape,
                                            onArchive = { onRestoreLink(link.id) },
                                            onDelete = { onOpenDeleteDialog(link) }
                                        )

                                        // Action row underneath archived card: Restore & Delete
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = spacing.xs, vertical = spacing.xxs),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Restore button
                                            Box(
                                                modifier = Modifier
                                                    .height(34.dp)
                                                    .clip(LaterboxShapeTokens.Button)
                                                    .background(colors.surfaceSecondary.copy(alpha = 0.5f))
                                                    .clickable(
                                                        role = Role.Button,
                                                        onClickLabel = "Restore link to Inbox",
                                                        onClick = { onRestoreLink(link.id) }
                                                    )
                                                    .padding(horizontal = spacing.sm),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        painter = painterResource(id = LaterboxIcons.Inbox),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = colors.accentPrimary
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Restore to Inbox",
                                                        style = typography.uiMedium.copy(fontSize = 12.sp),
                                                        color = colors.accentPrimary
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(spacing.xs))

                                            // Delete permanently button
                                            Box(
                                                modifier = Modifier
                                                    .height(34.dp)
                                                    .clip(LaterboxShapeTokens.Button)
                                                    .background(colors.surfaceSecondary.copy(alpha = 0.5f))
                                                    .clickable(
                                                        role = Role.Button,
                                                        onClickLabel = "Delete permanently",
                                                        onClick = { onOpenDeleteDialog(link) }
                                                    )
                                                    .padding(horizontal = spacing.sm),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        painter = painterResource(id = LaterboxIcons.Delete),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = colors.textSecondary
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Delete",
                                                        style = typography.uiMedium.copy(fontSize = 12.sp),
                                                        color = colors.textSecondary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Delete Confirmation Dialog
        uiState.linkToDelete?.let { link ->
            LaterboxConfirmDialog(
                title = "Delete Link Permanently?",
                message = "This will permanently remove \"${link.title.ifBlank { link.domain }}\" and its metadata. This action cannot be undone.",
                confirmText = "Delete permanently",
                onConfirm = { onPermanentDelete(link.id) },
                onDismiss = onCloseDeleteDialog,
                isDestructive = true
            )
        }
    }
}
