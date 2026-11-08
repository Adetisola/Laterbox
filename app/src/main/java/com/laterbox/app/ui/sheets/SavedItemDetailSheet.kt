package com.laterbox.app.ui.sheets

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laterbox.app.domain.model.MetadataStatus
import com.laterbox.app.ui.components.ContentTypeBadge
import com.laterbox.app.ui.components.FaviconImage
import com.laterbox.app.ui.components.LaterboxIconButton
import com.laterbox.app.ui.components.LaterboxIcons
import com.laterbox.app.ui.components.LaterboxShapeTokens
import com.laterbox.app.ui.components.LinkQuickActionsMenu
import com.laterbox.app.ui.theme.LaterboxTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedItemDetailSheet(
    uiState: SavedItemDetailUiState,
    sheetState: SheetState,
    onTitleChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onToggleCollection: (Long) -> Unit,
    onCreateCollection: (String) -> Unit,
    onSetReminder: (Long?) -> Unit = {},
    onSaveEdits: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    onQuickOpen: (String) -> Unit,
    onRetryMetadata: () -> Unit,
    onDismissRequest: () -> Unit,
    onCollectionChange: (Long?) -> Unit = {}
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing
    val context = LocalContext.current
    val link = uiState.link ?: return

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    var showCreateCollectionDialog by remember { mutableStateOf(false) }

    val hasPendingEdits = uiState.editedTitle != link.title ||
        uiState.editedNote != (link.personalNote ?: "") ||
        uiState.selectedCollectionIds != link.collectionIds.toSet()

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
            // Header: Domain, ContentType on Left; More (⋮) and Close (×) on Right
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

                // Action buttons: More Menu (⋮) & Close (×)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    Box {
                        LaterboxIconButton(
                            iconRes = LaterboxIcons.MoreVertical,
                            contentDescription = "More actions",
                            onClick = { showMoreMenu = true }
                        )

                        LinkQuickActionsMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            link = link,
                            onToggleRead = onToggleRead,
                            onCopyLink = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("URL", link.originalUrl))
                                Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                            },
                            onShareLink = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, link.originalUrl)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share link"))
                            },
                            onArchive = onToggleArchive,
                            onDelete = { showDeleteConfirm = true }
                        )
                    }

                    LaterboxIconButton(
                        iconRes = LaterboxIcons.Close,
                        contentDescription = "Close details",
                        onClick = onDismissRequest
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
                shape = LaterboxShapeTokens.Button,
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

            // Extracted description snippet with 2-line truncation and inline Read more / Show less
            if (!link.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(spacing.sm))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(LaterboxShapeTokens.Card)
                        .background(colors.surfaceSecondary)
                        .padding(spacing.md)
                ) {
                    Column {
                        Text(
                            text = link.description,
                            style = typography.bodySecondary,
                            color = colors.textSecondary,
                            maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isDescriptionExpanded) "Show less" else "Read more",
                            style = typography.uiMedium.copy(fontSize = 12.sp),
                            color = colors.accentPrimary,
                            modifier = Modifier
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = if (isDescriptionExpanded) "Collapse description" else "Expand description"
                                ) {
                                    isDescriptionExpanded = !isDescriptionExpanded
                                }
                                .padding(vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.sm))

            // Original Link box with clear label, single-line text selection, right end-fade, and copy button
            Text(
                text = "Original link",
                style = typography.metaText,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(spacing.xs))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(LaterboxShapeTokens.Card)
                    .background(colors.surfaceSecondary)
                    .border(spacing.hairline, colors.borderSubtle, LaterboxShapeTokens.Card)
                    .padding(start = spacing.md, top = 4.dp, bottom = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = link.originalUrl,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        textStyle = typography.codeText.copy(fontSize = 12.sp, color = colors.textPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Right end-fade gradient overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(36.dp)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        colors.surfaceSecondary
                                    )
                                )
                            )
                    )
                }

                LaterboxIconButton(
                    iconRes = LaterboxIcons.Copy,
                    contentDescription = "Copy original link",
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("URL", link.originalUrl))
                        Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            Spacer(modifier = Modifier.height(spacing.md))

            // Collection Section with toggle chips and trailing + creator
            Text(
                text = "Collection",
                style = typography.metaText,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(spacing.xs))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                uiState.collections.forEach { collection ->
                    val isSelected = uiState.selectedCollectionIds.contains(collection.id)
                    CollectionSelectionChip(
                        name = collection.name,
                        isSelected = isSelected,
                        onClick = { onToggleCollection(collection.id) }
                    )
                }

                // Trailing "+" chip to create new collection inline
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = spacing.minTouchTarget, minHeight = spacing.minTouchTarget)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Create new collection",
                            onClick = { showCreateCollectionDialog = true }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(LaterboxShapeTokens.Chip)
                            .background(colors.surfaceSecondary)
                            .border(spacing.hairline, colors.borderSubtle, LaterboxShapeTokens.Chip)
                            .padding(horizontal = spacing.md),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = LaterboxIcons.Plus),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = colors.accentPrimary
                            )
                            Text(
                                text = "New",
                                style = typography.metaText.copy(fontSize = 12.sp),
                                color = colors.accentPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.md))

            // Editable Personal Note with approaching-limit reminder
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
                    shape = LaterboxShapeTokens.Button,
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

            Spacer(modifier = Modifier.height(spacing.md))

            // Reminder section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    painter = painterResource(id = LaterboxIcons.Clock),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = colors.textSecondary
                )
                Text(
                    text = "Reminder",
                    style = typography.metaText,
                    color = colors.textSecondary
                )
            }
            Spacer(modifier = Modifier.height(spacing.xs))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tonightMillis = remember { com.laterbox.app.reminders.ReminderPresets.getTonightMillis() }
                val tomorrowMillis = remember { com.laterbox.app.reminders.ReminderPresets.getTomorrowMorningMillis() }
                val weekendMillis = remember { com.laterbox.app.reminders.ReminderPresets.getWeekendMillis() }

                if (link.reminderAt != null) {
                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(LaterboxShapeTokens.Chip)
                            .background(colors.surfaceSecondary)
                            .border(1.dp, colors.accentPrimary, LaterboxShapeTokens.Chip)
                            .padding(start = spacing.sm, end = spacing.xs),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.xxs)
                        ) {
                            Text(
                                text = com.laterbox.app.reminders.ReminderPresets.formatReminderTime(link.reminderAt),
                                style = typography.metaText.copy(fontSize = 12.sp),
                                color = colors.accentPrimary
                            )
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(spacing.radiusSmall))
                                    .clickable(
                                        role = Role.Button,
                                        onClickLabel = "Clear reminder",
                                        onClick = { onSetReminder(null) }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = LaterboxIcons.Close),
                                    contentDescription = "Clear reminder",
                                    tint = colors.accentPrimary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                } else {
                    ReminderDetailChip(
                        label = "Tonight (8 PM)",
                        onClick = { onSetReminder(tonightMillis) }
                    )
                    ReminderDetailChip(
                        label = "Tomorrow (9 AM)",
                        onClick = { onSetReminder(tomorrowMillis) }
                    )
                    ReminderDetailChip(
                        label = "Weekend",
                        onClick = { onSetReminder(weekendMillis) }
                    )
                }
            }

            // Compact metadata status badge with inline retry
            if (link.metadataStatus == MetadataStatus.FAILED || link.metadataStatus == MetadataStatus.PARTIAL || link.metadataStatus == MetadataStatus.FETCHING) {
                Spacer(modifier = Modifier.height(spacing.sm))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(LaterboxShapeTokens.Card)
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

        // New Collection inline creation dialog
        if (showCreateCollectionDialog) {
            var newCollectionName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showCreateCollectionDialog = false },
                containerColor = colors.surfaceElevated,
                title = {
                    Text(
                        text = "New Collection",
                        style = typography.titleSmall,
                        color = colors.textPrimary
                    )
                },
                text = {
                    OutlinedTextField(
                        value = newCollectionName,
                        onValueChange = { newCollectionName = it.take(50) },
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
                            val name = newCollectionName.trim()
                            if (name.isNotBlank()) {
                                onCreateCollection(name)
                                showCreateCollectionDialog = false
                            }
                        },
                        enabled = newCollectionName.isNotBlank()
                    ) {
                        Text(
                            text = "Create",
                            style = typography.uiMedium,
                            color = colors.accentPrimary
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateCollectionDialog = false }) {
                        Text(
                            text = "Cancel",
                            style = typography.uiMedium,
                            color = colors.textSecondary
                        )
                    }
                }
            )
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
                onClickLabel = "Select $name collection",
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .height(34.dp)
                .clip(LaterboxShapeTokens.Chip)
                .background(bg)
                .border(spacing.hairline, borderColor, LaterboxShapeTokens.Chip)
                .padding(horizontal = spacing.md),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isSelected) {
                    Icon(
                        painter = painterResource(id = LaterboxIcons.Check),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = colors.accentPrimary
                    )
                }
                Text(
                    text = name,
                    style = typography.metaText.copy(fontSize = 12.sp),
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun ReminderDetailChip(
    label: String,
    onClick: () -> Unit
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    Box(
        modifier = Modifier
            .sizeIn(minWidth = spacing.minTouchTarget, minHeight = spacing.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .height(34.dp)
                .clip(LaterboxShapeTokens.Chip)
                .background(colors.surfaceSecondary)
                .border(spacing.hairline, colors.borderSubtle, LaterboxShapeTokens.Chip)
                .padding(horizontal = spacing.md),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = typography.metaText.copy(fontSize = 12.sp),
                color = colors.textPrimary
            )
        }
    }
}
