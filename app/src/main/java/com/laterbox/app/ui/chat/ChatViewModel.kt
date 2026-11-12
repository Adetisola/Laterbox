package com.laterbox.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laterbox.app.ai.AssistantActionHandler
import com.laterbox.app.ai.ChatAction
import com.laterbox.app.ai.GeminiClient
import com.laterbox.app.ai.GeminiRole
import com.laterbox.app.ai.GeminiStreamEvent
import com.laterbox.app.ai.GeminiTurn
import com.laterbox.app.ai.LaterboxContextEngine
import com.laterbox.app.data.local.entity.ChatConversationEntity
import com.laterbox.app.data.local.entity.ChatMessageEntity
import com.laterbox.app.data.preferences.UserPreferencesRepository
import com.laterbox.app.data.repository.ChatMessageStatus
import com.laterbox.app.data.repository.ChatRepository
import com.laterbox.app.data.repository.ChatRole
import com.laterbox.app.data.repository.LinkRepository
import com.laterbox.app.diagnostics.AppHealthChecker
import com.laterbox.app.diagnostics.AppHealthReport
import com.laterbox.app.media.domain.repository.MediaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatUiState(
    val activeConversationId: String? = null,
    val activeConversationTitle: String = "Laterbox Assistant",
    val messages: List<ChatMessageEntity> = emptyList(),
    val recentConversations: List<ChatConversationEntity> = emptyList(),
    val assistantEnabled: Boolean = true,
    val aiDisclosureAccepted: Boolean = false,
    val composerText: String = "",
    val isStreaming: Boolean = false,
    val streamingAssistantMessageId: String? = null,
    val streamingPartialText: String = "",
    val latestHealthReport: AppHealthReport? = null,
    val isRunningHealthCheck: Boolean = false,
    val pendingMutatingAction: ChatAction? = null,
    val statusNotice: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val laterboxContextEngine: LaterboxContextEngine,
    private val appHealthChecker: AppHealthChecker,
    private val linkRepository: LinkRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _activeConversationId = MutableStateFlow<String?>(null)
    private val _composerText = MutableStateFlow("")
    private val _isStreaming = MutableStateFlow(false)
    private val _streamingAssistantMessageId = MutableStateFlow<String?>(null)
    private val _streamingPartialText = MutableStateFlow("")
    private val _latestHealthReport = MutableStateFlow<AppHealthReport?>(null)
    private val _isRunningHealthCheck = MutableStateFlow(false)
    private val _pendingMutatingAction = MutableStateFlow<ChatAction?>(null)
    private val _statusNotice = MutableStateFlow<String?>(null)

    private var streamingJob: Job? = null
    private var lastHandledInvocationKey: String? = null
    private var pendingConsentConversationId: String? = null

    val recentConversations: StateFlow<List<ChatConversationEntity>> =
        chatRepository.observeRecentConversations(30).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val activeMessagesFlow = _activeConversationId.flatMapLatest { convId ->
        if (convId.isNullOrBlank()) flowOf(emptyList()) else chatRepository.observeMessages(convId)
    }

    private val activeConversationFlow = _activeConversationId.flatMapLatest { convId ->
        if (convId.isNullOrBlank()) flowOf(null) else chatRepository.observeConversation(convId)
    }

    private val streamingTuple = combine(
        _isStreaming,
        _streamingAssistantMessageId,
        _streamingPartialText,
        _composerText
    ) { isStreaming, msgId, partial, composer ->
        Quad(isStreaming, msgId, partial, composer)
    }

    private val auxiliaryTuple = combine(
        _latestHealthReport,
        _isRunningHealthCheck,
        _pendingMutatingAction,
        _statusNotice
    ) { report, running, pendingAction, notice ->
        AuxState(report, running, pendingAction, notice)
    }

    val uiState: StateFlow<ChatUiState> = combine(
        _activeConversationId,
        activeConversationFlow,
        activeMessagesFlow,
        recentConversations,
        userPreferencesRepository.userPreferences,
        streamingTuple,
        auxiliaryTuple
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val activeId = args[0] as String?
        val activeConv = args[1] as ChatConversationEntity?
        @Suppress("UNCHECKED_CAST")
        val messages = args[2] as List<ChatMessageEntity>
        @Suppress("UNCHECKED_CAST")
        val recent = args[3] as List<ChatConversationEntity>
        val prefs = args[4] as com.laterbox.app.data.preferences.UserPreferences
        val stream = args[5] as Quad
        val aux = args[6] as AuxState

        ChatUiState(
            activeConversationId = activeId,
            activeConversationTitle = activeConv?.title ?: "Laterbox Assistant",
            messages = messages,
            recentConversations = recent,
            assistantEnabled = prefs.assistantEnabled,
            aiDisclosureAccepted = prefs.assistantAiDisclosureAccepted,
            composerText = stream.composer,
            isStreaming = stream.isStreaming,
            streamingAssistantMessageId = stream.msgId,
            streamingPartialText = stream.partial,
            latestHealthReport = aux.report,
            isRunningHealthCheck = aux.runningHealthCheck,
            pendingMutatingAction = aux.pendingAction,
            statusNotice = aux.notice
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatUiState()
    )

    fun onComposerTextChange(text: String) {
        _composerText.value = text
    }

    fun clearStatusNotice() {
        _statusNotice.value = null
    }

    fun openChat(
        conversationId: String?,
        initialPrompt: String?,
        invocationKey: String? = null
    ) {
        val key = invocationKey ?: "${conversationId.orEmpty()}|${initialPrompt.orEmpty()}"
        if (lastHandledInvocationKey == key && key.isNotBlank()) {
            return
        }
        lastHandledInvocationKey = key

        val trimmedPrompt = initialPrompt?.trim().orEmpty()
        val targetConvId = conversationId?.trim()?.takeIf { it.isNotBlank() }

        if (trimmedPrompt.isBlank()) {
            if (targetConvId != null) {
                selectConversation(targetConvId)
            } else if (_activeConversationId.value == null) {
                startNewChat()
            }
            return
        }

        viewModelScope.launch {
            if (_activeConversationId.value != targetConvId) {
                cancelStreaming()
            }
            val convId = targetConvId ?: chatRepository.createConversation(trimmedPrompt)
            _activeConversationId.value = convId
            submitUserPromptToConversation(convId, trimmedPrompt)
        }
    }

    fun startNewChat() {
        cancelStreaming()
        pendingConsentConversationId = null
        _activeConversationId.value = null
        _latestHealthReport.value = null
        _statusNotice.value = null
    }

    fun selectConversation(conversationId: String) {
        if (_activeConversationId.value != conversationId) {
            cancelStreaming()
            pendingConsentConversationId = null
            _activeConversationId.value = conversationId
            _statusNotice.value = null
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            if (_activeConversationId.value == conversationId) {
                cancelStreaming()
                pendingConsentConversationId = null
                _activeConversationId.value = null
            }
            chatRepository.deleteConversation(conversationId)
        }
    }

    fun acceptAiDisclosure() {
        viewModelScope.launch {
            userPreferencesRepository.setAssistantAiDisclosureAccepted(true)
            val pendingConvId = pendingConsentConversationId
            pendingConsentConversationId = null
            if (pendingConvId != null && _activeConversationId.value == pendingConvId) {
                val prefs = userPreferencesRepository.userPreferencesFlow.first()
                if (prefs.assistantEnabled) {
                    val lastUser = chatRepository.getMessagesSnapshot(pendingConvId)
                        .lastOrNull { it.role == ChatRole.USER.name }
                    if (lastUser != null) {
                        generateAssistantReply(pendingConvId, lastUser.content)
                    }
                }
            }
        }
    }

    fun sendCurrentMessage() {
        val prompt = _composerText.value.trim()
        if (prompt.isBlank() || _isStreaming.value) return
        _composerText.value = ""
        sendMessage(prompt)
    }

    fun sendMessage(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isBlank() || _isStreaming.value) return

        viewModelScope.launch {
            val convId = _activeConversationId.value ?: chatRepository.createConversation(trimmed).also {
                _activeConversationId.value = it
            }
            submitUserPromptToConversation(convId, trimmed)
        }
    }

    private suspend fun submitUserPromptToConversation(conversationId: String, trimmedPrompt: String) {
        // Guarantee user prompt is persisted locally BEFORE checking consent / network / remote calls
        chatRepository.addUserMessage(conversationId, trimmedPrompt)

        val prefs = userPreferencesRepository.userPreferencesFlow.first()
        if (!prefs.assistantEnabled) {
            _statusNotice.value = "AI Assistant is turned off in Settings → Reminders & AI."
            return
        }
        if (!prefs.assistantAiDisclosureAccepted) {
            pendingConsentConversationId = conversationId
            return
        }
        pendingConsentConversationId = null
        generateAssistantReply(conversationId, trimmedPrompt)
    }

    fun retryMessage(assistantMessageId: String) {
        if (_isStreaming.value) return
        val convId = _activeConversationId.value ?: return

        viewModelScope.launch {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            if (!prefs.assistantEnabled) {
                _statusNotice.value = "AI Assistant is turned off in Settings → Reminders & AI."
                return@launch
            }
            if (!prefs.assistantAiDisclosureAccepted) {
                pendingConsentConversationId = convId
                return@launch
            }
            chatRepository.deleteMessage(assistantMessageId)
            val lastUserPrompt = chatRepository.getMessagesSnapshot(convId)
                .lastOrNull { it.role == ChatRole.USER.name }
                ?.content
                ?: return@launch
            generateAssistantReply(convId, lastUserPrompt)
        }
    }

    fun cancelStreaming() {
        streamingJob?.cancel()
        streamingJob = null
    }

    fun runAppHealthCheck() {
        if (_isRunningHealthCheck.value) return
        viewModelScope.launch {
            _isRunningHealthCheck.value = true
            try {
                _latestHealthReport.value = appHealthChecker.runCheck()
            } finally {
                _isRunningHealthCheck.value = false
            }
        }
    }

    fun requestMutatingActionConfirmation(action: ChatAction) {
        _pendingMutatingAction.value = action
    }

    fun dismissMutatingAction() {
        _pendingMutatingAction.value = null
    }

    fun confirmMutatingAction() {
        val action = _pendingMutatingAction.value ?: return
        _pendingMutatingAction.value = null

        viewModelScope.launch {
            when (action) {
                is ChatAction.RetryInspection -> {
                    val link = linkRepository.getLinkById(action.linkId).first()
                    if (link != null) {
                        mediaRepository.startInspection(
                            linkId = link.id,
                            url = link.originalUrl,
                            previewImageUrl = link.previewImageUrl,
                            forceRefresh = true
                        )
                        _statusNotice.value = "Started fresh media inspection for \"${link.title}\"."
                    } else {
                        _statusNotice.value = "Saved link no longer exists in your library."
                    }
                }
                is ChatAction.RetryDownload -> {
                    mediaRepository.retryDownload(action.taskId)
                    _statusNotice.value = "Re-queued download \"${action.title}\"."
                }
                else -> Unit
            }
        }
    }

    private fun generateAssistantReply(conversationId: String, latestPrompt: String) {
        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            val placeholder = chatRepository.createAssistantPlaceholder(conversationId)
            _isStreaming.value = true
            _streamingAssistantMessageId.value = placeholder.id
            _streamingPartialText.value = ""

            val accumulated = StringBuilder()
            var resolvedKey: String? = null

            try {
                val prefs = userPreferencesRepository.userPreferencesFlow.first()
                resolvedKey = GeminiClient.resolveApiKey(prefs.geminiApiKey.ifBlank { null })
                val builtContext = laterboxContextEngine.buildContextForPrompt(latestPrompt)

                val snapshot = chatRepository.getMessagesSnapshot(conversationId)
                    .filter { it.id != placeholder.id && it.content.isNotBlank() }
                val turns = buildTurnsFromSnapshot(snapshot)

                var receivedTerminal = false
                GeminiClient.streamChat(
                    turns = turns,
                    systemInstruction = builtContext.systemInstruction,
                    customApiKey = prefs.geminiApiKey.ifBlank { null }
                ).collect { event ->
                    when (event) {
                        is GeminiStreamEvent.Delta -> {
                            accumulated.append(event.chunk)
                            _streamingPartialText.value = accumulated.toString()
                        }
                        is GeminiStreamEvent.Completed -> {
                            receivedTerminal = true
                            val parsed = AssistantActionHandler.parseModelResponse(event.fullText)
                            val actionsJson = AssistantActionHandler.toJsonList(parsed.actions)
                            chatRepository.updateAssistantMessage(
                                messageId = placeholder.id,
                                conversationId = conversationId,
                                content = GeminiClient.redactSecrets(parsed.displayText, resolvedKey),
                                status = ChatMessageStatus.COMPLETE,
                                actionsJson = actionsJson
                            )
                        }
                        is GeminiStreamEvent.Error -> {
                            receivedTerminal = true
                            val cleanError = GeminiClient.redactSecrets(event.reason.userMessage, resolvedKey)
                            chatRepository.updateAssistantMessage(
                                messageId = placeholder.id,
                                conversationId = conversationId,
                                content = GeminiClient.redactSecrets(accumulated.toString(), resolvedKey),
                                status = ChatMessageStatus.FAILED,
                                errorMessage = cleanError
                            )
                        }
                    }
                }

                if (!receivedTerminal && accumulated.isNotEmpty()) {
                    val parsed = AssistantActionHandler.parseModelResponse(accumulated.toString())
                    chatRepository.updateAssistantMessage(
                        messageId = placeholder.id,
                        conversationId = conversationId,
                        content = GeminiClient.redactSecrets(parsed.displayText, resolvedKey),
                        status = ChatMessageStatus.COMPLETE,
                        actionsJson = AssistantActionHandler.toJsonList(parsed.actions)
                    )
                }
            } catch (ce: CancellationException) {
                withContext(NonCancellable) {
                    val partial = GeminiClient.redactSecrets(accumulated.toString(), resolvedKey).trim()
                    chatRepository.updateAssistantMessage(
                        messageId = placeholder.id,
                        conversationId = conversationId,
                        content = partial,
                        status = ChatMessageStatus.CANCELLED,
                        errorMessage = if (partial.isEmpty()) "Stopped before response started." else null
                    )
                }
                throw ce
            } catch (t: Throwable) {
                val cleanMsg = GeminiClient.redactSecrets(t.message ?: "Assistant request failed.", resolvedKey)
                chatRepository.updateAssistantMessage(
                    messageId = placeholder.id,
                    conversationId = conversationId,
                    content = GeminiClient.redactSecrets(accumulated.toString(), resolvedKey),
                    status = ChatMessageStatus.FAILED,
                    errorMessage = cleanMsg.take(180)
                )
            } finally {
                _isStreaming.value = false
                _streamingAssistantMessageId.value = null
                _streamingPartialText.value = ""
            }
        }
    }

    private fun buildTurnsFromSnapshot(messages: List<ChatMessageEntity>): List<GeminiTurn> {
        val recent = messages.takeLast(12)
        return recent.mapNotNull { entity ->
            val role = when (entity.role) {
                ChatRole.USER.name -> GeminiRole.USER
                ChatRole.ASSISTANT.name -> GeminiRole.MODEL
                else -> null
            } ?: return@mapNotNull null
            val cleanText = entity.content.trim()
            if (cleanText.isEmpty()) return@mapNotNull null
            GeminiTurn(role = role, text = cleanText)
        }
    }

    private data class Quad(
        val isStreaming: Boolean,
        val msgId: String?,
        val partial: String,
        val composer: String
    )

    private data class AuxState(
        val report: AppHealthReport?,
        val runningHealthCheck: Boolean,
        val pendingAction: ChatAction?,
        val notice: String?
    )
}
