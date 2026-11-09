package com.laterbox.app.capture

import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Build
import java.security.MessageDigest

data class ClipboardSuggestion(
    val url: String,
    val domain: String,
    val hash: String
)

data class ClipMetadata(
    val text: String?,
    val isSensitive: Boolean,
    val timestampMs: Long?
)

object ClipboardSuggestionManager {

    private const val STALE_CLIP_WINDOW_MS = 12L * 60 * 60 * 1000L // 12 hours
    private const val RETENTION_WINDOW_MS = 7L * 24 * 60 * 60 * 1000L // 7 days

    fun extractClipMetadata(clipboardManager: ClipboardManager?): ClipMetadata? {
        if (clipboardManager == null || !clipboardManager.hasPrimaryClip()) return null

        val clip = clipboardManager.primaryClip ?: return null
        val description = clipboardManager.primaryClipDescription ?: clip.description

        var isSensitive = false
        if (description != null) {
            val extras = description.extras
            if (extras != null) {
                val sensitiveKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ClipDescription.EXTRA_IS_SENSITIVE
                } else {
                    "android.content.extra.IS_SENSITIVE"
                }
                isSensitive = extras.getBoolean(sensitiveKey, false)
            }
        }

        val timestampMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && description != null) {
            val ts = description.timestamp
            if (ts > 0L) ts else null
        } else {
            null
        }

        val item = if (clip.itemCount > 0) clip.getItemAt(0) else return null
        val text = item.text?.toString() ?: item.uri?.toString()

        return ClipMetadata(
            text = text,
            isSensitive = isSensitive,
            timestampMs = timestampMs
        )
    }

    fun evaluateCandidate(
        rawText: String?,
        isSensitive: Boolean,
        clipTimestampMs: Long?,
        nowMs: Long = System.currentTimeMillis(),
        clipboardEnabled: Boolean,
        recentHistoryEntries: Set<String>,
        isAlreadySaved: (String) -> Boolean
    ): ClipboardSuggestion? {
        if (!clipboardEnabled || isSensitive || rawText.isNullOrBlank()) {
            return null
        }

        // Ignore clip if timestamp is available and older than 12 hours
        if (clipTimestampMs != null && clipTimestampMs > 0L) {
            if (nowMs - clipTimestampMs > STALE_CLIP_WINDOW_MS) {
                return null
            }
        }

        val extraction = UrlCaptureExtractor.extract(rawText)
        if (extraction !is UrlExtractionResult.SingleUrl) {
            return null
        }

        val hash = computeHash(extraction.normalizedUrl)

        // Check against recent history within 7-day retention window
        val cutoff = nowMs - RETENTION_WINDOW_MS
        val isRecentlyHandled = recentHistoryEntries.any { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) {
                val entryHash = parts[0]
                val entryTime = parts[1].toLongOrNull() ?: 0L
                entryTime >= cutoff && entryHash == hash
            } else false
        }

        if (isRecentlyHandled) {
            return null
        }

        // Check if the link already exists in the database
        if (isAlreadySaved(extraction.normalizedUrl)) {
            return null
        }

        return ClipboardSuggestion(
            url = extraction.normalizedUrl,
            domain = extraction.domain,
            hash = hash
        )
    }

    fun computeHash(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(text.trim().toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
