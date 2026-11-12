package com.laterbox.app.ai

import org.json.JSONArray
import org.json.JSONObject

sealed interface ChatAction {
    val label: String

    data class SearchLinks(val query: String) : ChatAction {
        override val label: String get() = "Search \"$query\""
    }

    data class OpenLinkDetail(val linkId: Long, val title: String) : ChatAction {
        override val label: String get() = "Open ${title.take(28)}"
    }

    data class OpenDownloads(val initialTab: Int = 0) : ChatAction {
        override val label: String get() = if (initialTab == 1) "Open download queue" else "Open downloads"
    }

    data object OpenCollections : ChatAction {
        override val label: String get() = "Open collections"
    }

    data class OpenSettings(val route: String = "settings") : ChatAction {
        override val label: String get() = when (route) {
            "settings/reminders_ai" -> "Reminders & AI settings"
            "settings/media_downloads" -> "Media & Downloads settings"
            "settings/capture_shortcuts" -> "Capture & Shortcuts settings"
            "settings/about_privacy" -> "About & Privacy settings"
            "settings/data_storage" -> "Data & Storage settings"
            else -> "Open settings"
        }
    }

    data object RunHealthCheck : ChatAction {
        override val label: String get() = "Run app health check"
    }

    data class RetryInspection(
        val linkId: Long,
        val title: String,
        val requiresConfirmation: Boolean = true
    ) : ChatAction {
        override val label: String get() = "Retry inspection: ${title.take(24)}"
    }

    data class RetryDownload(
        val taskId: String,
        val title: String,
        val requiresConfirmation: Boolean = true
    ) : ChatAction {
        override val label: String get() = "Retry download: ${title.take(24)}"
    }
}

data class ParsedAssistantResponse(
    val displayText: String,
    val actions: List<ChatAction>
)

object AssistantActionHandler {

    private val ALLOWED_SETTINGS_ROUTES = setOf(
        "settings",
        "settings/appearance_profile",
        "settings/reminders_ai",
        "settings/capture_shortcuts",
        "settings/reading_behavior",
        "settings/media_downloads",
        "settings/data_storage",
        "settings/about_privacy"
    )

    private val ACTION_BLOCK_REGEX = Regex(
        """\[ACTIONS\]\s*(.*?)\s*\[/ACTIONS\]""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )

    fun parseModelResponse(rawText: String): ParsedAssistantResponse {
        val match = ACTION_BLOCK_REGEX.find(rawText)
        if (match == null) {
            return ParsedAssistantResponse(
                displayText = rawText.trim(),
                actions = emptyList()
            )
        }

        val jsonPart = match.groupValues[1].trim()
        val displayText = rawText.removeRange(match.range).trim()
        val actions = fromJsonList(jsonPart)
        return ParsedAssistantResponse(
            displayText = displayText,
            actions = actions
        )
    }

    fun fromJsonList(json: String?): List<ChatAction> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val cleaned = json.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val array = JSONArray(cleaned)
            val result = mutableListOf<ChatAction>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                parseAllowlistedAction(obj)?.let { result += it }
            }
            result.distinctBy { it.toString() }.take(4)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun toJsonList(actions: List<ChatAction>): String? {
        if (actions.isEmpty()) return null
        val array = JSONArray()
        for (action in actions) {
            val obj = JSONObject()
            when (action) {
                is ChatAction.SearchLinks -> {
                    obj.put("type", "SEARCH_LINKS")
                    obj.put("query", action.query)
                }
                is ChatAction.OpenLinkDetail -> {
                    obj.put("type", "OPEN_LINK_DETAIL")
                    obj.put("linkId", action.linkId)
                    obj.put("title", action.title)
                }
                is ChatAction.OpenDownloads -> {
                    obj.put("type", "OPEN_DOWNLOADS")
                    obj.put("initialTab", action.initialTab)
                }
                is ChatAction.OpenCollections -> {
                    obj.put("type", "OPEN_COLLECTIONS")
                }
                is ChatAction.OpenSettings -> {
                    obj.put("type", "OPEN_SETTINGS")
                    obj.put("route", action.route)
                }
                is ChatAction.RunHealthCheck -> {
                    obj.put("type", "RUN_HEALTH_CHECK")
                }
                is ChatAction.RetryInspection -> {
                    obj.put("type", "RETRY_INSPECTION")
                    obj.put("linkId", action.linkId)
                    obj.put("title", action.title)
                    obj.put("requiresConfirmation", true)
                }
                is ChatAction.RetryDownload -> {
                    obj.put("type", "RETRY_DOWNLOAD")
                    obj.put("taskId", action.taskId)
                    obj.put("title", action.title)
                    obj.put("requiresConfirmation", true)
                }
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun parseAllowlistedAction(obj: JSONObject): ChatAction? {
        return when (obj.optString("type").trim().uppercase()) {
            "SEARCH_LINKS" -> {
                val q = obj.optString("query").trim().take(80)
                if (q.isNotBlank()) ChatAction.SearchLinks(q) else null
            }
            "OPEN_LINK_DETAIL" -> {
                val linkId = obj.optLong("linkId", -1L)
                val title = obj.optString("title").trim().ifBlank { "Saved link" }.take(60)
                if (linkId > 0L) ChatAction.OpenLinkDetail(linkId, title) else null
            }
            "OPEN_DOWNLOADS" -> {
                val tab = obj.optInt("initialTab", 0).coerceIn(0, 1)
                ChatAction.OpenDownloads(tab)
            }
            "OPEN_COLLECTIONS" -> ChatAction.OpenCollections
            "OPEN_SETTINGS" -> {
                val route = obj.optString("route").trim().ifBlank { "settings" }
                if (route in ALLOWED_SETTINGS_ROUTES) ChatAction.OpenSettings(route) else ChatAction.OpenSettings("settings")
            }
            "RUN_HEALTH_CHECK" -> ChatAction.RunHealthCheck
            "RETRY_INSPECTION" -> {
                val linkId = obj.optLong("linkId", -1L)
                val title = obj.optString("title").trim().ifBlank { "Saved link" }.take(60)
                // Mutating action ALWAYS enforces confirmation gating regardless of model JSON
                if (linkId > 0L) ChatAction.RetryInspection(linkId, title, requiresConfirmation = true) else null
            }
            "RETRY_DOWNLOAD" -> {
                val taskId = obj.optString("taskId").trim().take(80)
                val title = obj.optString("title").trim().ifBlank { "Download task" }.take(60)
                // Mutating action ALWAYS enforces confirmation gating regardless of model JSON
                if (taskId.isNotBlank()) ChatAction.RetryDownload(taskId, title, requiresConfirmation = true) else null
            }
            else -> null
        }
    }
}
