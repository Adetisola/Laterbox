package com.laterbox.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laterbox.app.ai.ChatAction
import com.laterbox.app.ai.speech.ComposerSpeechUiState
import com.laterbox.app.ai.speech.SpeechStatus
import com.laterbox.app.ui.theme.LaterboxTheme

/**
 * Floating composer: [+ add link] [text input] [mic] [send/stop].
 *
 * Stays a compact single row until the text *actually* wraps (measured against the single-row input
 * width, so there is no expand/collapse flicker) or contains a newline; then the input takes the
 * upper row and the controls move to a lower row. Cursor and selection survive the switch.
 * Also hosts dictation status, the inline action-approval card and the audio consent prompt so they
 * visually extend from the composer.
 */
@Composable
fun FloatingChatComposer(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Message...",
    isStreaming: Boolean = false,
    onStopStreaming: (() -> Unit)? = null,
    onAddLinkClick: (() -> Unit)? = null,
    onMicClick: (() -> Unit)? = null,
    speechState: ComposerSpeechUiState = ComposerSpeechUiState(),
    onStopDictation: () -> Unit = {},
    onCancelDictation: () -> Unit = {},
    pendingApproval: ChatAction? = null,
    onDenyAction: () -> Unit = {},
    onAllowActionOnce: () -> Unit = {},
    onAlwaysAllowAction: () -> Unit = {},
    showAudioConsent: Boolean = false,
    onAcceptAudioConsent: () -> Unit = {},
    onDismissAudioConsent: () -> Unit = {}
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    var tfv by remember { mutableStateOf(TextFieldValue(text)) }
    // External changes (send clear, dictation append) reset the cursor to the end; typing keeps selection.
    val value = if (tfv.text == text) tfv else TextFieldValue(text, TextRange(text.length))

    var surfaceWidthPx by remember { mutableIntStateOf(0) }
    val textStyle = typography.bodyPrimary.copy(fontSize = 15.sp, color = colors.textPrimary)

    val controlSize = 36.dp
    val showPlus = onAddLinkClick != null
    val singleRowInputWidthPx = with(density) {
        val controls = controlSize * (if (showPlus) 3 else 2) + 4.dp * (if (showPlus) 2 else 1) + 12.dp /*row padding*/ + 12.dp /*input padding*/
        (surfaceWidthPx - controls.roundToPx()).coerceAtLeast(0)
    }
    val isMultiline = remember(text, singleRowInputWidthPx, textStyle) {
        if (text.contains('\n')) true
        else if (text.isEmpty() || singleRowInputWidthPx <= 0) false
        else textMeasurer.measure(
            text = text,
            style = textStyle,
            constraints = Constraints(maxWidth = singleRowInputWidthPx)
        ).lineCount > 1
    }

    val canSend = text.isNotBlank() && !isStreaming
    val isRecording = speechState.status == SpeechStatus.RECORDING
    val isTranscribing = speechState.status == SpeechStatus.TRANSCRIBING_GEMINI ||
        speechState.status == SpeechStatus.TRANSCRIBING_FALLBACK

    val currentOnTextChange by rememberUpdatedState(onTextChange)
    val currentOnSend by rememberUpdatedState(onSend)
    val currentCanSend by rememberUpdatedState(canSend)

    val inputContent = remember {
        movableContentOf { v: TextFieldValue, modifier: Modifier ->
            Box(modifier = modifier.padding(horizontal = 6.dp, vertical = 6.dp), contentAlignment = Alignment.CenterStart) {
                if (v.text.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = typography.bodyPrimary.copy(fontSize = 15.sp),
                        color = colors.textTertiary,
                        maxLines = 1
                    )
                }
                BasicTextField(
                    value = v,
                    onValueChange = {
                        tfv = it
                        if (it.text != text) currentOnTextChange(it.text)
                    },
                    singleLine = false,
                    maxLines = 5,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(colors.accentPrimary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(onSend = { if (currentCanSend) currentOnSend() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("floating_chat_input")
                )
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("floating_chat_composer")
            .onSizeChanged { surfaceWidthPx = it.width }
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        shape = RoundedCornerShape(if (isMultiline || pendingApproval != null || showAudioConsent) 22.dp else 28.dp),
        color = colors.surfaceElevated,
        shadowElevation = 6.dp,
        border = BorderStroke(spacing.hairline, colors.borderSubtle)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (pendingApproval != null) {
                ActionApprovalCard(pendingApproval, onDenyAction, onAllowActionOnce, onAlwaysAllowAction)
            }
            if (showAudioConsent) {
                AudioConsentCard(onAcceptAudioConsent, onDismissAudioConsent)
            }
            speechState.statusLabel?.let { label ->
                val isFallback = label.startsWith("Using Android")
                Row(
                    modifier = Modifier
                        .padding(start = 14.dp, end = 14.dp, top = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isFallback) colors.accentContainer else colors.surfaceSecondary)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag(if (isFallback) "speech_fallback_badge" else "speech_status_label"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = typography.bodyPrimary.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        color = if (isFallback) colors.accentPrimary else colors.textSecondary
                    )
                }
            }
            speechState.message?.takeIf { speechState.status == SpeechStatus.ERROR }?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).testTag("speech_error"),
                    style = typography.bodyPrimary.copy(fontSize = 12.sp),
                    color = colors.statusDanger
                )
            }

            if (isRecording) {
                RecordingBar(speechState.elapsedMs, onCancelDictation, onStopDictation)
            } else if (!isMultiline) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = 48.dp)
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showPlus) {
                        PlusButton(onAddLinkClick!!, controlSize)
                        Spacer(Modifier.width(4.dp))
                    }
                    inputContent(value, Modifier.weight(1f))
                    Spacer(Modifier.width(4.dp))
                    MicButton(onMicClick, isTranscribing, controlSize)
                    SendOrStop(isStreaming, onStopStreaming, canSend, onSend, controlSize)
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
                    inputContent(value, Modifier.fillMaxWidth().padding(horizontal = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (showPlus) PlusButton(onAddLinkClick!!, controlSize)
                        Spacer(Modifier.weight(1f))
                        MicButton(onMicClick, isTranscribing, controlSize)
                        SendOrStop(isStreaming, onStopStreaming, canSend, onSend, controlSize)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlusButton(onClick: () -> Unit, size: androidx.compose.ui.unit.Dp) {
    val colors = LaterboxTheme.colors
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = "Add link", onClick = onClick)
            .semantics { contentDescription = "Add link" }
            .testTag("composer_add_link_button"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = LaterboxIcons.Plus),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = colors.textSecondary
        )
    }
}

