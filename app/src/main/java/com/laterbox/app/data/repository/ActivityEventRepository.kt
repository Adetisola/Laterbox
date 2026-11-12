package com.laterbox.app.data.repository

import com.laterbox.app.ai.GeminiClient
import com.laterbox.app.data.local.dao.ActivityEventDao
import com.laterbox.app.data.local.entity.ActivityEventEntity
import kotlinx.coroutines.flow.Flow

enum class ActivityEventType {
    LINK_SAVED,
    INSPECTION_COMPLETED,
    INSPECTION_FAILED,
    DOWNLOAD_COMPLETED,
    DOWNLOAD_FAILED,
    NOTIFICATION_POSTED,
    APP_UPDATED
}

class ActivityEventRepository(
    private val activityEventDao: ActivityEventDao
) {

    fun observeRecentEvents(limit: Int = 30): Flow<List<ActivityEventEntity>> =
        activityEventDao.observeRecentEvents(limit)

    suspend fun getRecentEvents(limit: Int = 30): List<ActivityEventEntity> =
        activityEventDao.getRecentEvents(limit)

    suspend fun recordEvent(
        type: ActivityEventType,
        title: String,
        detail: String? = null,
        linkId: Long? = null,
        createdAt: Long = System.currentTimeMillis()
    ) {
        val sanitizedTitle = sanitizeText(title, maxLen = 120)
        val sanitizedDetail = detail?.let { sanitizeText(it, maxLen = 220) }?.ifBlank { null }
        activityEventDao.insert(
            ActivityEventEntity(
                eventType = type.name,
                title = sanitizedTitle.ifBlank { type.name },
                detail = sanitizedDetail,
                linkId = linkId,
                createdAt = createdAt
            )
        )
        activityEventDao.pruneToLatest(MAX_RETAINED_EVENTS)
    }

    suspend fun clearAll() {
        activityEventDao.clearAll()
    }

    companion object {
        const val MAX_RETAINED_EVENTS = 200

        fun sanitizeText(raw: String, maxLen: Int = 200): String {
            var cleaned = GeminiClient.redactSecrets(raw)
            // Redact Bearer / header tokens or absolute internal app filesystem paths
            cleaned = cleaned.replace(
                Regex("(?i)(authorization|cookie|x-goog-api-key)\\s*[:=]\\s*(?:(?:Bearer|Basic|Token)\\s+)?\\S+"),
                "$1=[REDACTED]"
            )
            cleaned = cleaned.replace(
                Regex("(?i)(Bearer|Basic|Token)\\s+[a-zA-Z0-9_\\-.=]+"),
                "$1 [REDACTED]"
            )
            cleaned = cleaned.replace(Regex("/data/(user/\\d+|data)/[^\\s]+"), "[app-storage]")
            return cleaned.trim().take(maxLen)
        }
    }
}
