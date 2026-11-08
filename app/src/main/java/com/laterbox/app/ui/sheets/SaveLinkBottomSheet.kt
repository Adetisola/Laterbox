package com.laterbox.app.ui.sheets

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laterbox.app.ui.components.LaterboxIcons
import com.laterbox.app.ui.components.LaterboxShapeTokens
import com.laterbox.app.ui.theme.LaterboxTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveLinkBottomSheet(
    uiState: SaveLinkUiState,
    sheetState: SheetState,
    onUrlChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onPersonalNoteChange: (String) -> Unit,
    onToggleCollection: (Long) -> Unit,
    onCreateCollection: (String) -> Unit,
    onReminderAtChange: (Long?) -> Unit = {},
    onSaveClick: () -> Unit,
    onDismissRequest: () -> Unit,
    onSelectCollection: (Long?) -> Unit = {}
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    var showCreateCollectionDialog by remember { mutableStateOf(false) }

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
            // Header
            Text(
                text = "Save to Laterbox",
                style = typography.displayLarge.copy(fontSize = 20.sp),
                color = colors.textPrimary
            )
            Text(
                text = "Keep this link to revisit calmly later",
                style = typography.metaText,
                color = colors.textSecondary
            )

            Spacer(modifier = Modifier.height(spacing.md))

            // Duplicate link banner
            uiState.duplicateLink?.let { existing ->
                val notice = if (existing.isArchived) {
                    "Already in your library (Archived). Saving will restore to your Inbox."
                } else {
                    "Already saved in your Inbox. Saving will update your notes."
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = spacing.sm)
                        .clip(LaterboxShapeTokens.Card)
                        .background(colors.accentContainer)
                        .border(spacing.hairline, colors.accentPrimary.copy(alpha = 0.4f), LaterboxShapeTokens.Card)
                        .padding(spacing.md)
                ) {
                    Text(
                        text = notice,
                        style = typography.bodySecondary.copy(fontSize = 13.sp),
                        color = colors.accentPrimary
                    )
                }
            }

            // URL input with inline field validation
            OutlinedTextField(
                value = uiState.url,
                onValueChange = onUrlChange,
                label = { Text("Web address (URL)") },
                placeholder = { Text("example.com/article") },
                singleLine = true,
                isError = uiState.urlError != null,
                supportingText = {
                    if (uiState.urlError != null) {
                        Text(
                            text = uiState.urlError,
                            color = colors.statusDanger,
                            style = typography.metaText
                        )
                    } else {
                        Text(
                            text = "e.g. nytimes.com/article",
                            color = colors.textTertiary,
                            style = typography.metaText
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    autoCorrectEnabled = false
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accentPrimary,
                    unfocusedBorderColor = colors.borderSubtle,
                    errorBorderColor = colors.statusDanger,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(spacing.xs))

            // Optional Title input
            OutlinedTextField(
                value = uiState.title,
                onValueChange = onTitleChange,
                label = { Text("Title (Optional)") },
                placeholder = { Text("Leave blank to auto-detect title") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accentPrimary,
                    unfocusedBorderColor = colors.borderSubtle,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(spacing.sm))

            // Collection selection chips (multi-toggle and inline create)
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
                    CollectionChip(
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

            Spacer(modifier = Modifier.height(spacing.sm))

            // Personal Note input with limit reminder when approaching limit
            val remainingChars = uiState.maxNoteLength - uiState.personalNote.length
            OutlinedTextField(
                value = uiState.personalNote,
                onValueChange = {
                    if (it.length <= uiState.maxNoteLength + 20) onPersonalNoteChange(it)
                },
                label = { Text("Personal note (Optional)") },
                placeholder = { Text("Why are you saving this? Key takeaway...") },
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

            Spacer(modifier = Modifier.height(spacing.sm))

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
                    text = "Remind me",
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

                if (uiState.reminderAt != null) {
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
                                text = com.laterbox.app.reminders.ReminderPresets.formatReminderTime(uiState.reminderAt),
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
                                        onClick = { onReminderAtChange(null) }
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
                    ReminderPresetChip(
                        label = "Tonight (8 PM)",
                        onClick = { onReminderAtChange(tonightMillis) }
                    )
                    ReminderPresetChip(
                        label = "Tomorrow (9 AM)",
                        onClick = { onReminderAtChange(tomorrowMillis) }
                    )
                    ReminderPresetChip(
                        label = "Weekend",
                        onClick = { onReminderAtChange(weekendMillis) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.lg))

            // Actions with 48dp minimum touch targets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.heightIn(min = spacing.minTouchTarget)
                ) {
                    Text(
                        text = "Cancel",
                        style = typography.uiMedium,
                        color = colors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.width(spacing.sm))

                Button(
                    onClick = onSaveClick,
                    enabled = uiState.canSave,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentPrimary,
                        contentColor = colors.surfaceElevated
                    ),
                    shape = LaterboxShapeTokens.Button,
                    modifier = Modifier.heightIn(min = spacing.minTouchTarget)
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            color = colors.surfaceElevated,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            text = if (uiState.duplicateLink != null) "Update Link" else "Save Link",
                            style = typography.uiMedium
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
    }
}

@Composable
private fun CollectionChip(
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
private fun ReminderPresetChip(
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
