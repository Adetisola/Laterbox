package com.laterbox.app.data.metadata

import com.laterbox.app.domain.model.ContentType
import com.laterbox.app.domain.util.UrlNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI

object JsoupMetadataExtractor {

    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
    private const val TIMEOUT_MILLIS = 6000
    private const val MAX_BODY_BYTES = 256 * 1024 // 256 KB cap

    suspend fun extract(url: String): Result<ExtractedMetadata> = withContext(Dispatchers.IO) {
        try {
            val trimmedUrl = url.trim()
            val validUrl = if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
                "https://$trimmedUrl"
            } else {
                trimmedUrl
            }

            val doc = Jsoup.connect(validUrl)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MILLIS)
                .maxBodySize(MAX_BODY_BYTES)
                .followRedirects(true)
                .get()

            Result.success(parseDocument(doc, validUrl))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parseDocument(doc: Document, baseUrl: String): ExtractedMetadata {
        val domain = UrlNormalizer.extractDomain(baseUrl)

        // Title Extraction: og:title -> twitter:title -> <title> -> domain fallback
        val ogTitle = doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
        val twitterTitle = doc.selectFirst("meta[name=twitter:title]")?.attr("content")?.trim()
        val docTitle = doc.title().trim()
        val title = listOfNotNull(ogTitle, twitterTitle, docTitle).firstOrNull { it.isNotBlank() } ?: domain

        // Description Extraction: og:description -> twitter:description -> meta description
        val ogDesc = doc.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        val twitterDesc = doc.selectFirst("meta[name=twitter:description]")?.attr("content")?.trim()
        val metaDesc = doc.selectFirst("meta[name=description]")?.attr("content")?.trim()
        val description = listOfNotNull(ogDesc, twitterDesc, metaDesc).firstOrNull { it.isNotBlank() }

        // Site Name: og:site_name -> domain
        val ogSiteName = doc.selectFirst("meta[property=og:site_name]")?.attr("content")?.trim()
        val siteName = if (!ogSiteName.isNullOrBlank()) ogSiteName else domain

        // Preview Image: og:image -> twitter:image (resolve relative to absolute)
        val ogImageElem = doc.selectFirst("meta[property=og:image]")
        val twitterImageElem = doc.selectFirst("meta[name=twitter:image]")
        val rawImage = ogImageElem?.absUrl("content")?.ifBlank { ogImageElem.attr("content") }
            ?: twitterImageElem?.absUrl("content")?.ifBlank { twitterImageElem.attr("content") }
        val previewImageUrl = rawImage?.let { resolveAbsoluteUrl(baseUrl, it) }

        // Favicon: <link rel="icon"> / <link rel="apple-touch-icon"> -> default /favicon.ico
        val iconElem = doc.selectFirst("link[rel~=(?i)^(shortcut )?icon$]")
            ?: doc.selectFirst("link[rel~=(?i)^apple-touch-icon.*]")
        val rawFavicon = iconElem?.absUrl("href")?.ifBlank { iconElem.attr("href") }
        val faviconUrl = if (!rawFavicon.isNullOrBlank()) {
            resolveAbsoluteUrl(baseUrl, rawFavicon)
        } else {
            "https://$domain/favicon.ico"
        }

        // Content Type Classification
        val contentType = classifyContentType(doc, baseUrl, domain)

        return ExtractedMetadata(
            title = title,
            description = description,
            siteName = siteName,
            faviconUrl = faviconUrl,
            previewImageUrl = previewImageUrl,
            contentType = contentType
        )
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
            lowerDomain.contains("bsky.app")
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
