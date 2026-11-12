package com.laterbox.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.laterbox.app.ui.theme.LaterboxTheme

/**
 * Standardised search bar control with tactile borders, 12dp rounded corners,
 * leading search icon, and quick clear action.
 */
@Composable
fun LaterboxSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onClear: () -> Unit = { onQueryChange("") }
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing
    val keyboardController = LocalSoftwareKeyboardController.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(spacing.buttonHeight)
            .clip(LaterboxTheme.shapes.InputField)
            .background(colors.surfacePrimary)
            .border(spacing.hairline, colors.borderSubtle, LaterboxTheme.shapes.InputField)
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
                modifier = Modifier.size(spacing.iconSmall + 2.dp),
                tint = colors.textSecondary
            )
            Spacer(modifier = Modifier.width(spacing.sm))

            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = typography.bodySecondary,
                        color = colors.textSecondary.copy(alpha = 0.7f)
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = typography.bodyPrimary.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accentPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(LaterboxTheme.shapes.IconButton)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Clear search",
                            onClick = onClear
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = LaterboxIcons.Close),
                        contentDescription = "Clear search",
                        modifier = Modifier.size(spacing.iconSmall),
                        tint = colors.textSecondary
                    )
                }
            }
        }
    }
}
