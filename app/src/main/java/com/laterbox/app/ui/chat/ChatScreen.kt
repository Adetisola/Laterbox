package com.laterbox.app.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laterbox.app.ai.AssistantActionHandler
import com.laterbox.app.ai.ChatAction
import com.laterbox.app.data.local.entity.ChatMessageEntity
import com.laterbox.app.data.repository.ChatMessageStatus
import com.laterbox.app.data.repository.ChatRole
import com.laterbox.app.diagnostics.AppHealthReport
import com.laterbox.app.diagnostics.AppHealthStatus
import com.laterbox.app.ui.components.FloatingChatComposer
import com.laterbox.app.ui.components.LaterboxBanner
import com.laterbox.app.ui.components.LaterboxConfirmDialog
import com.laterbox.app.ui.components.LaterboxIconButton
import com.laterbox.app.ui.components.LaterboxIcons
import com.laterbox.app.ui.components.LaterboxPrimaryButton
import com.laterbox.app.ui.components.LaterboxShapeTokens
import com.laterbox.app.ui.theme.LaterboxTheme

@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onMenuClick: (() -> Unit)?,
    onNewChatClick: () -> Unit,
    onRunHealthCheck: () -> Unit,
    onAcceptAiDisclosure: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onComposerTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onStopStreaming: () -> Unit,
    onRetryMessage: (String) -> Unit,
    onQuickPromptClick: (String) -> Unit,
    onActionClick: (ChatAction) -> Unit,
    onConfirmMutatingAction: () -> Unit,
    onDismissMutatingAction: () -> Unit,
    onDismissNotice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing
    val listState = rememberLazyListState()

    val itemCount = uiState.messages.size
    LaunchedEffect(itemCount, uiState.streamingPartialText.length) {
        if (itemCount > 0) {
            listState.animateScrollToItem((itemCount - 1).coerceAtLeast(0))
        }
    }

    uiState.pendingMutatingAction?.let { pending ->
        val (title, message, confirmLabel) = when (pending) {
            is ChatAction.RetryInspection -> Triple(
                "Retry media inspection?",
                "Re-inspect \"${pending.title}\" to refresh downloadable formats.",
                "Retry inspection"
            )
            is ChatAction.RetryDownload -> Triple(
                "Retry download?",
                "Re-queue the download for \"${pending.title}\".",
                "Retry download"
            )
            else -> Triple("Confirm action?", pending.label, "Confirm")
        }
        LaterboxConfirmDialog(
            title = title,
            message = message,
            confirmText = confirmLabel,
            onConfirm = onConfirmMutatingAction,
            onDismiss = onDismissMutatingAction
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundPrimary)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.base, vertical = spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onMenuClick != null) {
                    LaterboxIconButton(
                        iconRes = LaterboxIcons.Menu,
                        contentDescription = "Open navigation menu",
                        onClick = onMenuClick
                    )
                    Spacer(modifier = Modifier.width(spacing.sm))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiState.activeConversationTitle,
                        style = typography.titleLarge.copy(fontSize = 18.sp),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Powered by Google AI Studio (Gemini)",
                        style = typography.metaText,
                        color = colors.textSecondary,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(spacing.xs))

                LaterboxIconButton(
                    iconRes = LaterboxIcons.Health,
                    contentDescription = "Run App Health Check",
                    onClick = onRunHealthCheck,
                    enabled = !uiState.isRunningHealthCheck
                )

                Spacer(modifier = Modifier.width(spacing.xs))

                LaterboxIconButton(
                    iconRes = LaterboxIcons.Chat,
                    contentDescription = "New chat",
                    onClick = onNewChatClick
                )
            }

            if (!uiState.assistantEnabled) {
                LaterboxBanner(
                    text = "Laterbox Assistant is currently disabled in Settings.",
                    isWarning = true,
                    actionText = "Settings",
                    onActionClick = onOpenAiSettings,
                    modifier = Modifier.padding(horizontal = spacing.base, vertical = spacing.xs)
                )
            }

            uiState.statusNotice?.let { notice ->
                LaterboxBanner(
                    text = notice,
                    actionText = "Dismiss",
                    onActionClick = onDismissNotice,
                    modifier = Modifier.padding(horizontal = spacing.base, vertical = spacing.xs)
                )
            }

            if (!uiState.aiDisclosureAccepted && uiState.assistantEnabled) {
                AiDisclosureConsentCard(
                    onAccept = onAcceptAiDisclosure,
                    onOpenSettings = onOpenAiSettings,
                    modifier = Modifier.padding(horizontal = spacing.base, vertical = spacing.xs)
                )
            }

            uiState.latestHealthReport?.let { report ->
                HealthSummaryCard(
                    report = report,
                    modifier = Modifier.padding(horizontal = spacing.base, vertical = spacing.xs)
                )
            }

            // Messages or Starter Prompts
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (uiState.messages.isEmpty()) {
                    ChatEmptyStarter(
                        assistantEnabled = uiState.assistantEnabled,
                        aiDisclosureAccepted = uiState.aiDisclosureAccepted,
                        onQuickPromptClick = onQuickPromptClick,
                        onRunHealthCheck = onRunHealthCheck
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = spacing.base,
                            end = spacing.base,
                            top = spacing.sm,
                            bottom = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(spacing.md)
                    ) {
                        items(uiState.messages, key = { it.id }) { message ->
                            val isActiveStreamingBubble =
                                uiState.isStreaming && message.id == uiState.streamingAssistantMessageId
                            val displayedContent = if (isActiveStreamingBubble) {
                                uiState.streamingPartialText
                            } else {
                                message.content
                            }
                            ChatMessageBubble(
                                message = message,
                                displayedContent = displayedContent,
                                isStreamingThis = isActiveStreamingBubble,
                                onRetry = { onRetryMessage(message.id) },
                                onActionClick = onActionClick
                            )
                        }
                    }
                }
            }

            // Docked Floating Composer anchored calmly at the bottom
            if (uiState.assistantEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = spacing.base, vertical = spacing.sm)
                ) {
                    FloatingChatComposer(
                        text = uiState.composerText,
                        onTextChange = onComposerTextChange,
                        onSend = onSendMessage,
                        isStreaming = uiState.isStreaming,
                        onStopStreaming = onStopStreaming,
                        placeholder = "Ask about your links, downloads, or app health..."
                    )
                }
            }
        }
    }
}