@Composable
private fun MicButton(onClick: (() -> Unit)?, busy: Boolean, size: androidx.compose.ui.unit.Dp) {
    val colors = LaterboxTheme.colors
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(enabled = onClick != null && !busy, role = Role.Button, onClickLabel = "Voice input") { onClick?.invoke() }
            .semantics { contentDescription = "Voice input" }
            .testTag("composer_mic_button"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = LaterboxIcons.Mic),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (busy) colors.textTertiary else colors.textSecondary
        )
    }
}

@Composable
private fun SendOrStop(
    isStreaming: Boolean,
    onStopStreaming: (() -> Unit)?,
    canSend: Boolean,
    onSend: () -> Unit,
    size: androidx.compose.ui.unit.Dp
) {
    val colors = LaterboxTheme.colors
    if (isStreaming && onStopStreaming != null) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(colors.accentPrimary)
                .clickable(role = Role.Button, onClickLabel = "Stop generating", onClick = onStopStreaming)
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
                .size(size)
                .clip(CircleShape)
                .background(if (canSend) colors.accentPrimary else colors.surfaceSecondary)
                .clickable(enabled = canSend, role = Role.Button, onClickLabel = "Send message", onClick = onSend)
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

@Composable
private fun RecordingBar(elapsedMs: Long, onCancel: () -> Unit, onStop: () -> Unit) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val seconds = (elapsedMs / 1000).toInt()
    val timer = "%d:%02d".format(seconds / 60, seconds % 60)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .testTag("composer_recording_bar"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = "Cancel recording", onClick = onCancel)
                .semantics { contentDescription = "Cancel recording" }
                .testTag("composer_cancel_dictation"),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(LaterboxIcons.Close), null, Modifier.size(18.dp), tint = colors.textSecondary)
        }
        Box(Modifier.padding(start = 8.dp).size(10.dp).clip(CircleShape).background(colors.statusDanger))
        Text(
            text = "Recording  $timer",
            modifier = Modifier.weight(1f).padding(start = 8.dp).testTag("composer_recording_timer"),
            style = typography.bodyPrimary.copy(fontSize = 15.sp),
            color = colors.textPrimary
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.accentPrimary)
                .clickable(role = Role.Button, onClickLabel = "Stop and transcribe", onClick = onStop)
                .semantics { contentDescription = "Stop and transcribe" }
                .testTag("composer_stop_dictation"),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(LaterboxIcons.Check), null, Modifier.size(18.dp), tint = colors.onAccentPrimary)
        }
    }
}

