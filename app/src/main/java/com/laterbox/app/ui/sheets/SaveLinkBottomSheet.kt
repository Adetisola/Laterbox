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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laterbox.app.ui.theme.LaterboxTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveLinkBottomSheet(
    uiState: SaveLinkUiState,
    sheetState: SheetState,
    onUrlChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onPersonalNoteChange: (String) -> Unit,
    onSelectCollection: (Long?) -> Unit,
    onSaveClick: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

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
                        .clip(RoundedCornerShape(spacing.radiusSmall))
                        .background(colors.accentContainer)
                        .border(spacing.hairline, colors.accentPrimary.copy(alpha = 0.4f), RoundedCornerShape(spacing.radiusSmall))
                        .padding(spacing.md)
                ) {
                    Text(
                        text = notice,
                        style = typography.bodySecondary.copy(fontSize = 13.sp),
                        color = colors.accentPrimary
                    )
                }
            }

            // URL input with inline field validation [UX06, UX18]
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

            // Collection selection chips
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
                CollectionChip(
                    name = "None",
                    isSelected = uiState.selectedCollectionId == null,
                    onClick = { onSelectCollection(null) }
                )
                uiState.collections.forEach { collection ->
                    CollectionChip(
                        name = collection.name,
                        isSelected = uiState.selectedCollectionId == collection.id,
                        onClick = { onSelectCollection(collection.id) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.sm))

            // Personal Note input with limit reminder when approaching limit [UX06]
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

            Spacer(modifier = Modifier.height(spacing.lg))

            // Actions with 48dp minimum touch targets [UX18, UX19]
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
                    shape = RoundedCornerShape(spacing.radiusSmall),
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
                role = androidx.compose.ui.semantics.Role.Button,
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

