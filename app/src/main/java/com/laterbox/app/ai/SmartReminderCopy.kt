package com.laterbox.app.ai

import com.laterbox.app.domain.model.SavedLink

enum class ReminderOrigin {
    MANUAL,
    SMART_DIGEST
}

data class SmartReminderCopy(
    val title: String,
    val body: String,
    val isAiGenerated: Boolean = false,
    val origin: ReminderOrigin = ReminderOrigin.SMART_DIGEST
)

sealed interface AiCopyResult {
    data class Success(val copy: SmartReminderCopy) : AiCopyResult
    data class Failure(val reason: AiFailure) : AiCopyResult
}

sealed interface AiFailure {
    val userMessage: String

    data object NoApiKey : AiFailure {
        override val userMessage: String = "No API key configured"
    }

    data object Offline : AiFailure {
        override val userMessage: String = "Network connection unavailable"
    }

    data object Timeout : AiFailure {
        override val userMessage: String = "Request timed out"
    }

    data class RateLimited(val code: Int = 429) : AiFailure {
        override val userMessage: String = "Rate limited by AI provider ($code)"
    }

    data class AuthError(val code: Int) : AiFailure {
        override val userMessage: String = "Invalid API key or unauthorized ($code)"
    }

    data class ServerError(val code: Int, val details: String? = null) : AiFailure {
        override val userMessage: String = if (details.isNullOrBlank()) {
            "AI server error ($code)"
        } else {
            "AI server error ($code): $details"
        }
    }

    data object EmptyResponse : AiFailure {
        override val userMessage: String = "AI returned an empty response"
    }

    data object MalformedResponse : AiFailure {
        override val userMessage: String = "AI returned unparseable content"
    }
}

object PlainReminderCopy {
    fun forManual(link: SavedLink): SmartReminderCopy {
        val title = if (link.title.isNotBlank()) {
            link.title.take(40)
        } else {
            link.domain.ifBlank { "Saved Link" }
        }

        val body = if (!link.personalNote.isNullOrBlank()) {
            link.personalNote.take(120)
        } else if (link.title.isNotBlank() && link.domain.isNotBlank()) {
            "Reminder for ${link.title} • ${link.domain}"
        } else {
            "Reminder you scheduled in Laterbox"
        }

        return SmartReminderCopy(
            title = title,
            body = body,
            isAiGenerated = false,
            origin = ReminderOrigin.MANUAL
        )
    }
}
