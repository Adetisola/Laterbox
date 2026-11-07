package com.laterbox.app.domain.util

import java.util.regex.Pattern

data class ParsedSharedContent(
    val url: String,
    val title: String?,
    val note: String?
)

object SharedTextParser {

    private val URL_PATTERN = Pattern.compile(
        "https?://[a-zA-Z0-9.-]+(?:\\.[a-zA-Z]{2,})+(?::\\d+)?(?:/[^\\s\"<>)\\]]*)?",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Parses raw incoming shared text (from Android ACTION_SEND) and subject.
     */
    fun parse(sharedText: String?, sharedSubject: String?): ParsedSharedContent? {
        if (sharedText.isNullOrBlank()) return null

        val matcher = URL_PATTERN.matcher(sharedText)
        if (!matcher.find()) return null

        var rawUrl = matcher.group()
        // Strip trailing punctuation often appended by human text or messaging apps
        rawUrl = rawUrl.trimEnd('.', ',', ')', ']', ';', '!')

        // Extract surrounding text for note/title if present
        val beforeUrl = sharedText.substring(0, matcher.start()).trim()
        val afterUrl = sharedText.substring(matcher.end()).trim()

        val titleCandidate = when {
            !sharedSubject.isNullOrBlank() -> sharedSubject.trim()
            beforeUrl.isNotEmpty() && beforeUrl.length < 80 -> beforeUrl
            afterUrl.isNotEmpty() && afterUrl.length < 80 -> afterUrl
            else -> null
        }

        val noteCandidate = when {
            beforeUrl.isNotEmpty() && beforeUrl != titleCandidate -> beforeUrl
            afterUrl.isNotEmpty() && afterUrl != titleCandidate -> afterUrl
            else -> null
        }

        return ParsedSharedContent(
            url = rawUrl,
            title = titleCandidate,
            note = noteCandidate
        )
    }
}