@Composable
private fun AiDisclosureConsentCard(
    onAccept: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ai_disclosure_card"),
        shape = LaterboxShapeTokens.Card,
        color = colors.surfacePrimary,
        border = BorderStroke(spacing.hairline, colors.accentPrimary.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                Icon(
                    painter = painterResource(id = LaterboxIcons.Sparkles),
                    contentDescription = null,
                    tint = colors.accentPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Remote AI Privacy Notice",
                    style = typography.uiMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary
                )
            }
            Text(
                text = "When you send a message, your prompt and relevant saved link metadata (titles, URLs, notes, and app diagnostics—never API keys or private storage paths) are sent remotely to Google AI Studio (Gemini). You can disable this or review your API key anytime in Settings.",
                style = typography.bodySecondary.copy(fontSize = 13.sp),
                color = colors.textSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LaterboxPrimaryButton(
                    text = "Accept & Continue",
                    onClick = onAccept,
                    modifier = Modifier.testTag("accept_ai_disclosure_button")
                )
                Text(
                    text = "Reminders & AI settings",
                    style = typography.metaText.copy(fontWeight = FontWeight.Medium),
                    color = colors.accentPrimary,
                    modifier = Modifier
                        .clip(LaterboxShapeTokens.Button)
                        .clickable(role = Role.Button, onClick = onOpenSettings)
                        .padding(horizontal = spacing.sm, vertical = spacing.xs)
                )
            }
        }
    }
}

