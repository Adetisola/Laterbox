package com.laterbox.app.data.metadata

import com.laterbox.app.domain.model.ContentType
import com.laterbox.app.domain.util.UrlNormalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MetadataFetchException(
    message: String,
    val httpStatusCode: Int? = null,
    val retryAfterSeconds: Long? = null,
    cause: Throwable? = null
) : Exception(message, cause)

object JsoupMetadataExtractor {

    private const val BROWSER_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
    private const val CRAWLER_USER_AGENT = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"
    private const val TIMEOUT_MILLIS = 8000
    private const val OEMBED_TIMEOUT_MILLIS = 4000
    private const val MAX_BODY_BYTES = 1024 * 1024 // 1 MB cap

    private val GENERIC_PLACEHOLDER_TITLES = setOf(
        "instagram",
        "login • instagram",
        "reddit",
        "reddit - dive into anything",
        "x",
        "x (formerly twitter)",
        "twitter",
        "facebook",
        "log into facebook",
        "log in or sign up to view",
        "threads",
        "tiktok",
        "tiktok - make your day",
        "just a moment...",
        "attention required! | cloudflare",
        "access denied"
    )

    suspend fun extract(url: String): Result<ExtractedMetadata> = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        val validUrl = if (!trimmedUrl.startsWith("http://", ignoreCase = true) &&
            !trimmedUrl.startsWith("https://", ignoreCase = true)
        ) {
            "https://$trimmedUrl"
        } else {
            trimmedUrl
        }

        val initialDomain = UrlNormalizer.extractDomain(validUrl)

        // 1. Optional, non-fatal platform oEmbed enrichment
        val oembedMetadata: ExtractedMetadata? = try {
            fetchPlatformOEmbed(validUrl, initialDomain)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        }

        // 2. Primary HTML extraction
        val useCrawlerFirst = isSocialDomain(initialDomain)
        val primaryUa = if (useCrawlerFirst) CRAWLER_USER_AGENT else BROWSER_USER_AGENT

        var primaryMetadata: ExtractedMetadata? = null
        var primaryError: Throwable? = null

        try {
            val doc = fetchDocument(validUrl, primaryUa)
            primaryMetadata = parseDocument(doc, validUrl)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            primaryError = e
        }

        // 3. Fallback HTML extraction if primary lacked a preview image or title, or failed with a non-permanent error
        var fallbackMetadata: ExtractedMetadata? = null
        val needsFallback = primaryMetadata == null ||
            primaryMetadata.previewImageUrl == null ||
            !isMeaningfulTitle(primaryMetadata.title, initialDomain)

        val isPermanentHttpError = (primaryError as? HttpStatusException)?.statusCode == 404

        if (needsFallback && !isPermanentHttpError) {
            val alternateUa = if (useCrawlerFirst) BROWSER_USER_AGENT else CRAWLER_USER_AGENT
            try {
                val fallbackDoc = fetchDocument(validUrl, alternateUa)
                fallbackMetadata = parseDocument(fallbackDoc, validUrl)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                // Ignore alternate UA failure
            }

            // If both failed to connect and URL lacked www., try www. prefix
            if (primaryMetadata == null && fallbackMetadata == null) {
                val hasWww = validUrl.contains("://www.", ignoreCase = true)
                if (!hasWww) {
                    try {
                        val prefix = if (validUrl.startsWith("https://", ignoreCase = true)) "https://" else "http://"
                        val withWww = prefix + "www." + validUrl.substring(prefix.length)
                        val wwwDoc = fetchDocument(withWww, BROWSER_USER_AGENT)
                        fallbackMetadata = parseDocument(wwwDoc, withWww)
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (_: Exception) {
                        // Retain original primaryError
                    }
                }
            }
        }

        // 4. Merge all available sources field-by-field: Primary HTML -> Fallback HTML -> oEmbed
        var merged: ExtractedMetadata? = null
        if (primaryMetadata != null) {
            merged = primaryMetadata
        }
        if (fallbackMetadata != null) {
            merged = if (merged != null) {
                mergeMetadata(merged, fallbackMetadata, initialDomain)
            } else {
                fallbackMetadata
            }
        }
        if (oembedMetadata != null) {
            merged = if (merged != null) {
                // Merge so rich HTML fields and oEmbed fields complement each other without overwriting good data
                mergeMetadata(merged, oembedMetadata, initialDomain)
            } else {
                oembedMetadata
            }
        }

        if (merged != null) {
            return@withContext Result.success(merged)
        }

        val finalException = when (val err = primaryError) {
            is HttpStatusException -> MetadataFetchException(
                message = err.message ?: "HTTP ${err.statusCode}",
                httpStatusCode = err.statusCode,
                cause = err
            )
            else -> err ?: MetadataFetchException("Unable to extract metadata")
        }
        Result.failure(finalException)
    }

