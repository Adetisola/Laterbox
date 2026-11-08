package com.laterbox.app.ui.sheets

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laterbox.app.domain.model.MetadataStatus
import com.laterbox.app.ui.components.ContentTypeBadge
import com.laterbox.app.ui.components.FaviconImage
import com.laterbox.app.ui.theme.LaterboxTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedItemDetailSheet(
    uiState: SavedItemDetailUiState,
    sheetState: SheetState,
    onTitleChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onCollectionChange: (Long?) -> Unit,
    onSaveEdits: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    onQuickOpen: (String) -> Unit,
    onRetryMetadata: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing
    val context = LocalContext.current
    val link = uiState.link ?: return

    var showDeleteConfirm by remember { mutableStateOf(false) }

    val hasPendingEdits = uiState.editedTitle != link.title ||
        uiState.editedNote != (link.personalNote ?: "") ||
        uiState.selectedCollectionId != link.collectionId

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = colors.surfaceElevated,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = spacing.sm)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(spacing.radiusFull))
                    .background(colors.borderSubtle)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.base)
                .padding(bottom = spacing.xl)
                .verticalScroll(rememberScrollState())
        ) {
            // Header: Domain, ContentType, Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    FaviconImage(
                        faviconUrl = link.faviconUrl,
                        domain = link.domain,
                        size = 20.dp
                    )
                    Spacer(modifier = Modifier.width(spacing.xs))
                    Text(
                        text = link.domain,
                        style = typography.uiMedium.copy(fontSize = 13.sp),
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.width(spacing.sm))
                    ContentTypeBadge(contentType = link.contentType)
                }

                // Close button (min 48dp touch target)
                Box(
                    modifier = Modifier
                        .size(spacing.minTouchTarget)
                        .clip(CircleShape)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Close details",
                            onClick = onDismissRequest
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕",
                        style = typography.metaText,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.sm))

            // Quick Open ↗ button
            Button(
                onClick = { onQuickOpen(link.originalUrl) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPrimary,
                    contentColor = colors.surfaceElevated
                ),
                shape = RoundedCornerShape(spacing.radiusSmall),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text(
                    text = "Open Link ↗",
                    style = typography.uiMedium
                )
            }

            Spacer(modifier = Modifier.height(spacing.md))

            // Editable Title Field
            OutlinedTextField(
                value = uiState.editedTitle,
                onValueChange = onTitleChange,
                label = { Text("Title") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accentPrimary,
                    unfocusedBorderColor = colors.borderSubtle,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Extracted description snippet
            if (!link.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(spacing.sm))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(spacing.radiusSmall))
                        .background(colors.surfaceSecondary)
                        .padding(spacing.md)
                ) {
                    Text(
                        text = link.description,
                        style = typography.bodySecondary,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.sm))

            // URL Row with Copy action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(spacing.radiusSmall))
                    .background(colors.surfaceSecondary)
                    .border(spacing.hairline, colors.borderSubtle, RoundedCornerShape(spacing.radiusSmall))
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = link.originalUrl,
                    style = typography.codeText.copy(fontSize = 11.sp),
                    color = colors.textSecondary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(spacing.sm))

                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = spacing.minTouchTarget, minHeight = spacing.minTouchTarget)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Copy URL to clipboard"
                        ) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("URL", link.originalUrl))
                            Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Copy",
                        style = typography.uiMedium.copy(fontSize = 12.sp),
                        color = colors.accentPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.md))

            // Shelf / Collection Selector
            Text(
                text = "Shelf / Collection",
                style = typography.metaText,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(spacing.xs))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                CollectionSelectionChip(
                    name = "None",
                    isSelected = uiState.selectedCollectionId == null,
                    onClick = { onCollectionChange(null) }
                )
                uiState.collections.forEach { collection ->
                    CollectionSelectionChip(
                        name = collection.name,
                        isSelected = uiState.selectedCollectionId == collection.id,
                        onClick = { onCollectionChange(collection.id) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.md))

            // Editable Personal Note with approaching-limit reminder [UX06]
            val maxNoteLength = 500
            val remainingChars = maxNoteLength - uiState.editedNote.length
            OutlinedTextField(
                value = uiState.editedNote,
                onValueChange = {
                    if (it.length <= maxNoteLength + 20) onNoteChange(it)
                },
                label = { Text("Personal Note") },
                placeholder = { Text("Add takeaways, quotes, or thoughts...") },
                minLines = 2,
                maxLines = 4,
                supportingText = if (remainingChars <= 50) {
                    {
                        Text(
                            text = if (remainingChars >= 0) "$remainingChars characters remaining" else "Exceeded by ${-remainingChars} characters",
                            color = if (remainingChars >= 0) colors.textTertiary else colors.statusDanger,
                            style = typography.metaText
                        )
                    }
                } else null,
                isError = remainingChars < 0,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accentPrimary,
                    unfocusedBorderColor = colors.borderSubtle,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Save Edits Button (if changed)
            if (hasPendingEdits) {
                Spacer(modifier = Modifier.height(spacing.sm))
                Button(
                    onClick = onSaveEdits,
                    enabled = !uiState.isSaving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.surfaceSecondary,
                        contentColor = colors.accentPrimary
                    ),
                    shape = RoundedCornerShape(spacing.radiusSmall),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = spacing.minTouchTarget)
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            color = colors.accentPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text("Save Changes", style = typography.uiMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.lg))

            // Quick Status Actions: Mark Read, Archive, Delete with 48dp min touch target
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Read toggle button (min 48dp touch target)
                Box(
                    modifier = Modifier
                        .height(spacing.minTouchTarget)
                        .clip(RoundedCornerShape(spacing.radiusSmall))
                        .clickable(
                            role = Role.Button,
                            onClickLabel = if (link.isRead) "Mark link as unread" else "Mark link as read",
                            onClick = onToggleRead
                        )
                        .padding(horizontal = spacing.sm),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (link.isRead) "Mark Unread" else "✓ Mark Read",
                        style = typography.uiMedium.copy(fontSize = 13.sp),
                        color = colors.textPrimary
                    )
                }

                // Archive toggle button (min 48dp touch target)
                Box(
                    modifier = Modifier
                        .height(spacing.minTouchTarget)
                        .clip(RoundedCornerShape(spacing.radiusSmall))
                        .clickable(
                            role = Role.Button,
                            onClickLabel = if (link.isArchived) "Restore link to inbox" else "Archive link",
                            onClick = onToggleArchive
                        )
                        .padding(horizontal = spacing.sm),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (link.isArchived) "↩ Restore" else "Archive",
                        style = typography.uiMedium.copy(fontSize = 13.sp),
                        color = colors.textPrimary
                    )
                }

                // Delete button (min 48dp touch target)
                Box(
                    modifier = Modifier
                        .height(spacing.minTouchTarget)
                        .clip(RoundedCornerShape(spacing.radiusSmall))
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Delete link",
                            onClick = { showDeleteConfirm = true }
                        )
                        .padding(horizontal = spacing.sm),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Delete",
                        style = typography.uiMedium.copy(fontSize = 13.sp),
                        color = colors.accentPrimary
                    )
                }
            }

            // Compact metadata status badge with inline retry [UX05, UX09]
            if (link.metadataStatus == MetadataStatus.FAILED || link.metadataStatus == MetadataStatus.PARTIAL || link.metadataStatus == MetadataStatus.FETCHING) {
                Spacer(modifier = Modifier.height(spacing.sm))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(spacing.radiusSmall))
                        .background(colors.surfaceSecondary)
                        .padding(horizontal = spacing.md, vertical = spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (link.metadataStatus) {
                            MetadataStatus.FETCHING -> "Fetching metadata..."
                            MetadataStatus.PARTIAL -> "Partial preview"
                            else -> "Preview fetch failed"
                        },
                        style = typography.metaText,
                        color = colors.textSecondary
                    )
                    if (link.metadataStatus != MetadataStatus.FETCHING) {
                        Box(
                            modifier = Modifier
                                .sizeIn(minWidth = spacing.minTouchTarget, minHeight = spacing.minTouchTarget)
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = "Retry fetching link preview metadata",
                                    onClick = onRetryMetadata
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "⟳ Retry",
                                style = typography.uiMedium.copy(fontSize = 12.sp),
                                color = colors.accentPrimary
                            )
                        }
                    } else {
                        CircularProgressIndicator(
                            color = colors.accentPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Delete confirmation dialog
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                containerColor = colors.surfaceElevated,
                title = {
                    Text(
                        text = "Delete Link?",
                        style = typography.titleSmall,
                        color = colors.textPrimary
                    )
                },
                text = {
                    Text(
                        text = "\"${link.title}\" will be permanently deleted.",
                        style = typography.bodySecondary,
                        color = colors.textSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }) {
                        Text(
                            text = "Delete",
                            style = typography.uiMedium,
                            color = colors.accentPrimary
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
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
private fun CollectionSelectionChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    val bg = if (isSelected) colors.accentContainer else colors.surfaceSecondary
    val borderColor = if (isSelected) colors.accentPrimary else colors.borderSubtle
    val textColor = if (isSelected) colors.accentPrimary else colors.textSecondary

    Box(
        modifier = Modifier
            .sizeIn(minWidth = spacing.minTouchTarget, minHeight = spacing.minTouchTarget)
            .clickable(
                role = Role.Button,
                onClickLabel = "Select $name shelf",
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(spacing.radiusSmall))
                .background(bg)
                .border(spacing.hairline, borderColor, RoundedCornerShape(spacing.radiusSmall))
                .padding(horizontal = spacing.md),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name,
                style = typography.metaText.copy(fontSize = 12.sp),
                color = textColor
            )
        }
    }
}

