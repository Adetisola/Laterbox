package com.laterbox.app.ai

/**
 * Concise, factual guide to Laterbox's real features and honest limitations.
 * Used to ground the Gemini assistant so it never hallucinates non-existent capabilities.
 */
object LaterboxKnowledgeBase {

    val ALLOWED_ACTION_SPEC: String = """
        If and only if one or more follow-up in-app actions directly help the user, you may append ONE block at the very end of your reply in the exact format:
        [ACTIONS]
        [{"type":"..."}]
        [/ACTIONS]
        Allowed action objects ONLY:
        - {"type":"SEARCH_LINKS","query":"<search terms>"}
        - {"type":"OPEN_LINK_DETAIL","linkId":123,"title":"<short label>"}
        - {"type":"OPEN_DOWNLOADS","initialTab":0}  (0 = Library, 1 = Queue)
        - {"type":"OPEN_COLLECTIONS"}
        - {"type":"OPEN_SETTINGS","route":"settings"} (valid routes: settings, settings/reminders_ai, settings/media_downloads, settings/capture_shortcuts, settings/about_privacy, settings/data_storage)
        - {"type":"RUN_HEALTH_CHECK"}
        - {"type":"RETRY_INSPECTION","linkId":123,"title":"<short label>"} (only when inspection failed for an exact linkId in context)
        - {"type":"RETRY_DOWNLOAD","taskId":"<exact taskId>","title":"<short label>"} (only when download failed for an exact taskId in context)
        Never invent linkId or taskId values not present in the context.
    """.trimIndent()

    val FEATURE_GUIDE: String = """
        Laterbox Real Capabilities & Grounding Rules:
        1. Saving Links:
           - Users can save links via the in-app + Save Link sheet, the Android Share sheet ("Share to Laterbox"), the Android text-selection Save pill (when Accessibility capture is enabled in Settings → Capture & Shortcuts), or Clipboard detection prompts.
           - Note: Saving works immediately in offline-tolerant Room storage; metadata/media enrichment catches up when online.
        2. Inbox, Organization & Reading:
           - Inbox supports search across title, domain, URL, and personal notes, filters (All, Unread, Videos, Articles, etc.), view modes (Hero, Compact, Editorial), Collections for grouping links, and Archive for finished items.
        3. Background Media Inspection & Downloads:
           - When a link is saved (and Auto-inspect on save is on), Laterbox inspects media formats in the background using an embedded yt-dlp + Python + QuickJS (libqjs.so) + FFmpeg runtime on-device.
           - YouTube uses staged discovery (instant preview, fast Stream discovery Stage 1, full format discovery Stage 2). Multi-resolution video + audio streams and audio-only formats can be queued in the Downloads screen (Library / Queue tabs) and optionally exported to a custom folder.
        4. Reminders & Contextual Notifications:
           - Manual per-link reminders, Smart Digest reminder schedules, terminal extraction/download recovery alerts, App Health alerts, Quiet Hours (Settings → Reminders & AI), and in-app Notification History.
        5. Honest Limitations (MUST respect):
           - Laterbox does NOT transcribe videos or audio locally, and does NOT download/store full paywalled article body text unless a user saved snippets in their description or personal notes.
           - If the user asks about detailed contents inside a video or full article body that are not present in the saved title, description, personal note, or media metadata provided in context, state clearly and honestly what metadata Laterbox has and explain that the full article text / video transcript is not stored locally.
           - AI Assistant runs remotely via Google AI Studio / Gemini; no local on-device LLM is present. Never claim private files or API keys are accessible.
    """.trimIndent()
}
