package com.laterbox.app.ui.archive

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.laterbox.app.domain.model.SavedLink
import com.laterbox.app.ui.components.EmptyStateView
import com.laterbox.app.ui.components.InboxSkeletonList
import com.laterbox.app.ui.components.LaterboxIconButton
import com.laterbox.app.ui.components.LaterboxIcons
import com.laterbox.app.ui.components.LaterboxShapeTokens
import com.laterbox.app.ui.components.SavedLinkCard
import com.laterbox.app.ui.theme.LaterboxTheme

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
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing
    val keyboardController = LocalSoftwareKeyboardController.current

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

            // Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.base, vertical = spacing.xs)
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
                                text = "Search archived links...",
                                style = typography.bodySecondary,
                                color = colors.textSecondary.copy(alpha = 0.7f)
                            )
                        }
                        BasicTextField(
                            value = uiState.searchQuery,
                            onValueChange = onSearchQueryChange,
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
                                    onClick = onClearSearch
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

            when {
                uiState.isLoading -> {
                    InboxSkeletonList(
                        isHero = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }

                uiState.isEmpty -> {
                    EmptyStateView(
                        title = "No archived links",
                        subtitle = "When you finish an article or video, archive it to keep your Inbox calm and focused.",
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
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = spacing.base,
                            end = spacing.base,
                            top = spacing.sm,
                            bottom = 148.dp // Leave clear space for floating navigation pill
                        ),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(
                            items = uiState.links,
                            key = { it.id },
                            contentType = { "archived_card" }
                        ) { link ->
                            Column {
                                SavedLinkCard(
                                    link = link,
                                    onClick = { onLinkClick(link) },
                                    onQuickOpenClick = { onQuickOpenClick(link) }
                                )

                                // Action row underneath archived card: Restore & Delete
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = spacing.xs, vertical = spacing.xxs),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Restore button (min 48dp touch target)
                                    Box(
                                        modifier = Modifier
                                            .height(spacing.minTouchTarget)
                                            .clip(RoundedCornerShape(spacing.radiusSmall))
                                            .clickable(
                                                role = Role.Button,
                                                onClickLabel = "Restore link to Inbox",
                                                onClick = { onRestoreLink(link.id) }
                                            )
                                            .padding(horizontal = spacing.sm),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "↩ Restore to Inbox",
                                            style = typography.uiMedium.copy(fontSize = 12.sp),
                                            color = colors.accentPrimary
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(spacing.xs))

                                    // Delete button (min 48dp touch target)
                                    Box(
                                        modifier = Modifier
                                            .height(spacing.minTouchTarget)
                                            .clip(RoundedCornerShape(spacing.radiusSmall))
                                            .clickable(
                                                role = Role.Button,
                                                onClickLabel = "Delete permanently",
                                                onClick = { onOpenDeleteDialog(link) }
                                            )
                                            .padding(horizontal = spacing.sm),
                                        contentAlignment = Alignment.Center
                                    ) {
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

        // Delete Confirmation Dialog
        uiState.linkToDelete?.let { link ->
            AlertDialog(
                onDismissRequest = onCloseDeleteDialog,
                containerColor = colors.surfaceElevated,
                title = {
                    Text(
                        text = "Delete Link Permanently?",
                        style = typography.titleSmall,
                        color = colors.textPrimary
                    )
                },
                text = {
                    Text(
                        text = "\"${link.title}\" will be permanently deleted from your device. This cannot be undone.",
                        style = typography.bodySecondary,
                        color = colors.textSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = { onPermanentDelete(link.id) }) {
                        Text(
                            text = "Delete Permanently",
                            style = typography.uiMedium,
                            color = colors.accentPrimary
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = onCloseDeleteDialog) {
                        Text(
                            text = "Cancel",
                            style = typography.uiMedium,
                            color = colors.textSecondary
                        )
                    }
                }
            )
        }
    }
}