    /**
     * Merges [primary] and [secondary] metadata field-by-field so useful fields in [primary]
     * are never overwritten by empty or inferior fields in [secondary].
     */
    fun mergeMetadata(
        primary: ExtractedMetadata,
        secondary: ExtractedMetadata,
        domain: String = UrlNormalizer.extractDomain(primary.siteName ?: "")
    ): ExtractedMetadata {
        val resolvedTitle = when {
            isMeaningfulTitle(primary.title, domain) -> primary.title
            isMeaningfulTitle(secondary.title, domain) -> secondary.title
            !primary.title.isNullOrBlank() -> primary.title
            !secondary.title.isNullOrBlank() -> secondary.title
            else -> domain
        }

        val resolvedDescription = when {
            !primary.description.isNullOrBlank() -> primary.description
            !secondary.description.isNullOrBlank() -> secondary.description
            else -> null
        }

        val resolvedSiteName = when {
            !primary.siteName.isNullOrBlank() && !primary.siteName.equals(domain, ignoreCase = true) -> primary.siteName
            !secondary.siteName.isNullOrBlank() && !secondary.siteName.equals(domain, ignoreCase = true) -> secondary.siteName
            !primary.siteName.isNullOrBlank() -> primary.siteName
            else -> secondary.siteName
        }

        val resolvedPreviewImage = primary.previewImageUrl?.takeIf { isValidHttpUrl(it) }
            ?: secondary.previewImageUrl?.takeIf { isValidHttpUrl(it) }

        val defaultFavicon = "https://$domain/favicon.ico"
        val resolvedFavicon = when {
            !primary.faviconUrl.isNullOrBlank() && primary.faviconUrl != defaultFavicon -> primary.faviconUrl
            !secondary.faviconUrl.isNullOrBlank() && secondary.faviconUrl != defaultFavicon -> secondary.faviconUrl
            !primary.faviconUrl.isNullOrBlank() -> primary.faviconUrl
            else -> secondary.faviconUrl
        }

        val resolvedContentType = if (primary.contentType != ContentType.WEBSITE) {
            primary.contentType
        } else {
            secondary.contentType
        }

        return ExtractedMetadata(
            title = resolvedTitle,
            description = resolvedDescription,
            siteName = resolvedSiteName,
            faviconUrl = resolvedFavicon,
            previewImageUrl = resolvedPreviewImage,
            contentType = resolvedContentType
        )
    }

