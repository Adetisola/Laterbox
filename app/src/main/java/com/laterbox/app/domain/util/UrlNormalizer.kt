package com.laterbox.app.domain.util

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object UrlNormalizer {

    private val TRACKING_PARAMS = setOf(
        "utm_source",
        "utm_medium",
        "utm_campaign",
        "utm_term",
        "utm_content",
        "fbclid",
        "gclid",
        "si",
        "feature",
        "igshid",
        "msclkid",
        "mc_cid",
        "mc_eid"
    )

    private val MOBILE_SUBDOMAINS_MAP = mapOf(
        "m.youtube.com" to "youtube.com",
        "m.wikipedia.org" to "wikipedia.org",
        "mobile.twitter.com" to "twitter.com"
    )

    /**
     * Conservatively normalizes a URL for duplicate detection.
     * The original URL should always be preserved for opening the link.
     */
    fun normalize(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ""

        val withScheme = if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            "https://$trimmed"
        } else {
            trimmed
        }

        return try {
            val uri = URI(withScheme)
            val scheme = "https" // Normalize http to https for comparison

            var host = (uri.host ?: "").lowercase()
            if (host.startsWith("www.")) {
                host = host.removePrefix("www.")
            }
            // Normalize whitelisted mobile subdomains only
            host = MOBILE_SUBDOMAINS_MAP[host] ?: host

            var path = uri.rawPath ?: ""
            var query = uri.rawQuery ?: ""

            // Handle YouTube short links (youtu.be/ID -> youtube.com/watch?v=ID)
            if (host == "youtu.be" && path.isNotEmpty()) {
                val videoId = path.removePrefix("/").substringBefore("/")
                host = "youtube.com"
                path = "/watch"
                val existingParams = parseQueryParams(query)
                    .filterKeys { it != "v" }
                    .toMutableMap()
                existingParams["v"] = listOf(videoId)
                query = buildQueryString(existingParams)
            } else if (host == "youtube.com" || host == "www.youtube.com") {
                host = "youtube.com"
            }

            // Normalize path trailing slash (except root)
            if (path.length > 1 && path.endsWith("/")) {
                path = path.dropLast(1)
            } else if (path == "/") {
                path = ""
            }

            // Filter out tracking parameters & sort query params
            val filteredQuery = if (query.isNotEmpty()) {
                val params = parseQueryParams(query)
                    .filterKeys { it.lowercase() !in TRACKING_PARAMS }
                buildQueryString(params)
            } else {
                ""
            }

            // Handle fragments: preserve SPA routing (fragments starting with /)
            val fragment = uri.rawFragment
            val preservedFragment = if (fragment != null && (fragment.startsWith("/") || fragment.startsWith("!/"))) {
                "#$fragment"
            } else {
                ""
            }

            val finalPath = if (path.isEmpty() && preservedFragment.isNotEmpty()) "/" else path
            val queryPart = if (filteredQuery.isNotEmpty()) "?$filteredQuery" else ""
            val portPart = if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""

            "$scheme://$host$portPart$finalPath$queryPart$preservedFragment"
        } catch (e: Exception) {
            // Fallback: lowercase and strip basic tracking if URI parsing fails
            trimmed.lowercase()
        }
    }

    /**
     * Extracts a clean, human-readable domain (e.g. "youtube.com", "github.com").
     */
    fun extractDomain(url: String): String {
        return try {
            val trimmed = url.trim()
            val withScheme = if (!trimmed.startsWith("http://", ignoreCase = true) &&
                !trimmed.startsWith("https://", ignoreCase = true)
            ) {
                "https://$trimmed"
            } else {
                trimmed
            }
            val uri = URI(withScheme)
            var host = (uri.host ?: "").lowercase()
            if (host.startsWith("www.")) {
                host = host.removePrefix("www.")
            }
            host.ifEmpty { "unknown" }
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun parseQueryParams(rawQuery: String): Map<String, List<String>> {
        val result = mutableMapOf<String, MutableList<String>>()
        if (rawQuery.isEmpty()) return result

        val pairs = rawQuery.split("&")
        for (pair in pairs) {
            if (pair.isEmpty()) continue
            val idx = pair.indexOf("=")
            val key = if (idx > 0) pair.substring(0, idx) else pair
            val value = if (idx > 0 && pair.length > idx + 1) pair.substring(idx + 1) else ""
            val decodedKey = urlDecode(key)
            val decodedValue = urlDecode(value)
            result.getOrPut(decodedKey) { mutableListOf() }.add(decodedValue)
        }
        return result
    }

    private fun buildQueryString(params: Map<String, List<String>>): String {
        if (params.isEmpty()) return ""
        val sortedKeys = params.keys.sorted()
        val parts = mutableListOf<String>()
        for (key in sortedKeys) {
            val values = params[key]?.sorted() ?: emptyList()
            for (value in values) {
                parts.add(if (value.isNotEmpty()) "$key=$value" else key)
            }
        }
        return parts.joinToString("&")
    }

    private fun urlDecode(str: String): String {
        return try {
            URLDecoder.decode(str, StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            str
        }
    }
}
