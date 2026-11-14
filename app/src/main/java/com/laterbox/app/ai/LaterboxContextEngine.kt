package com.laterbox.app.ai

import com.laterbox.app.data.local.LaterboxDatabase
import com.laterbox.app.data.local.entity.SavedLinkWithCollection
import com.laterbox.app.data.preferences.UserPreferencesRepository
import com.laterbox.app.data.repository.ActivityEventRepository
import com.laterbox.app.diagnostics.AppHealthChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class BuiltAssistantContext(
    val systemInstruction: String,
    val contextBlock: String
)

class LaterboxContextEngine(
    private val database: LaterboxDatabase,
    private val activityEventRepository: ActivityEventRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val appHealthChecker: AppHealthChecker? = null
) {

    suspend fun buildContextForPrompt(prompt: String): BuiltAssistantContext = withContext(Dispatchers.IO) {
        val prefs = userPreferencesRepository.userPreferencesFlow.first()
        val keywords = extractKeywords(prompt)
        val lowerPrompt = prompt.lowercase()

        val linkDao = database.savedLinkDao()
        val inboxCount = try { linkDao.getInboxCount() } catch (_: Throwable) { 0 }
        val archivedCount = try { linkDao.getArchivedCount() } catch (_: Throwable) { 0 }
        val candidateLinks = try {
            val recent = linkDao.getRecentInboxLinksSnapshot(limit = 20)
            if (keywords.isEmpty()) recent
            else {
                val matched = keywords.take(4).flatMap { kw ->
                    linkDao.searchInboxLinksSnapshot(kw, limit = 10)
                }
                (matched + recent).distinctBy { it.link.id }
            }
        } catch (_: Throwable) {
            emptyList()
        }

        val rankedLinks = rankRelevantLinks(candidateLinks, keywords, maxItems = 10)
        val inspections = try {
            database.mediaInspectionDao().getRecentInspectionsSnapshot(15)
        } catch (_: Throwable) {
            emptyList()
        }
        val downloadTasks = try {
            database.downloadTaskDao().getRecentTasksSnapshot(15)
        } catch (_: Throwable) {
            emptyList()
        }
        val downloadedAssetCount = try {
            database.downloadedAssetDao().getAssetCount().first()
        } catch (_: Throwable) {
            0
        }
        val recentNotifications = try {
            database.notificationHistoryDao().getRecentHistorySnapshot(8)
        } catch (_: Throwable) {
            emptyList()
        }
        val recentEvents = try {
            activityEventRepository.getRecentEvents(15)
        } catch (_: Throwable) {
            emptyList()
        }

        val asksAboutHealthOrDiagnostics = listOf(
            "health", "diagnostic", "yt-dlp", "ytdlp", "ffmpeg", "quickjs",
            "fail", "error", "broken", "slow", "storage", "permission", "download", "inspect"
        ).any { lowerPrompt.contains(it) }

        val healthReport = if (asksAboutHealthOrDiagnostics && appHealthChecker != null) {
            try {
                appHealthChecker.runCheck()
            } catch (_: Throwable) {
                null
            }
        } else null

        val asksForDeepContent = listOf(
            "transcript", "full article", "summarize the video", "what did they say",
            "read the whole", "word for word", "entire page", "full text"
        ).any { lowerPrompt.contains(it) }

        val contextBlock = buildString {
            appendLine("=== LATERBOX USER CONTEXT SNAPSHOT ===")
            if (prefs.userName.isNotBlank()) {
                appendLine("User name: ${sanitize(prefs.userName, 40)}")
            }
            appendLine("Library summary: $inboxCount inbox links, $archivedCount archived links, $downloadedAssetCount downloaded media files.")

            if (rankedLinks.isNotEmpty()) {
                appendLine("\nRelevant & recent saved links (max ${rankedLinks.size}):")
                for (item in rankedLinks) {
                    val l = item.link
                    val cols = item.collections.joinToString(", ") { it.name }.ifBlank { "None" }
                    val statusFlags = buildList {
                        if (!l.isRead) add("unread") else add("read")
                        if (l.reminderAt != null && !l.nudgeDismissed) add("reminder-set")
                    }.joinToString(", ")
                    append("- [linkId=${l.id}] title=\"${sanitize(l.title, 80)}\" domain=${sanitize(l.domain, 40)} url=${sanitize(l.originalUrl, 120)} type=${l.contentType.name} status=($statusFlags) collections=[$cols]")
                    if (!l.personalNote.isNullOrBlank()) {
                        append(" note=\"${sanitize(l.personalNote, 120)}\"")
                    }
                    if (!l.description.isNullOrBlank()) {
                        append(" desc=\"${sanitize(l.description, 120)}\"")
                    }
                    appendLine()
                }
            }

            if (inspections.isNotEmpty()) {
                val relevantInspections = inspections.filter {
                    it.status != "COMPLETED" || keywords.isEmpty() || rankedLinks.any { rl -> rl.link.id == it.savedLinkId }
                }.take(8)
                if (relevantInspections.isNotEmpty()) {
                    appendLine("\nRecent media inspections:")
                    for (insp in relevantInspections) {
                        append("- linkId=${insp.savedLinkId} status=${insp.status}")
                        if (!insp.errorCode.isNullOrBlank()) append(" code=${insp.errorCode}")
                        if (!insp.errorMessage.isNullOrBlank()) append(" detail=\"${sanitize(insp.errorMessage, 100)}\"")
                        appendLine()
                    }
                }
            }

            if (downloadTasks.isNotEmpty()) {
                val tasksSlice = downloadTasks.take(8)
                appendLine("\nRecent download tasks:")
                for (t in tasksSlice) {
                    append("- taskId=${t.id} linkId=${t.savedLinkId} status=${t.status} type=${t.mediaType} progress=${t.progress}%")
                    if (!t.title.isNullOrBlank()) append(" title=\"${sanitize(t.title, 60)}\"")
                    if (!t.errorMessage.isNullOrBlank()) append(" error=\"${sanitize(t.errorMessage, 100)}\"")
                    appendLine()
                }
            }

            if (recentNotifications.isNotEmpty()) {
                appendLine("\nRecent in-app notifications:")
                for (n in recentNotifications.take(5)) {
                    appendLine("- [${n.origin}] ${sanitize(n.title, 70)}: ${sanitize(n.body, 100)}")
                }
            }

            if (recentEvents.isNotEmpty()) {
                appendLine("\nRecent app activity events:")
                for (ev in recentEvents.take(8)) {
                    append("- [${ev.eventType}] ${sanitize(ev.title, 80)}")
                    if (!ev.detail.isNullOrBlank()) append(" (${sanitize(ev.detail, 100)})")
                    appendLine()
                }
            }

            if (healthReport != null) {
                appendLine("\n${sanitize(healthReport.toHumanSummary(), 1000)}")
            }

            if (asksForDeepContent) {
                appendLine("\nHONESTY REMINDER: Laterbox only stores link title, URL, snippet/description, personal note, and media format metadata — NOT full paywalled article text or video transcripts. Tell the user honestly if the requested content is not stored locally.")
            }
        }.trim()

        val systemInstruction = buildString {
            appendLine("You are the Laterbox Assistant embedded inside the Laterbox Android app.")
            appendLine("Be concise, warm, accurate, and grounded strictly in Laterbox's real features and the provided user context snapshot.")
            appendLine("Never invent saved links, URLs, formats, IDs, or app settings that do not exist.")
            appendLine()
            appendLine(LaterboxKnowledgeBase.FEATURE_GUIDE)
            appendLine()
            appendLine(LaterboxKnowledgeBase.ALLOWED_ACTION_SPEC)
            appendLine()
            appendLine(contextBlock)
        }.trim()

        BuiltAssistantContext(
            systemInstruction = systemInstruction,
            contextBlock = contextBlock
        )
    }

    private fun extractKeywords(prompt: String): Set<String> {
        return prompt.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 && it !in STOP_WORDS }
            .toSet()
    }

    private fun rankRelevantLinks(
        links: List<SavedLinkWithCollection>,
        keywords: Set<String>,
        maxItems: Int
    ): List<SavedLinkWithCollection> {
        if (links.isEmpty()) return emptyList()
        if (keywords.isEmpty()) return links.take(maxItems)

        return links.sortedByDescending { item ->
            val l = item.link
            val haystack = buildString {
                append(l.title.lowercase()).append(' ')
                append(l.domain.lowercase()).append(' ')
                append(l.originalUrl.lowercase()).append(' ')
                append(l.personalNote?.lowercase().orEmpty()).append(' ')
                append(l.description?.lowercase().orEmpty()).append(' ')
                item.collections.forEach { append(it.name.lowercase()).append(' ') }
            }
            var score = 0
            for (kw in keywords) {
                if (haystack.contains(kw)) score += 10
            }
            if (!l.isRead) score += 1
            score
        }.take(maxItems)
    }

    private fun sanitize(raw: String, maxLen: Int): String {
        return ActivityEventRepository.sanitizeText(raw, maxLen)
            .replace("\n", " ")
            .replace("\r", " ")
    }

    companion object {
        private val STOP_WORDS = setOf(
            "the", "and", "for", "with", "that", "this", "what", "when", "where",
            "which", "have", "from", "about", "your", "laterbox", "links", "link",
            "saved", "please", "can", "you", "how", "why", "are", "was", "were"
        )
    }
}
