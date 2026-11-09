package com.laterbox.app.ui.save

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.laterbox.app.domain.util.UrlNormalizer
import com.laterbox.app.reminders.ReminderPresets
import com.laterbox.app.ui.components.CustomReminderPickerDialog
import com.laterbox.app.ui.components.LaterboxBanner
import com.laterbox.app.ui.components.LaterboxIcons
import com.laterbox.app.ui.components.LaterboxPrimaryButton
import com.laterbox.app.ui.components.LaterboxSecondaryButton
import com.laterbox.app.ui.components.LaterboxShapeTokens
import com.laterbox.app.ui.components.LaterboxTextButton
import com.laterbox.app.ui.sheets.MetadataPreviewStatus
import com.laterbox.app.ui.sheets.SaveLinkUiState
import com.laterbox.app.ui.theme.LaterboxMotion
import com.laterbox.app.ui.theme.LaterboxTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SaveFormMode {
    FULL,
    COMPACT
}

@Composable
fun SaveLinkForm(
    uiState: SaveLinkUiState,
    mode: SaveFormMode = SaveFormMode.FULL,
    onUrlChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onPersonalNoteChange: (String) -> Unit,
    onToggleCollection: (Long) -> Unit,
    onCreateCollection: (String) -> Unit,
    onReminderAtChange: (Long?) -> Unit,
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    var showCreateCollectionDialog by remember { mutableStateOf(false) }
    var showCustomReminderPicker by remember { mutableStateOf(false) }
    var showMoreOptions by remember { mutableStateOf(mode == SaveFormMode.FULL) }

    val domain = remember(uiState.url) {
        if (uiState.url.isNotBlank()) UrlNormalizer.extractDomain(uiState.url) else ""
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = LaterboxMotion.tweenSpec(LaterboxMotion.durationMedium)
            )
    ) {
        // 1. Header & Domain
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Save to Laterbox",
                    style = typography.sheetTitle,
                    color = colors.textPrimary
                )
                Text(
                    text = "Keep this link to revisit calmly later",
                    style = typography.metaText,
                    color = colors.textSecondary
                )
            }
            if (domain.isNotBlank()) {
                Text(
                    text = domain,
                    style = typography.metadataMono,
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.md))

        // 2. Duplicate Notice Banner
        uiState.duplicateLink?.let { existing ->
            val notice = if (existing.isArchived) {
                "Already in your library (Archived). Saving will restore to your Inbox."
            } else {
                "Already saved in your library. Saving will update your notes."
            }
            LaterboxBanner(
                text = notice,
                modifier = Modifier.padding(bottom = spacing.sm)
            )
        }

        // 3. URL Input / Preview
        if (mode == SaveFormMode.FULL) {
            OutlinedTextField(
                value = uiState.url,
                onValueChange = onUrlChange,
                label = { Text("Web address (URL)", style = typography.bodySecondary) },
                placeholder = { Text("example.com/article", style = typography.bodySecondary) },
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
                shape = LaterboxShapeTokens.InputField,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accentPrimary,
                    unfocusedBorderColor = colors.borderSubtle,
                    errorBorderColor = colors.statusDanger,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            // Compact Mode: Compact URL Display with Preview Image or Loading State
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(LaterboxShapeTokens.InputField)
                    .background(colors.surfaceSecondary)
                    .border(spacing.hairline, colors.borderSubtle, LaterboxShapeTokens.InputField)
                    .padding(spacing.sm)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    if (uiState.metadataStatus == MetadataPreviewStatus.LOADING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = colors.accentPrimary,
                            strokeWidth = 2.dp
                        )
                    } else if (!uiState.previewImageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = uiState.previewImageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(spacing.thumbnailRadius))
                        )
                    }
                    Text(
                        text = uiState.url.ifBlank { "No link detected" },
                        style = typography.metadataMono,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.sm))

        // 4. Title Input
        OutlinedTextField(
            value = uiState.title,
            onValueChange = onTitleChange,
            label = { Text("Title (Optional)", style = typography.bodySecondary) },
            placeholder = { Text("Leave blank to auto-detect title", style = typography.bodySecondary) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = LaterboxShapeTokens.InputField,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accentPrimary,
                unfocusedBorderColor = colors.borderSubtle,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(spacing.sm))

        // 5. Personal Note Input
        val remainingChars = uiState.maxNoteLength - uiState.personalNote.length
        OutlinedTextField(
            value = uiState.personalNote,
            onValueChange = {
                if (it.length <= uiState.maxNoteLength + 10) onPersonalNoteChange(it)
            },
            label = { Text("Personal note (Optional)", style = typography.bodySecondary) },
            placeholder = { Text("Why are you saving this? Key takeaway...", style = typography.bodySecondary) },
            minLines = 2,
            maxLines = 3,
            supportingText = if (remainingChars <= 50) {
                {
                    Text(
                        text = if (remainingChars >= 0) "$remainingChars characters remaining" else "Exceeded by ${-remainingChars}",
                        color = if (remainingChars >= 0) colors.textTertiary else colors.statusDanger,
                        style = typography.metaText
                    )
                }
            } else null,
            isError = remainingChars < 0,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = LaterboxShapeTokens.InputField,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accentPrimary,
                unfocusedBorderColor = colors.borderSubtle,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // 6. Compact Mode Collapsible Toggle
        if (mode == SaveFormMode.COMPACT) {
            Spacer(modifier = Modifier.height(spacing.xs))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showMoreOptions = !showMoreOptions }
                    .padding(vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (showMoreOptions) "Fewer options" else "+ Add collection or reminder",
                    style = typography.uiMedium.copy(fontSize = 13.sp),
                    color = colors.accentPrimary
                )
                Icon(
                    painter = painterResource(
                        id = if (showMoreOptions) LaterboxIcons.ChevronDown else LaterboxIcons.ChevronRight
                    ),
                    contentDescription = null,
                    tint = colors.accentPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // 7. Collections & Reminders
        AnimatedVisibility(visible = showMoreOptions) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(spacing.sm))

                // Collections
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
                                .height(spacing.chipHeight)
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
                                    style = typography.metaText,
                                    color = colors.accentPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(spacing.sm))

                // Reminder section
                Text(
                    text = "Reminder",
                    style = typography.metaText,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(spacing.xs))

                val presets = remember { ReminderPresets.getPresets() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    presets.forEach { preset ->
                        val isSelected = uiState.reminderAt != null &&
                            Math.abs(uiState.reminderAt - preset.triggerAtMillis) < 60_000L
                        ReminderOptionChip(
                            label = preset.label,
                            isSelected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    onReminderAtChange(null)
                                } else {
                                    onReminderAtChange(preset.triggerAtMillis)
                                }
                            }
                        )
                    }

                    // Custom reminder chip
                    val isCustomSelected = uiState.reminderAt != null &&
                        presets.none { Math.abs(uiState.reminderAt - it.triggerAtMillis) < 60_000L }
                    ReminderOptionChip(
                        label = if (isCustomSelected && uiState.reminderAt != null) {
                            val format = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                            format.format(Date(uiState.reminderAt))
                        } else {
                            "+ Custom…"
                        },
                        isSelected = isCustomSelected,
                        onClick = { showCustomReminderPicker = true }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.md))

        // 8. Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LaterboxSecondaryButton(
                text = "Cancel",
                onClick = onCancelClick,
                modifier = Modifier.weight(1f),
                enabled = !uiState.isSaving
            )

            Spacer(modifier = Modifier.width(spacing.sm))

            val saveLabel = when {
                uiState.isSavedSuccess -> "✓ Saved"
                uiState.duplicateLink != null -> "Update Link"
                else -> "Save to Inbox"
            }

            LaterboxPrimaryButton(
                text = saveLabel,
                onClick = onSaveClick,
                modifier = Modifier.weight(1.5f),
                enabled = uiState.canSave,
                isLoading = uiState.isSaving
            )
        }
    }

    // Dialogs
    if (showCreateCollectionDialog) {
        CreateCollectionDialog(
            onDismissRequest = { showCreateCollectionDialog = false },
            onConfirm = { name ->
                onCreateCollection(name)
                showCreateCollectionDialog = false
            }
        )
    }

    if (showCustomReminderPicker) {
        CustomReminderPickerDialog(
            initialTriggerMillis = uiState.reminderAt,
            onDismissRequest = { showCustomReminderPicker = false },
            onConfirm = { triggerAtMillis ->
                onReminderAtChange(triggerAtMillis)
                showCustomReminderPicker = false
            }
        )
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

    val bgColor = if (isSelected) colors.accentContainer else colors.surfaceSecondary
    val borderColor = if (isSelected) colors.accentPrimary else colors.borderSubtle
    val textColor = if (isSelected) colors.accentPrimary else colors.textPrimary

    Box(
        modifier = Modifier
            .sizeIn(minWidth = spacing.minTouchTarget, minHeight = spacing.minTouchTarget)
            .clickable(
                role = Role.Checkbox,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .height(spacing.chipHeight)
                .clip(LaterboxShapeTokens.Chip)
                .background(bgColor)
                .border(spacing.hairline, borderColor, LaterboxShapeTokens.Chip)
                .padding(horizontal = spacing.md),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name,
                style = typography.metaText,
                color = textColor
            )
        }
    }
}

