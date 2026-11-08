package com.laterbox.app.data.metadata

import com.laterbox.app.domain.model.ContentType
import com.laterbox.app.domain.util.UrlNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.net.URI

object JsoupMetadataExtractor {

    private const val BROWSER_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
    private const val CRAWLER_USER_AGENT = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"
    private const val TIMEOUT_MILLIS = 8000
    private const val MAX_BODY_BYTES = 1024 * 1024 // 1 MB cap

    suspend fun extract(url: String): Result<ExtractedMetadata> = withContext(Dispatchers.IO) {
        try {
            val trimmedUrl = url.trim()
            val validUrl = if (!trimmedUrl.startsWith("http://", ignoreCase = true) &&
                !trimmedUrl.startsWith("https://", ignoreCase = true)
            ) {
                "https://$trimmedUrl"
            } else {
                trimmedUrl
            }

            val initialDomain = UrlNormalizer.extractDomain(validUrl)
            val useCrawlerFirst = isSocialDomain(initialDomain)
            val primaryUa = if (useCrawlerFirst) CRAWLER_USER_AGENT else BROWSER_USER_AGENT

            var doc = fetchDocument(validUrl, primaryUa)
            var metadata = parseDocument(doc, validUrl)

            // If initial fetch yielded no preview image and it's not a known failed page, retry with alternate crawler UA
            if (metadata.previewImageUrl == null && !useCrawlerFirst) {
                try {
                    val fallbackDoc = fetchDocument(validUrl, CRAWLER_USER_AGENT)
                    val fallbackMeta = parseDocument(fallbackDoc, validUrl)
                    if (fallbackMeta.previewImageUrl != null || (metadata.title == initialDomain && fallbackMeta.title != initialDomain)) {
                        metadata = fallbackMeta
                    }
                } catch (_: Exception) {
                    // Retain primary metadata if fallback fails
                }
            }

            Result.success(metadata)
        } catch (e: Exception) {
            // If connection failed and url does not contain www., retry with www.
            val trimmedUrl = url.trim()
            val hasWww = trimmedUrl.contains("://www.") || trimmedUrl.startsWith("www.")
            if (!hasWww) {
                try {
                    val withWww = if (trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://")) {
                        val prefix = if (trimmedUrl.startsWith("https://")) "https://" else "http://"
                        prefix + "www." + trimmedUrl.substring(prefix.length)
                    } else {
                        "https://www.$trimmedUrl"
                    }
                    val doc = fetchDocument(withWww, BROWSER_USER_AGENT)
                    return@withContext Result.success(parseDocument(doc, withWww))
                } catch (_: Exception) {
                    // return original exception
                }
            }
            Result.failure(e)
        }
    }

    private fun fetchDocument(url: String, userAgent: String): Document {
        return Jsoup.connect(url)
            .userAgent(userAgent)
            .header("Accept-Language", "en-US,en;q=0.9")
            .timeout(TIMEOUT_MILLIS)
            .maxBodySize(MAX_BODY_BYTES)
            .followRedirects(true)
            .ignoreHttpErrors(false)
            .get()
    }

    fun parseDocument(doc: Document, baseUrl: String): ExtractedMetadata {
        val effectiveUrl = doc.location().ifBlank { baseUrl }
        val domain = UrlNormalizer.extractDomain(effectiveUrl)

        // Title Extraction: og:title -> twitter:title -> <title> -> domain fallback
        val ogTitle = doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: doc.selectFirst("meta[name=og:title]")?.attr("content")?.trim()
        val twitterTitle = doc.selectFirst("meta[name=twitter:title]")?.attr("content")?.trim()
            ?: doc.selectFirst("meta[property=twitter:title]")?.attr("content")?.trim()
        val docTitle = doc.title().trim()

        val rawTitle = listOfNotNull(ogTitle, twitterTitle, docTitle).firstOrNull { it.isNotBlank() } ?: domain
        val title = cleanText(rawTitle)

        // Description Extraction: og:description -> twitter:description -> meta description
        val ogDesc = doc.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
            ?: doc.selectFirst("meta[name=og:description]")?.attr("content")?.trim()
        val twitterDesc = doc.selectFirst("meta[name=twitter:description]")?.attr("content")?.trim()
            ?: doc.selectFirst("meta[property=twitter:description]")?.attr("content")?.trim()
        val metaDesc = doc.selectFirst("meta[name=description]")?.attr("content")?.trim()
        val rawDescription = listOfNotNull(ogDesc, twitterDesc, metaDesc).firstOrNull { it.isNotBlank() }
        val description = rawDescription?.let { cleanText(it) }

        // Site Name: og:site_name -> domain
        val ogSiteName = doc.selectFirst("meta[property=og:site_name]")?.attr("content")?.trim()
            ?: doc.selectFirst("meta[name=og:site_name]")?.attr("content")?.trim()
        val siteName = if (!ogSiteName.isNullOrBlank()) cleanText(ogSiteName) else domain

        // Preview Image: og:image (and variants) -> twitter:image -> json-ld fallback
        val ogImageElem = doc.selectFirst("meta[property=og:image]")
            ?: doc.selectFirst("meta[name=og:image]")
            ?: doc.selectFirst("meta[property=og:image:url]")
            ?: doc.selectFirst("meta[property=og:image:secure_url]")
            ?: doc.selectFirst("meta[name=twitter:image]")
            ?: doc.selectFirst("meta[name=twitter:image:src]")
            ?: doc.selectFirst("meta[property=twitter:image]")
            ?: doc.selectFirst("meta[itemprop=image]")

        val rawImage = ogImageElem?.absUrl("content")?.ifBlank { ogImageElem.attr("content") }
            ?: extractJsonLdImage(doc)

        val previewImageUrl = rawImage?.let { resolveAbsoluteUrl(effectiveUrl, it) }

        // Favicon: <link rel="icon"> / <link rel="apple-touch-icon"> -> default /favicon.ico
        val iconElem = doc.selectFirst("link[rel~=(?i)^(shortcut )?icon$]")
            ?: doc.selectFirst("link[rel~=(?i)^apple-touch-icon.*]")
        val rawFavicon = iconElem?.absUrl("href")?.ifBlank { iconElem.attr("href") }
        val faviconUrl = if (!rawFavicon.isNullOrBlank()) {
            resolveAbsoluteUrl(effectiveUrl, rawFavicon)
        } else {
            "https://$domain/favicon.ico"
        }

        // Content Type Classification
        val contentType = classifyContentType(doc, effectiveUrl, domain)

        return ExtractedMetadata(
            title = title,
            description = description,
            siteName = siteName,
            faviconUrl = faviconUrl,
            previewImageUrl = previewImageUrl,
            contentType = contentType
        )
    }

