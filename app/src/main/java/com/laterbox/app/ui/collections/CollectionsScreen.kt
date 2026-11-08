package com.laterbox.app.ui.collections

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.sizeIn
import com.laterbox.app.domain.model.Collection
import com.laterbox.app.domain.model.CollectionWithCount
import com.laterbox.app.ui.components.CollectionsSkeletonGrid
import com.laterbox.app.ui.components.EmptyStateView
import com.laterbox.app.ui.theme.LaterboxTheme

@Composable
fun CollectionsScreen(
    uiState: CollectionsUiState,
    onCollectionClick: (Collection) -> Unit,
    onOpenCreateDialog: () -> Unit,
    onCloseCreateDialog: () -> Unit,
    onCreateCollection: (String) -> Unit,
    onOpenRenameDialog: (Collection) -> Unit,
    onCloseRenameDialog: () -> Unit,
    onRenameCollection: (Long, String) -> Unit,
    onOpenDeleteDialog: (Collection) -> Unit,
    onCloseDeleteDialog: () -> Unit,
    onDeleteCollection: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

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
                        text = "Collections",
                        style = typography.displayLarge,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Themed shelves for your library",
                        style = typography.metaText,
                        color = colors.textSecondary
                    )
                }

                // + New collection button
                Box(
                    modifier = Modifier
                        .sizeIn(minHeight = spacing.minTouchTarget, minWidth = spacing.minTouchTarget)
                        .clip(RoundedCornerShape(spacing.radiusSmall))
                        .background(colors.surfaceSecondary)
                        .border(spacing.hairline, colors.borderSubtle, RoundedCornerShape(spacing.radiusSmall))
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "New collection",
                            onClick = onOpenCreateDialog
                        )
                        .padding(horizontal = spacing.md, vertical = spacing.xs),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+ New folder",
                        style = typography.uiMedium.copy(fontSize = 13.sp),
                        color = colors.accentPrimary
                    )
                }
            }

            when {
                uiState.isLoading -> {
                    CollectionsSkeletonGrid(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }

                uiState.isEmpty -> {
                    EmptyStateView(
                        title = "No collections yet.",
                        subtitle = "Create shelves like \"Design Inspiration\", \"Weekend Reading\", or \"Tech Articles\" to organise your links.",
                        actionText = "+ Create a collection",
                        onActionClick = onOpenCreateDialog,
                        modifier = Modifier.weight(1f)
                    )
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 156.dp),
                        contentPadding = PaddingValues(
                            start = spacing.base,
                            end = spacing.base,
                            top = spacing.sm,
                            bottom = 80.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(
                            items = uiState.collections,
                            key = { it.collection.id }
                        ) { item ->
                            CollectionCard(
                                item = item,
                                onClick = { onCollectionClick(item.collection) },
                                onRename = { onOpenRenameDialog(item.collection) },
                                onDelete = { onOpenDeleteDialog(item.collection) }
                            )
                        }
                    }
                }
            }
        }

        // Create Collection Dialog
        if (uiState.showCreateDialog) {
            CollectionInputDialog(
                title = "New Collection",
                confirmText = "Create",
                initialValue = "",
                onDismiss = onCloseCreateDialog,
                onConfirm = onCreateCollection
            )
        }

        // Rename Collection Dialog
        uiState.collectionToRename?.let { collection ->
            CollectionInputDialog(
                title = "Rename Collection",
                confirmText = "Save",
                initialValue = collection.name,
                onDismiss = onCloseRenameDialog,
                onConfirm = { newName -> onRenameCollection(collection.id, newName) }
            )
        }

        // Delete Collection Dialog
        uiState.collectionToDelete?.let { collection ->
            AlertDialog(
                onDismissRequest = onCloseDeleteDialog,
                containerColor = colors.surfaceElevated,
                title = {
                    Text(
                        text = "Delete Collection?",
                        style = typography.titleSmall,
                        color = colors.textPrimary
                    )
                },
                text = {
                    Text(
                        text = "\"${collection.name}\" will be removed. Saved links in this collection will not be deleted; they will simply become uncategorised in your Inbox.",
                        style = typography.bodySecondary,
                        color = colors.textSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = { onDeleteCollection(collection.id) }) {
                        Text(
                            text = "Delete",
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

@Composable
private fun CollectionCard(
    item: CollectionWithCount,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(spacing.radiusMedium))
            .background(colors.surfacePrimary)
            .border(spacing.hairline, colors.borderSubtle, RoundedCornerShape(spacing.radiusMedium))
            .clickable(onClick = onClick)
            .padding(spacing.md)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "📁",
                    fontSize = 20.sp
                )

                Box {
                    Box(
                        modifier = Modifier
                            .size(spacing.minTouchTarget)
                            .clip(RoundedCornerShape(spacing.radiusSmall))
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "Collection options",
                                onClick = { showMenu = true }
                            )
                            .semantics {
                                contentDescription = "Options for ${item.collection.name}"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⋮",
                            style = typography.uiMedium.copy(fontSize = 18.sp),
                            color = colors.textSecondary
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(colors.surfaceElevated)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename", style = typography.uiMedium, color = colors.textPrimary) },
                            onClick = {
                                showMenu = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", style = typography.uiMedium, color = colors.accentPrimary) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Column {
                Text(
                    text = item.collection.name,
                    style = typography.titleSmall.copy(fontSize = 15.sp),
                    color = colors.textPrimary,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(spacing.xxs))
                val countText = if (item.itemCount == 0) "Empty" else "${item.itemCount} link${if (item.itemCount > 1) "s" else ""}"
                Text(
                    text = countText,
                    style = typography.metaText,
                    color = colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun CollectionInputDialog(
    title: String,
    confirmText: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    var name by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceElevated,
        title = {
            Text(
                text = title,
                style = typography.titleSmall,
                color = colors.textPrimary
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Collection name") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accentPrimary,
                    unfocusedBorderColor = colors.borderSubtle,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) onConfirm(name.trim())
                },
                enabled = name.isNotBlank()
            ) {
                Text(
                    text = confirmText,
                    style = typography.uiMedium,
                    color = if (name.isNotBlank()) colors.accentPrimary else colors.textSecondary.copy(alpha = 0.5f)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    style = typography.uiMedium,
                    color = colors.textSecondary
                )
            }
        }
    )
}
