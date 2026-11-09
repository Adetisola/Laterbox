package com.laterbox.app.capture

import com.laterbox.app.domain.util.UrlNormalizer
import java.net.URI

sealed interface UrlExtractionResult {
    data class SingleUrl(
        val normalizedUrl: String,
        val rawUrl: String,
        val domain: String
    ) : UrlExtractionResult

    data class MultipleUrls(
        val urls: List<SingleUrl>
    ) : UrlExtractionResult

    data object NoValidUrl : UrlExtractionResult
}

object UrlCaptureExtractor {

    private val REJECTED_SCHEMES = setOf(
        "javascript:",
        "file:",
        "content:",
        "mailto:",
        "tel:",
        "sms:",
        "geo:",
        "intent:"
    )

    private val COMMON_FILE_EXTENSIONS = setOf(
        "pdf", "png", "jpg", "jpeg", "gif", "svg", "webp",
        "mp3", "mp4", "wav", "m4a", "mov", "avi", "mkv",
        "zip", "tar", "gz", "rar", "7z", "apk",
        "kt", "java", "py", "c", "cpp", "h", "json", "xml",
        "txt", "md", "csv", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "html", "htm", "css", "js", "ts", "log", "sh", "sql", "wasm"
    )

    // Matches http://, https://, www., or host.tld patterns
    private val URL_TOKEN_REGEX = Regex(
        """(?i)\b(?:https?://|www\.)[^\s<>"{}|\\^`]+|\b[a-zA-Z0-9][-a-zA-Z0-9]*(?:\.[a-zA-Z0-9][-a-zA-Z0-9]*)*\.[a-zA-Z]{2,}(?:/[^\s<>"{}|\\^`]*)?"""
    )

    // Matches email pattern
    private val EMAIL_REGEX = Regex("""(?i)[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""")

    // Matches non-http/https URI schemes to avoid treating file:/// or mailto: as URLs
    private val NON_HTTP_SCHEME_REGEX = Regex(
        """(?i)\b(?:javascript|file|content|mailto|tel|sms|geo|intent|about|data|blob|ftp|ftps|ssh|git|svn|magnet):[^\s<>"{}|\\^`]*"""
    )

    // Matches version string like v1.0.12 or 1.2.3
    private val VERSION_REGEX = Regex("""^v?\d+(\.\d+)+$""", RegexOption.IGNORE_CASE)

    fun extract(text: String?): UrlExtractionResult {
        if (text.isNullOrBlank()) return UrlExtractionResult.NoValidUrl

        val candidates = extractCandidateUrls(text)
        return when {
            candidates.isEmpty() -> UrlExtractionResult.NoValidUrl
            candidates.size == 1 -> candidates.first()
            else -> UrlExtractionResult.MultipleUrls(candidates)
        }
    }

    fun extractCandidateUrls(text: String?): List<UrlExtractionResult.SingleUrl> {
        if (text.isNullOrBlank()) return emptyList()

        // Mask out non-http URI schemes (e.g. file:///sdcard/..., mailto:...)
        val textWithoutNonHttp = NON_HTTP_SCHEME_REGEX.replace(text) { match ->
            " ".repeat(match.value.length)
        }

        // Mask out email addresses from search to avoid extracting the domain part of user@domain.com
        val textWithoutEmails = EMAIL_REGEX.replace(textWithoutNonHttp) { match ->
            " ".repeat(match.value.length)
        }

        val found = mutableListOf<UrlExtractionResult.SingleUrl>()
        val seenNormalized = mutableSetOf<String>()

        val matches = URL_TOKEN_REGEX.findAll(textWithoutEmails)
        for (match in matches) {
            val cleaned = cleanCandidate(match.value) ?: continue
            if (!isValidCandidateUrl(cleaned)) continue

            val normalized = UrlNormalizer.normalize(cleaned)
            if (normalized.isBlank() || seenNormalized.contains(normalized)) continue

            val domain = UrlNormalizer.extractDomain(cleaned)
            if (domain.isBlank() || domain == "unknown" || !domain.contains(".")) continue

            seenNormalized.add(normalized)
            found.add(
                UrlExtractionResult.SingleUrl(
                    normalizedUrl = normalized,
                    rawUrl = cleaned,
                    domain = domain
                )
            )
        }

        return found
    }

    fun isValidCandidateUrl(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (trimmed.length < 4) return false

        for (scheme in REJECTED_SCHEMES) {
            if (trimmed.startsWith(scheme, ignoreCase = true)) return false
        }

        if (trimmed.contains("@")) {
            val beforeAt = trimmed.substringBefore("@")
            if (!beforeAt.startsWith("http://", ignoreCase = true) &&
                !beforeAt.startsWith("https://", ignoreCase = true)
            ) {
                return false
            }
        }

        if (VERSION_REGEX.matches(trimmed)) return false

        val hasHttpScheme = trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)

        val hasWww = trimmed.startsWith("www.", ignoreCase = true)

        // If no scheme and no www, check for common file extensions masquerading as domain
        if (!hasHttpScheme && !hasWww) {
            val slashIdx = trimmed.indexOf('/')
            val hostPart = if (slashIdx != -1) trimmed.substring(0, slashIdx) else trimmed
            val ext = hostPart.substringAfterLast('.', "").lowercase()
            if (COMMON_FILE_EXTENSIONS.contains(ext)) {
                return false
            }
            // Must have a valid dot and alphabetic TLD of at least 2 chars
            if (!hostPart.contains('.')) return false
            if (hostPart.all { it.isDigit() || it == '.' || it == '-' }) {
                // Reject pure IP or version numbers if no scheme
                return false
            }
        }

        val urlWithScheme = if (!hasHttpScheme) "https://$trimmed" else trimmed

        return try {
            val uri = URI(urlWithScheme)
            val scheme = uri.scheme?.lowercase()
            val host = uri.host
            if (scheme != "http" && scheme != "https") return false
            if (host.isNullOrBlank() || !host.contains(".")) return false
            // Check TLD
            val tld = host.substringAfterLast('.', "")
            tld.length >= 2 && tld.all { it.isLetter() }
        } catch (_: Exception) {
            false
        }
    }

    private fun cleanCandidate(raw: String): String? {
        var token = raw.trim()

        // Strip trailing surrounding punctuation: . , ; : ! ? " ' > ] }
        val trailingPunctuation = charArrayOf('.', ',', ';', ':', '!', '?', '"', '\'', '>', ']', '}', '»')
        while (token.isNotEmpty() && trailingPunctuation.contains(token.last())) {
            token = token.dropLast(1)
        }

        // Strip leading punctuation: < " ' ( [ { «
        val leadingPunctuation = charArrayOf('<', '"', '\'', '(', '[', '{', '«')
        while (token.isNotEmpty() && leadingPunctuation.contains(token.first())) {
            token = token.drop(1)
        }

        // Balanced parenthesis check for trailing ')'
        // e.g. "https://en.wikipedia.org/wiki/Kotlin_(programming_language)" -> balanced, keep ')'
        // e.g. "(https://example.com/path)" -> unbalanced, strip trailing ')'
        while (token.endsWith(")")) {
            val openCount = token.count { it == '(' }
            val closeCount = token.count { it == ')' }
            if (closeCount > openCount) {
                token = token.dropLast(1)
            } else {
                break
            }
        }

        return token.ifBlank { null }
    }
}
