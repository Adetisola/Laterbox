package com.laterbox.app.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.laterbox.app.ui.save.SaveFormMode
import com.laterbox.app.ui.save.SaveLinkForm
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
    val spacing = LaterboxTheme.spacing

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = colors.surfaceElevated,
        contentColor = colors.textPrimary,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = spacing.sm)
                    .size(width = spacing.dragHandleWidth, height = spacing.dragHandleHeight)
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
            SaveLinkForm(
                uiState = uiState,
                mode = SaveFormMode.FULL,
                onUrlChange = onUrlChange,
                onTitleChange = onTitleChange,
                onPersonalNoteChange = onPersonalNoteChange,
                onToggleCollection = onToggleCollection,
                onCreateCollection = onCreateCollection,
                onReminderAtChange = onReminderAtChange,
                onSaveClick = onSaveClick,
                onCancelClick = onDismissRequest
            )
        }
    }
}
