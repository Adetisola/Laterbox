package com.laterbox.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laterbox.app.ui.theme.LaterboxTheme

/**
 * Calm, ChatGPT-style floating single-line composer pill.
 * Remains single-line at rest ("Message...") with NO microphone/voice button and NO background scrim.
 * Expands smoothly up to ~5 lines only when the text wraps onto multiple lines or contains newlines,
 * and scrolls internally beyond 5 lines.
 */
@Composable
fun FloatingChatComposer(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Message...",
    isStreaming: Boolean = false,
    onStopStreaming: (() -> Unit)? = null
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    var visualLineCount by remember { mutableIntStateOf(1) }
    val isMultiline = visualLineCount > 1 || text.contains('\n')
    val canSend = text.isNotBlank() && !isStreaming

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("floating_chat_composer")
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        shape = RoundedCornerShape(if (isMultiline) 22.dp else 28.dp),
        color = colors.surfaceElevated,
        shadowElevation = 6.dp,
        border = BorderStroke(spacing.hairline, colors.borderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 48.dp)
                .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = if (isMultiline) Alignment.Bottom else Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = typography.bodyPrimary.copy(fontSize = 15.sp),
                        color = colors.textTertiary,
                        maxLines = 1
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    singleLine = false,
                    maxLines = 5,
                    textStyle = typography.bodyPrimary.copy(
                        fontSize = 15.sp,
                        color = colors.textPrimary
                    ),
                    cursorBrush = SolidColor(colors.accentPrimary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (canSend) onSend()
                        }
                    ),
                    onTextLayout = { layoutResult ->
                        visualLineCount = layoutResult.lineCount.coerceAtLeast(1)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("floating_chat_input")
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isStreaming && onStopStreaming != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.accentPrimary)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Stop generating",
                            onClick = onStopStreaming
                        )
                        .semantics { contentDescription = "Stop generating" }
                        .testTag("composer_stop_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = LaterboxIcons.Stop),
                        contentDescription = "Stop generating",
                        modifier = Modifier.size(16.dp),
                        tint = colors.onAccentPrimary
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) colors.accentPrimary else colors.surfaceSecondary
                        )
                        .clickable(
                            enabled = canSend,
                            role = Role.Button,
                            onClickLabel = "Send message",
                            onClick = onSend
                        )
                        .semantics { contentDescription = "Send message" }
                        .testTag("composer_send_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = LaterboxIcons.Send),
                        contentDescription = "Send message",
                        modifier = Modifier.size(18.dp),
                        tint = if (canSend) colors.onAccentPrimary else colors.textTertiary
                    )
                }
            }
        }
    }
}