    fun isMeaningfulTitle(title: String?, domain: String): Boolean {
        if (title.isNullOrBlank()) return false
        val normalized = title.trim().lowercase()
        if (normalized == domain.lowercase() || normalized == "www.${domain.lowercase()}") return false
        if (normalized in GENERIC_PLACEHOLDER_TITLES) return false
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) return false
        return true
    }

    private fun fetchDocument(url: String, userAgent: String): Document {
        return Jsoup.connect(url)
            .userAgent(userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .timeout(TIMEOUT_MILLIS)
            .maxBodySize(MAX_BODY_BYTES)
            .followRedirects(true)
            .ignoreHttpErrors(false)
            .get()
    }

    /**
     * Optional, independent oEmbed metadata provider for platforms with documented public endpoints.
     * Any failure returns null and never interrupts HTML extraction.
     */
    internal fun fetchPlatformOEmbed(url: String, domain: String): ExtractedMetadata? {
        val lowerDomain = domain.lowercase()
        val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.name())

        val oembedEndpoint = when {
            lowerDomain.contains("youtube.com") || lowerDomain.contains("youtu.be") ->
                "https://www.youtube.com/oembed?url=$encodedUrl&format=json"
            lowerDomain.contains("reddit.com") || lowerDomain.contains("redd.it") ->
                "https://www.reddit.com/oembed?url=$encodedUrl"
            lowerDomain.contains("tiktok.com") ->
                "https://www.tiktok.com/oembed?url=$encodedUrl"
            lowerDomain.contains("twitter.com") || lowerDomain.contains("x.com") ->
                "https://publish.twitter.com/oembed?url=$encodedUrl&omit_script=true"
            else -> return null
        }

        val response = Jsoup.connect(oembedEndpoint)
            .userAgent(BROWSER_USER_AGENT)
            .ignoreContentType(true)
            .timeout(OEMBED_TIMEOUT_MILLIS)
            .maxBodySize(256 * 1024)
            .execute()

        if (response.statusCode() !in 200..299) return null
        return parseOEmbedJson(response.body(), url, domain)
    }

    internal fun parseOEmbedJson(jsonBody: String, originalUrl: String, domain: String): ExtractedMetadata? {
        return try {
            val obj = JSONObject(jsonBody)
            val rawTitle = obj.optString("title").trim()
            val authorName = obj.optString("author_name").trim()
            val providerName = obj.optString("provider_name").trim()
            val thumbnailUrl = obj.optString("thumbnail_url").trim()
            val embedHtml = obj.optString("html").trim()

            // For X/Twitter or oEmbeds that return blockquote HTML without a title field
            var extractedHtmlText: String? = null
            if (embedHtml.isNotEmpty()) {
                val embedDoc = Jsoup.parse(embedHtml)
                val parsedText = embedDoc.selectFirst("p")?.text()?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: embedDoc.text().trim()
                if (parsedText.isNotEmpty()) {
                    extractedHtmlText = parsedText
                }
            }

            val finalTitle = when {
                rawTitle.isNotEmpty() -> cleanText(rawTitle)
                !extractedHtmlText.isNullOrEmpty() -> {
                    val prefix = if (authorName.isNotEmpty()) "$authorName: " else ""
                    val snippet = if (extractedHtmlText.length > 100) extractedHtmlText.take(97) + "..." else extractedHtmlText
                    cleanText("$prefix$snippet")
                }
                authorName.isNotEmpty() -> cleanText(authorName)
                else -> domain
            }

            val finalDescription = when {
                !extractedHtmlText.isNullOrEmpty() && rawTitle.isNotEmpty() -> cleanText(extractedHtmlText)
                !extractedHtmlText.isNullOrEmpty() -> cleanText(extractedHtmlText)
                authorName.isNotEmpty() -> "By ${cleanText(authorName)}"
                else -> null
            }

            val finalImage = thumbnailUrl
                .takeIf { it.isNotEmpty() }
                ?.let { resolveAbsoluteUrl(originalUrl, it) }
                ?.takeIf { isValidHttpUrl(it) && !isLikelyTrackingOrTinyAsset(it, null, null) }

            val finalSiteName = when {
                providerName.isNotEmpty() -> cleanText(providerName)
                authorName.isNotEmpty() -> cleanText(authorName)
                else -> domain
            }

            val contentType = when {
                domain.contains("youtube.com") || domain.contains("youtu.be") || domain.contains("tiktok.com") ->
                    ContentType.VIDEO
                domain.contains("reddit.com") || domain.contains("redd.it") ||
                    domain.contains("twitter.com") || domain.contains("x.com") ->
                    ContentType.DISCUSSION
                else -> ContentType.WEBSITE
            }

            ExtractedMetadata(
                title = finalTitle,
                description = finalDescription,
                siteName = finalSiteName,
                faviconUrl = "https://$domain/favicon.ico",
                previewImageUrl = finalImage,
                contentType = contentType
            )
        } catch (_: Exception) {
            null
        }
    }

    fun parseDocument(doc: Document, baseUrl: String): ExtractedMetadata {
        val effectiveUrl = doc.location().ifBlank { baseUrl }
        val domain = UrlNormalizer.extractDomain(effectiveUrl)
        val jsonLd = parseJsonLdMetadata(doc, effectiveUrl)

        // Title Extraction: og:title -> twitter:title -> JSON-LD headline/name -> <title> -> domain fallback
        val ogTitle = doc.selectFirst("meta[property=og:title], meta[name=og:title]")?.attr("content")?.trim()
        val twitterTitle = doc.selectFirst("meta[name=twitter:title], meta[property=twitter:title]")?.attr("content")?.trim()
        val docTitle = doc.title().trim()

        val titleCandidates = listOfNotNull(ogTitle, twitterTitle, jsonLd.title, docTitle)
            .map { cleanText(it) }
            .filter { it.isNotBlank() }

        val title = titleCandidates.firstOrNull { isMeaningfulTitle(it, domain) }
            ?: titleCandidates.firstOrNull()
            ?: domain

        // Description Extraction: og:description -> twitter:description -> meta description -> JSON-LD description
        val ogDesc = doc.selectFirst("meta[property=og:description], meta[name=og:description]")?.attr("content")?.trim()
        val twitterDesc = doc.selectFirst("meta[name=twitter:description], meta[property=twitter:description]")?.attr("content")?.trim()
        val metaDesc = doc.selectFirst("meta[name=description], meta[property=description]")?.attr("content")?.trim()
        val description = listOfNotNull(ogDesc, twitterDesc, metaDesc, jsonLd.description)
            .map { cleanText(it) }
            .firstOrNull { it.isNotBlank() }

        // Site Name: og:site_name -> JSON-LD publisher -> application-name -> domain
        val ogSiteName = doc.selectFirst("meta[property=og:site_name], meta[name=og:site_name]")?.attr("content")?.trim()
        val appName = doc.selectFirst("meta[name=application-name]")?.attr("content")?.trim()
        val siteName = listOfNotNull(ogSiteName, jsonLd.siteName, appName)
            .map { cleanText(it) }
            .firstOrNull { it.isNotBlank() }
            ?: domain

        // Preview Image: Multi-candidate collection & scoring
        val previewImageUrl = selectBestPreviewImage(doc, effectiveUrl, jsonLd.imageCandidates)

        // Favicon: <link rel="icon"> / <link rel="apple-touch-icon"> -> default /favicon.ico
        val iconElem = doc.selectFirst("link[rel~=(?i)^apple-touch-icon.*]")
            ?: doc.selectFirst("link[rel~=(?i)^(shortcut )?icon$]")
        val rawFavicon = iconElem?.absUrl("href")?.ifBlank { iconElem.attr("href") }
        val resolvedFavicon = rawFavicon?.let { resolveAbsoluteUrl(effectiveUrl, it) }
        val faviconUrl = if (!resolvedFavicon.isNullOrBlank() && isValidHttpUrl(resolvedFavicon)) {
            resolvedFavicon
        } else {
            "https://$domain/favicon.ico"
        }

        // Content Type Classification
        val contentType = classifyContentType(doc, effectiveUrl, domain, jsonLd.schemaType)

        return ExtractedMetadata(
            title = title,
            description = description,
            siteName = siteName,
            faviconUrl = faviconUrl,
            previewImageUrl = previewImageUrl,
            contentType = contentType
        )
    }

    internal data class ImageCandidate(
        val url: String,
        val width: Int? = null,
        val height: Int? = null,
        val sourcePriority: Int = 0
    )

    internal data class JsonLdExtracted(
        val title: String? = null,
        val description: String? = null,
        val siteName: String? = null,
        val schemaType: String? = null,
        val imageCandidates: List<ImageCandidate> = emptyList()
    )

    private fun selectBestPreviewImage(
        doc: Document,
        effectiveUrl: String,
        jsonLdImages: List<ImageCandidate>
    ): String? {
        val candidates = mutableListOf<ImageCandidate>()

        val globalOgWidth = doc.selectFirst("meta[property=og:image:width], meta[name=og:image:width]")
            ?.attr("content")?.trim()?.toIntOrNull()
        val globalOgHeight = doc.selectFirst("meta[property=og:image:height], meta[name=og:image:height]")
            ?.attr("content")?.trim()?.toIntOrNull()

        // 1. OpenGraph image tags
        val ogSelectors = listOf(
            "meta[property=og:image:secure_url]",
            "meta[property=og:image]",
            "meta[name=og:image]",
            "meta[property=og:image:url]"
        )
        for (selector in ogSelectors) {
            for (elem in doc.select(selector)) {
                val raw = elem.absUrl("content").ifBlank { elem.attr("content") }
                val resolved = resolveAbsoluteUrl(effectiveUrl, raw)
                if (isValidHttpUrl(resolved)) {
                    candidates.add(
                        ImageCandidate(
                            url = resolved,
                            width = globalOgWidth,
                            height = globalOgHeight,
                            sourcePriority = 100
                        )
                    )
                }
            }
        }

        // 2. Twitter image tags
        val twitterSelectors = listOf(
            "meta[name=twitter:image]",
            "meta[name=twitter:image:src]",
            "meta[property=twitter:image]",
            "meta[property=twitter:image:src]"
        )
        for (selector in twitterSelectors) {
            for (elem in doc.select(selector)) {
                val raw = elem.absUrl("content").ifBlank { elem.attr("content") }
                val resolved = resolveAbsoluteUrl(effectiveUrl, raw)
                if (isValidHttpUrl(resolved)) {
                    candidates.add(ImageCandidate(url = resolved, sourcePriority = 90))
                }
            }
        }

        // 3. JSON-LD image candidates
        candidates.addAll(jsonLdImages.filter { isValidHttpUrl(it.url) })

        // 4. Schema itemprop=image and link rel=image_src
        for (elem in doc.select("meta[itemprop=image]")) {
            val raw = elem.absUrl("content").ifBlank { elem.attr("content") }
            val resolved = resolveAbsoluteUrl(effectiveUrl, raw)
            if (isValidHttpUrl(resolved)) {
                candidates.add(ImageCandidate(url = resolved, sourcePriority = 70))
            }
        }
        for (elem in doc.select("link[rel=image_src]")) {
            val raw = elem.absUrl("href").ifBlank { elem.attr("href") }
            val resolved = resolveAbsoluteUrl(effectiveUrl, raw)
            if (isValidHttpUrl(resolved)) {
                candidates.add(ImageCandidate(url = resolved, sourcePriority = 65))
            }
        }

        if (candidates.isEmpty()) return null

        // Filter out obvious tracking pixels, spacer gifs, and tiny icons
        val viable = candidates.filterNot { isLikelyTrackingOrTinyAsset(it.url, it.width, it.height) }
        if (viable.isEmpty()) return null

        // Score and pick the best candidate
        return viable.maxByOrNull { scoreImageCandidate(it) }?.url
    }

    private fun scoreImageCandidate(candidate: ImageCandidate): Int {
        var score = candidate.sourcePriority
        val lower = candidate.url.lowercase()

        if (lower.startsWith("https://")) score += 10
        val w = candidate.width ?: 0
        val h = candidate.height ?: 0
        if (w >= 400 && h >= 200) score += 30
        else if (w >= 200 && h >= 200) score += 15

        // Deprioritize SVG logos if raster images exist
        if (lower.endsWith(".svg") || lower.contains(".svg?")) score -= 25
        if (lower.contains("logo") || lower.contains("favicon") || lower.contains("avatar")) score -= 15

        return score
    }

    private fun isLikelyTrackingOrTinyAsset(url: String, width: Int?, height: Int?): Boolean {
        if (width != null && width in 1..63) return true
        if (height != null && height in 1..63) return true
        val lower = url.lowercase()
        if (lower.endsWith(".ico")) return true
        val blockedPatterns = listOf(
            "/pixel",
            "1x1",
            "spacer.gif",
            "blank.gif",
            "transparent.gif",
            "/beacon",
            "analytics",
            "doubleclick.net",
            "facebook.com/tr"
        )
        return blockedPatterns.any { lower.contains(it) }
    }

    internal fun parseJsonLdMetadata(doc: Document, baseUrl: String): JsonLdExtracted {
        var title: String? = null
        var description: String? = null
        var siteName: String? = null
        var schemaType: String? = null
        val images = mutableListOf<ImageCandidate>()

        val scripts = doc.select("script[type=application/ld+json]")
        for (script in scripts) {
            val rawJson = script.data().trim()
            if (rawJson.isEmpty()) continue
            try {
                val root = JSONTokener(rawJson).nextValue()
                val objects = mutableListOf<JSONObject>()
                collectJsonObjects(root, objects)

                for (obj in objects) {
                    val typeVal = obj.optString("@type").trim()
                    if (schemaType == null && typeVal.isNotEmpty() &&
                        !typeVal.equals("BreadcrumbList", ignoreCase = true) &&
                        !typeVal.equals("WebSite", ignoreCase = true) &&
                        !typeVal.equals("Organization", ignoreCase = true)
                    ) {
                        schemaType = typeVal
                    }

                    if (title == null) {
                        val headline = obj.optString("headline").trim()
                            .ifEmpty { obj.optString("name").trim() }
                        if (headline.isNotEmpty() && !typeVal.equals("Organization", ignoreCase = true) &&
                            !typeVal.equals("WebSite", ignoreCase = true) &&
                            !typeVal.equals("Person", ignoreCase = true)
                        ) {
                            title = headline
                        }
                    }

                    if (description == null) {
                        val desc = obj.optString("description").trim()
                        if (desc.isNotEmpty()) {
                            description = desc
                        }
                    }

                    if (siteName == null) {
                        val publisher = obj.opt("publisher")
                        if (publisher is JSONObject) {
                            val pubName = publisher.optString("name").trim()
                            if (pubName.isNotEmpty()) {
                                siteName = pubName
                            }
                        } else if (typeVal.equals("WebSite", ignoreCase = true)) {
                            val webName = obj.optString("name").trim()
                            if (webName.isNotEmpty()) {
                                siteName = webName
                            }
                        }
                    }

                    if (obj.has("image")) {
                        extractJsonLdImagesFromNode(obj.opt("image"), baseUrl, 85, images)
                    }
                    if (obj.has("thumbnailUrl")) {
                        extractJsonLdImagesFromNode(obj.opt("thumbnailUrl"), baseUrl, 80, images)
                    }
                }
            } catch (_: Exception) {
                // Malformed JSON-LD block; skip safely and continue
            }
        }

        return JsonLdExtracted(
            title = title,
            description = description,
            siteName = siteName,
            schemaType = schemaType,
            imageCandidates = images
        )
    }

    private fun collectJsonObjects(node: Any?, out: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> {
                out.add(node)
                if (node.has("@graph")) {
                    collectJsonObjects(node.opt("@graph"), out)
                }
                if (node.has("mainEntity")) {
                    collectJsonObjects(node.opt("mainEntity"), out)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectJsonObjects(node.opt(i), out)
                }
            }
        }
    }

    private fun extractJsonLdImagesFromNode(
        node: Any?,
        baseUrl: String,
        priority: Int,
        out: MutableList<ImageCandidate>
    ) {
        when (node) {
            is String -> {
                val resolved = resolveAbsoluteUrl(baseUrl, node)
                if (isValidHttpUrl(resolved)) {
                    out.add(ImageCandidate(url = resolved, sourcePriority = priority))
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    extractJsonLdImagesFromNode(node.opt(i), baseUrl, priority, out)
                }
            }
            is JSONObject -> {
                val rawUrl = node.optString("url").trim()
                    .ifEmpty { node.optString("contentUrl").trim() }
                if (rawUrl.isNotEmpty()) {
                    val resolved = resolveAbsoluteUrl(baseUrl, rawUrl)
                    val width = node.optInt("width", -1).takeIf { it > 0 }
                    val height = node.optInt("height", -1).takeIf { it > 0 }
                    if (isValidHttpUrl(resolved)) {
                        out.add(
                            ImageCandidate(
                                url = resolved,
                                width = width,
                                height = height,
                                sourcePriority = priority
                            )
                        )
                    }
                }
            }
        }
    }

    private fun isSocialDomain(domain: String): Boolean {
        val d = domain.lowercase()
        return d.contains("instagram.com") || d.contains("facebook.com") ||
            d.contains("threads.net") || d.contains("tiktok.com") ||
            d.contains("twitter.com") || d.contains("x.com")
    }

    private fun cleanText(text: String): String {
        return Parser.unescapeEntities(text, false)
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun classifyContentType(
        doc: Document,
        url: String,
        domain: String,
        schemaType: String? = null
    ): ContentType {
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
        if (lowerDomain.contains("reddit.com") || lowerDomain.contains("redd.it") ||
            lowerDomain.contains("news.ycombinator.com") ||
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

        // 4. OpenGraph og:type and JSON-LD @type inspection
        val ogType = doc.selectFirst("meta[property=og:type], meta[name=og:type]")?.attr("content")?.lowercase()
        val lowerSchema = schemaType?.lowercase()
        when {
            ogType == "article" || lowerSchema?.contains("article") == true || lowerSchema?.contains("blogposting") == true ->
                return ContentType.ARTICLE
            ogType?.startsWith("video") == true || lowerSchema?.contains("videoobject") == true ->
                return ContentType.VIDEO
            ogType == "product" || lowerSchema?.contains("product") == true ->
                return ContentType.PRODUCT
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

    internal fun isValidHttpUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return false
        }
        return try {
            val uri = URI(trimmed)
            val host = uri.host
            !host.isNullOrBlank() && host.contains(".")
        } catch (_: Exception) {
            false
        }
    }

    internal fun resolveAbsoluteUrl(baseUrl: String, relativeOrAbsolute: String): String {
        val trimmed = relativeOrAbsolute.trim()
        if (trimmed.isEmpty()) return ""
        val lower = trimmed.lowercase()
        if (lower.startsWith("data:") || lower.startsWith("javascript:") || lower.startsWith("blob:") || lower.startsWith("mailto:")) {
            return ""
        }
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return trimmed
        }
        if (trimmed.startsWith("//")) {
            return "https:$trimmed"
        }
        return try {
            val baseUri = URI(baseUrl)
            baseUri.resolve(trimmed).toString()
        } catch (_: Exception) {
            ""
        }
    }
}