@Composable
private fun HealthSummaryCard(
    report: AppHealthReport,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    val statusColor = when (report.overallStatus) {
        AppHealthStatus.HEALTHY -> colors.statusSuccess
        AppHealthStatus.ATTENTION_NEEDED -> colors.accentPrimary
        AppHealthStatus.CRITICAL -> colors.statusDanger
    }
    val statusLabel = when (report.overallStatus) {
        AppHealthStatus.HEALTHY -> "Healthy"
        AppHealthStatus.ATTENTION_NEEDED -> "Attention Needed"
        AppHealthStatus.CRITICAL -> "Critical Issue"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = LaterboxShapeTokens.Card,
        color = colors.surfacePrimary,
        border = BorderStroke(spacing.hairline, colors.borderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    Icon(
                        painter = painterResource(id = LaterboxIcons.Health),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = statusColor
                    )
                    Text(
                        text = "App Health: $statusLabel",
                        style = typography.uiMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = statusColor
                    )
                }
                Text(
                    text = "${report.availableStorageMb()} MB free",
                    style = typography.metaText,
                    color = colors.textSecondary
                )
            }

            Text(
                text = "yt-dlp: ${report.ytDlpStateLabel}${report.ytDlpVersion?.let { " v$it" } ?: ""} • FFmpeg: ${report.ffmpegStateLabel} • QuickJS: ${if (report.quickJsAvailable) "Ready" else "Missing"}",
                style = typography.metaText,
                color = colors.textSecondary
            )

            if (report.issues.isNotEmpty()) {
                report.issues.take(3).forEach { issue ->
                    Text(
                        text = "• ${issue.title}: ${issue.suggestedFix}",
                        style = typography.bodySecondary.copy(fontSize = 12.sp),
                        color = colors.textPrimary
                    )
                }
            } else {
                Text(
                    text = "Media extractor, storage, permissions, and connectivity look healthy.",
                    style = typography.bodySecondary.copy(fontSize = 12.sp),
                    color = colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun ChatEmptyStarter(
    assistantEnabled: Boolean,
    aiDisclosureAccepted: Boolean,
    onQuickPromptClick: (String) -> Unit,
    onRunHealthCheck: () -> Unit
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    val starterPrompts = remember {
        listOf(
            "Summarize what I saved recently",
            "Why did a download or inspection fail?",
            "Find my unread videos",
            "How do I save links faster?"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.base, vertical = spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = LaterboxIcons.Sparkles),
            contentDescription = null,
            tint = colors.accentPrimary,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(spacing.sm))
        Text(
            text = "Ask Laterbox anything",
            style = typography.titleLarge,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(spacing.xs))
        Text(
            text = "Search your saved library, check media extractions and downloads, or run diagnostics.",
            style = typography.bodySecondary,
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.height(spacing.md))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            starterPrompts.forEach { prompt ->
                StarterChip(
                    label = prompt,
                    enabled = assistantEnabled && aiDisclosureAccepted,
                    onClick = { onQuickPromptClick(prompt) }
                )
            }
            StarterChip(
                label = "Check App Health",
                enabled = true,
                onClick = onRunHealthCheck
            )
        }
    }
}

@Composable
private fun StarterChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    Box(
        modifier = Modifier
            .clip(LaterboxShapeTokens.Chip)
            .background(colors.surfacePrimary)
            .border(spacing.hairline, colors.borderSubtle, LaterboxShapeTokens.Chip)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = typography.metaText.copy(fontSize = 13.sp),
            color = if (enabled) colors.textPrimary else colors.textTertiary,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatMessageBubble(
    message: ChatMessageEntity,
    displayedContent: String,
    isStreamingThis: Boolean,
    onRetry: () -> Unit,
    onActionClick: (ChatAction) -> Unit
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing
    val isUser = message.role == ChatRole.USER.name

    val parsedActions = remember(message.actionsJson) {
        AssistantActionHandler.fromJsonList(message.actionsJson)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (isUser) 18.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 18.dp
                ),
                color = if (isUser) colors.accentPrimary else colors.surfacePrimary,
                border = if (isUser) null else BorderStroke(spacing.hairline, colors.borderSubtle)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isStreamingThis && displayedContent.isBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.5.dp,
                                color = colors.accentPrimary
                            )
                            Text(
                                text = "Thinking…",
                                style = typography.bodySecondary,
                                color = colors.textSecondary
                            )
                        }
                    } else if (displayedContent.isNotBlank()) {
                        Text(
                            text = displayedContent,
                            style = typography.bodyPrimary.copy(fontSize = 14.sp),
                            color = if (isUser) colors.onAccentPrimary else colors.textPrimary
                        )
                    }

                    if (message.status == ChatMessageStatus.CANCELLED.name) {
                        Text(
                            text = "Stopped",
                            style = typography.metaText.copy(fontSize = 11.sp),
                            color = colors.textTertiary
                        )
                    }

                    if (message.status == ChatMessageStatus.FAILED.name) {
                        Text(
                            text = message.errorMessage ?: "Couldn't complete response.",
                            style = typography.bodySecondary.copy(fontSize = 12.sp),
                            color = colors.statusDanger
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(LaterboxShapeTokens.Chip)
                                .background(colors.surfaceSecondary)
                                .clickable(role = Role.Button, onClick = onRetry)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = LaterboxIcons.Refresh),
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = colors.accentPrimary
                            )
                            Text(
                                text = "Retry",
                                style = typography.metaText.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.accentPrimary
                            )
                        }
                    }
                }
            }

            if (!isUser && parsedActions.isNotEmpty() && !isStreamingThis) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    parsedActions.forEach { action ->
                        Box(
                            modifier = Modifier
                                .clip(LaterboxShapeTokens.Chip)
                                .background(colors.accentContainer)
                                .border(spacing.hairline, colors.accentPrimary.copy(alpha = 0.35f), LaterboxShapeTokens.Chip)
                                .clickable(role = Role.Button) { onActionClick(action) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = action.label,
                                style = typography.metaText.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = colors.accentPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}