    private fun isSocialDomain(domain: String): Boolean {
        val d = domain.lowercase()
        return d.contains("instagram.com") || d.contains("facebook.com") ||
            d.contains("threads.net") || d.contains("tiktok.com")
    }

    private fun cleanText(text: String): String {
        return Parser.unescapeEntities(text, false).trim()
    }

    private fun extractJsonLdImage(doc: Document): String? {
        val scripts = doc.select("script[type=application/ld+json]")
        val imageRegex = Regex("\"image\"\\s*:\\s*\"([^\"]+)\"")
        val thumbnailRegex = Regex("\"thumbnailUrl\"\\s*:\\s*\"([^\"]+)\"")
        for (script in scripts) {
            val data = script.data()
            val match = imageRegex.find(data) ?: thumbnailRegex.find(data)
            if (match != null) {
                val candidate = match.groupValues[1].replace("\\/", "/")
                if (candidate.startsWith("http://") || candidate.startsWith("https://") || candidate.startsWith("/")) {
                    return candidate
                }
            }
        }
        return null
    }

    private fun classifyContentType(doc: Document, url: String, domain: String): ContentType {
        val lowerDomain = domain.lowercase()
        val lowerUrl = url.lowercase()

        // 1. Video domains
        if (lowerDomain.contains("youtube.com") || lowerDomain.contains("youtu.be") ||
            lowerDomain.contains("vimeo.com") || lowerDomain.contains("tiktok.com") ||
            lowerDomain.contains("twitch.tv")
        ) {
            return ContentType.VIDEO
        }

        // 2. Discussion domains
        if (lowerDomain.contains("reddit.com") || lowerDomain.contains("news.ycombinator.com") ||
            lowerDomain.contains("lobste.rs") || lowerDomain.contains("twitter.com") ||
            lowerDomain.contains("x.com") || lowerDomain.contains("threads.net") ||
            lowerDomain.contains("bsky.app") || lowerDomain.contains("instagram.com")
        ) {
            return ContentType.DISCUSSION
        }

        // 3. Product domains
        if (lowerDomain.contains("amazon.") || lowerDomain.contains("ebay.") ||
            lowerDomain.contains("etsy.com") || lowerDomain.contains("shopify.com")
        ) {
            return ContentType.PRODUCT
        }

        // 4. OpenGraph og:type inspection
        val ogType = doc.selectFirst("meta[property=og:type]")?.attr("content")?.lowercase()
        when {
            ogType == "article" -> return ContentType.ARTICLE
            ogType?.startsWith("video") == true -> return ContentType.VIDEO
            ogType == "product" -> return ContentType.PRODUCT
        }

        // 5. HTML structural heuristic (article tag or blog indicators)
        if (doc.selectFirst("article") != null ||
            doc.selectFirst("meta[name=article:published_time]") != null ||
            doc.selectFirst("meta[property=article:published_time]") != null ||
            lowerUrl.contains("/article/") || lowerUrl.contains("/posts/") || lowerUrl.contains("/blog/")
        ) {
            return ContentType.ARTICLE
        }

        return ContentType.WEBSITE
    }

    private fun resolveAbsoluteUrl(baseUrl: String, relativeOrAbsolute: String): String {
        val trimmed = relativeOrAbsolute.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        if (trimmed.startsWith("//")) {
            return "https:$trimmed"
        }
        return try {
            val baseUri = URI(baseUrl)
            baseUri.resolve(trimmed).toString()
        } catch (e: Exception) {
            trimmed
        }
    }
}