@Composable
private fun ActionApprovalCard(
    action: ChatAction,
    onDeny: () -> Unit,
    onAllowOnce: () -> Unit,
    onAlwaysAllow: () -> Unit
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)
            .testTag("action_approval_card")
    ) {
        Text(action.actionName, style = typography.bodyPrimary.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
        Text(action.explanation, modifier = Modifier.padding(top = 2.dp), style = typography.bodyPrimary.copy(fontSize = 13.sp), color = colors.textSecondary)
        action.targetLabel?.let {
            Text("Item: $it", modifier = Modifier.padding(top = 6.dp), style = typography.bodyPrimary.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium), color = colors.textPrimary, maxLines = 2)
        }
        Text(action.consequenceSummary, modifier = Modifier.padding(top = 2.dp), style = typography.bodyPrimary.copy(fontSize = 12.sp), color = colors.textTertiary)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ApprovalButton("Don't allow", filled = false, onClick = onDeny, tag = "approval_deny", modifier = Modifier.weight(1f))
            ApprovalButton("Allow once", filled = true, onClick = onAllowOnce, tag = "approval_allow_once", modifier = Modifier.weight(1f))
        }
        ApprovalButton(
            "Always allow in this conversation", filled = false, onClick = onAlwaysAllow,
            tag = "approval_always_allow", modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        )
        Spacer(Modifier.size(6.dp))
        Box(Modifier.fillMaxWidth().size(width = 0.dp, height = 1.dp).background(colors.borderSubtle))
    }
}

@Composable
private fun AudioConsentCard(onAccept: () -> Unit, onDismiss: () -> Unit) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)
            .testTag("audio_consent_card")
    ) {
        Text("Voice dictation uses Google Gemini", style = typography.bodyPrimary.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
        Text(
            "Your recording is uploaded to Google to be transcribed, then deleted from this device. " +
                "If Gemini is unavailable, Laterbox falls back to Android speech recognition. Nothing is sent until you stop recording.",
            modifier = Modifier.padding(top = 2.dp),
            style = typography.bodyPrimary.copy(fontSize = 13.sp),
            color = colors.textSecondary
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ApprovalButton("Not now", filled = false, onClick = onDismiss, tag = "audio_consent_dismiss", modifier = Modifier.weight(1f))
            ApprovalButton("Agree & continue", filled = true, onClick = onAccept, tag = "audio_consent_accept", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ApprovalButton(
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    Box(
        modifier = modifier
            .sizeIn(minHeight = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (filled) colors.accentPrimary else colors.surfaceSecondary)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = typography.bodyPrimary.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
            color = if (filled) colors.onAccentPrimary else colors.textPrimary
        )
    }
}

/**
 * Shared bottom inset wrapper used by both the Inbox and Chat composer hosts.
 * Uses `navigationBars.union(ime)` in a single `windowInsetsPadding` call so IME and navigation bar
 * heights are never double-counted, and tightens the bottom gap to ~5dp while the keyboard is open.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComposerInsetHost(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    openKeyboardGap: Dp = 5.dp,
    closedKeyboardGap: Dp = 12.dp,
    onMeasuredHeightPx: ((Int) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val isKeyboardOpen = imeBottom > navBottom
    val bottomGap by animateDpAsState(
        targetValue = if (isKeyboardOpen) openKeyboardGap else closedKeyboardGap,
        label = "composer_bottom_gap"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("composer_inset_host")
            .then(
                if (onMeasuredHeightPx != null) Modifier.onSizeChanged { onMeasuredHeightPx(it.height) }
                else Modifier
            )
            .windowInsetsPadding(
                WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom)
            )
            .padding(start = horizontalPadding, end = horizontalPadding, top = 4.dp, bottom = bottomGap)
    ) {
        content()
    }
}

fun appendDictatedTranscript(existing: String, transcript: String): String {
    val clean = transcript.trim()
    if (clean.isEmpty()) return existing
    return if (existing.isBlank()) clean else existing.trimEnd() + " " + clean
}
