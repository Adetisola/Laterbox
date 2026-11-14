package com.laterbox.app.ai.speech

import com.laterbox.app.ai.AiFailure
import com.laterbox.app.ai.GeminiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SpeechStatus { IDLE, RECORDING, TRANSCRIBING_GEMINI, TRANSCRIBING_FALLBACK, ERROR }

data class ComposerSpeechUiState(
    val status: SpeechStatus = SpeechStatus.IDLE,
    val elapsedMs: Long = 0L,
    /** Stays true for a few seconds after fallback is used so the user notices it. */
    val fallbackBadgeVisible: Boolean = false,
    val message: String? = null
) {
    val statusLabel: String? get() = when (status) {
        SpeechStatus.TRANSCRIBING_GEMINI -> "Transcribing with Gemini"
        SpeechStatus.TRANSCRIBING_FALLBACK -> "Listening with Android speech recognition"
        else -> if (fallbackBadgeVisible) "Using Android speech recognition" else null
    }
    val isBusy: Boolean get() = status != SpeechStatus.IDLE && status != SpeechStatus.ERROR
}

/**
 * Orchestrates pre-flight -> record -> Gemini transcription -> automatic Android fallback.
 * Never auto-sends; the transcript is delivered through [onTranscript] for insertion into a draft.
 */
class SpeechTranscriptionCoordinator(
    private val recorder: AudioRecorder,
    private val gemini: GeminiSpeechTranscriber,
    private val fallback: AndroidFallbackRecognizer,
    private val scope: CoroutineScope,
    private val apiKeyProvider: suspend () -> String?,
    private val isNetworkAvailable: () -> Boolean = { true },
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val minDurationMs: Long = 350L,
    private val badgeVisibleMs: Long = 3_500L,
    private val rateLimitCooldownMs: Long = 60_000L
) {
    private val _state = MutableStateFlow(ComposerSpeechUiState())
    val state: StateFlow<ComposerSpeechUiState> = _state.asStateFlow()

    private var tickJob: Job? = null
    private var workJob: Job? = null
    private var badgeJob: Job? = null
    private var isStarting = false
    private var geminiCooldownUntilMs = 0L
    private var activeTranscriptCallback: ((String) -> Unit)? = null

    /**
     * Pre-flights Gemini availability (API key, connectivity, rate-limit cooldown) before starting
     * microphone recording. If Gemini cannot run, routes straight to live Android SpeechRecognizer
     * instead of recording audio that Android's recognizer cannot consume.
     */
    fun startDictation(onTranscript: (String) -> Unit) {
        if (_state.value.isBusy || isStarting) return
        isStarting = true
        activeTranscriptCallback = onTranscript
        workJob?.cancel()
        workJob = scope.launch {
            try {
                val resolvedKey = GeminiClient.resolveApiKey(apiKeyProvider())
                val online = isNetworkAvailable()
                val inCooldown = nowMs() < geminiCooldownUntilMs
                if (resolvedKey != null && online && !inCooldown) {
                    startRecording()
                } else if (fallback.isAvailable()) {
                    startLiveFallback(onTranscript)
                } else {
                    activeTranscriptCallback = null
                    val reason = when {
                        resolvedKey == null -> "Add a Gemini API key in Settings for voice input."
                        !online -> "You're offline and Android speech recognition isn't available."
                        else -> "Gemini voice quota is temporarily rate-limited. Try again shortly."
                    }
                    _state.value = ComposerSpeechUiState(SpeechStatus.ERROR, message = reason)
                }
            } finally {
                isStarting = false
            }
        }
    }

    /** Starts capture directly. Returns false if a session is already active or capture failed. */
    fun startRecording(): Boolean {
        if (_state.value.isBusy) return false
        if (!recorder.start()) {
            _state.value = ComposerSpeechUiState(SpeechStatus.ERROR, message = "Couldn't start recording")
            return false
        }
        badgeJob?.cancel()
        _state.value = ComposerSpeechUiState(SpeechStatus.RECORDING)
        val began = nowMs()
        tickJob = scope.launch {
            while (true) {
                delay(250)
                _state.value = _state.value.copy(elapsedMs = (nowMs() - began).coerceAtLeast(0L))
            }
        }
        return true
    }

    fun cancel() {
        isStarting = false
        tickJob?.cancel()
        workJob?.cancel()
        recorder.cancel()
        activeTranscriptCallback = null
        _state.value = ComposerSpeechUiState()
    }

    fun clearError() {
        if (_state.value.status == SpeechStatus.ERROR) _state.value = ComposerSpeechUiState()
    }

    fun stopAndTranscribe() {
        val cb = activeTranscriptCallback ?: return
        stopAndTranscribe(cb)
    }

    fun stopAndTranscribe(onTranscript: (String) -> Unit) {
        if (_state.value.status != SpeechStatus.RECORDING) return
        tickJob?.cancel()
        val audio = recorder.stop()
        if (audio == null || audio.durationMs < minDurationMs || audio.file.length() == 0L) {
            audio?.file?.delete()
            activeTranscriptCallback = null
            _state.value = ComposerSpeechUiState()
            return
        }
        _state.value = ComposerSpeechUiState(SpeechStatus.TRANSCRIBING_GEMINI)
        workJob = scope.launch {
            var usedFallback = false
            try {
                val result = gemini.transcribe(audio, apiKeyProvider())
                val text: String? = when (result) {
                    is TranscribeResult.Success -> result.text
                    is TranscribeResult.Failure -> {
                        if (result.reason is AiFailure.RateLimited || result.reason is AiFailure.AuthError) {
                            geminiCooldownUntilMs = nowMs() + rateLimitCooldownMs
                        }
                        if (!fallback.isAvailable()) {
                            _state.value = ComposerSpeechUiState(
                                SpeechStatus.ERROR,
                                message = result.reason.userMessage
                            )
                            return@launch
                        }
                        usedFallback = true
                        _state.value = ComposerSpeechUiState(
                            SpeechStatus.TRANSCRIBING_FALLBACK, fallbackBadgeVisible = true
                        )
                        fallback.recognize()
                    }
                }
                if (text.isNullOrBlank()) {
                    _state.value = ComposerSpeechUiState(
                        SpeechStatus.ERROR,
                        fallbackBadgeVisible = usedFallback,
                        message = "Couldn't transcribe that. Try again."
                    )
                } else {
                    onTranscript(text)
                    _state.value = ComposerSpeechUiState(fallbackBadgeVisible = usedFallback)
                }
                if (usedFallback) scheduleBadgeClear()
            } finally {
                activeTranscriptCallback = null
                audio.file.delete()
            }
        }
    }

    private suspend fun startLiveFallback(onTranscript: (String) -> Unit) {
        badgeJob?.cancel()
        _state.value = ComposerSpeechUiState(SpeechStatus.TRANSCRIBING_FALLBACK, fallbackBadgeVisible = true)
        try {
            val text = fallback.recognize()
            if (text.isNullOrBlank()) {
                _state.value = ComposerSpeechUiState(
                    SpeechStatus.ERROR,
                    fallbackBadgeVisible = true,
                    message = "Couldn't hear anything. Try again."
                )
            } else {
                onTranscript(text)
                _state.value = ComposerSpeechUiState(fallbackBadgeVisible = true)
            }
            scheduleBadgeClear()
        } finally {
            activeTranscriptCallback = null
        }
    }

    private fun scheduleBadgeClear() {
        badgeJob?.cancel()
        badgeJob = scope.launch {
            delay(badgeVisibleMs)
            _state.value = _state.value.copy(fallbackBadgeVisible = false)
        }
    }
}