@Composable
private fun ReminderOptionChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    val bgColor = if (isSelected) colors.accentContainer else colors.surfaceSecondary
    val borderColor = if (isSelected) colors.accentPrimary else colors.borderSubtle
    val textColor = if (isSelected) colors.accentPrimary else colors.textPrimary

    Box(
        modifier = Modifier
            .sizeIn(minWidth = spacing.minTouchTarget, minHeight = spacing.minTouchTarget)
            .clickable(
                role = Role.RadioButton,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .height(spacing.chipHeight)
                .clip(LaterboxShapeTokens.Chip)
                .background(bgColor)
                .border(spacing.hairline, borderColor, LaterboxShapeTokens.Chip)
                .padding(horizontal = spacing.md),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = typography.metaText,
                color = textColor
            )
        }
    }
}

@Composable
private fun CreateCollectionDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing
    var name by remember { mutableStateOf("") }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(LaterboxShapeTokens.Dialog)
                .background(colors.surfaceElevated)
                .border(spacing.hairline, colors.borderSubtle, LaterboxShapeTokens.Dialog)
                .padding(spacing.base)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "New Collection",
                    style = typography.cardTitle,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(spacing.sm))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Collection name", style = typography.bodySecondary) },
                    singleLine = true,
                    shape = LaterboxShapeTokens.InputField,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accentPrimary,
                        unfocusedBorderColor = colors.borderSubtle,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(spacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    LaterboxTextButton(
                        text = "Cancel",
                        onClick = onDismissRequest,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.width(spacing.xs))
                    LaterboxPrimaryButton(
                        text = "Create",
                        onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                        enabled = name.isNotBlank()
                    )
                }
            }
        }
    }
}
