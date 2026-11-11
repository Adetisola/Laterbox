package com.laterbox.app.media.data.adapters.ytdlp

import com.laterbox.app.media.domain.model.AcquisitionSource
import com.laterbox.app.media.domain.model.FailureCode
import com.laterbox.app.media.domain.model.InspectionResult
import com.laterbox.app.media.domain.model.MediaCandidate
import com.laterbox.app.media.domain.model.MediaType
import org.json.JSONArray
import org.json.JSONObject

object YtDlpOutputParser {

    fun parse(originalUrl: String, jsonStr: String): InspectionResult {
        return try {
            val root = JSONObject(jsonStr)

            val platform = root.optString("extractor_key").ifBlank {
                root.optString("extractor").ifBlank { "yt-dlp" }
            }
            val title = root.optString("title").takeIf { it.isNotBlank() }
            val thumbnailUrl = root.optString("thumbnail").takeIf { it.isNotBlank() }

            val candidates = mutableListOf<MediaCandidate>()
            val entriesArray = root.optJSONArray("entries")

            if (entriesArray != null && entriesArray.length() > 0) {
                // Multi-item / carousel / playlist post
                val totalEntries = entriesArray.length()
                val entryObjects = (0 until totalEntries).mapNotNull { entriesArray.optJSONObject(it) }

                var imageIndex = 0
                var videoIndex = 0

                for ((idx, entryJson) in entryObjects.withIndex()) {
                    val entryId = entryJson.optString("id").ifBlank { "item_$idx" }
                    val entryFormats = parseFormats(entryJson.optJSONArray("formats") ?: JSONArray())
                    val videoFormats = entryFormats.filter { it.vcodec != null }

                    if (videoFormats.isNotEmpty()) {
                        videoIndex++
                        val labelSuffix = if (totalEntries > 1) " (Video $videoIndex)" else ""
                        addVideoCandidates(
                            candidates = candidates,
                            formats = entryFormats,
                            originalUrl = originalUrl,
                            id = entryId,
                            durationMs = parseDurationMs(entryJson),
                            labelSuffix = labelSuffix
                        )
                    } else {
                        // Image candidate extraction (multi-image posts, Instagram carousels, Reddit gallery, Twitter photos)
                        val imageUrl = extractBestImageUrl(entryJson, entryFormats)
                        if (imageUrl != null) {
                            imageIndex++
                            val (w, h) = extractDimensions(entryJson, entryFormats)
                            val label = if (totalEntries > 1) "Image $imageIndex of $totalEntries" else "Image (Original)"
                            candidates.add(
                                MediaCandidate(
                                    candidateId = "ytdlp_image_${entryId}_$idx",
                                    mediaType = MediaType.IMAGE,
                                    formatLabel = label,
                                    estimatedBytes = null,
                                    durationMs = null,
                                    width = w,
                                    height = h,
                                    source = AcquisitionSource.DirectHttp(url = imageUrl)
                                )
                            )
                        }
                    }
                }

                // If no audio candidate was added yet and formats exist, add audio from first entry
                if (candidates.none { it.mediaType == MediaType.AUDIO }) {
                    val firstEntry = entryObjects.firstOrNull()
                    if (firstEntry != null) {
                        val firstFormats = parseFormats(firstEntry.optJSONArray("formats") ?: JSONArray())
                        val audioFormats = firstFormats.filter { it.acodec != null }
                        if (audioFormats.isNotEmpty()) {
                            val firstId = firstEntry.optString("id").ifBlank { "media" }
                            addAudioCandidate(candidates, audioFormats, originalUrl, firstId, parseDurationMs(firstEntry))
                        }
                    }
                }
            } else {
                // Single item post
                val id = root.optString("id").ifBlank { "media" }
                val formats = parseFormats(root.optJSONArray("formats") ?: JSONArray())
                val videoFormats = formats.filter { it.vcodec != null }
                val audioFormats = formats.filter { it.acodec != null }
                val durationMs = parseDurationMs(root)

                if (videoFormats.isNotEmpty()) {
                    addVideoCandidates(candidates, formats, originalUrl, id, durationMs)
                }

                if (audioFormats.isNotEmpty()) {
                    addAudioCandidate(candidates, audioFormats, originalUrl, id, durationMs)
                }

                // Differentiate video thumbnails from original images:
                // Only treat thumbnails / image formats as an IMAGE candidate if:
                // 1) There are explicit standalone image formats with image extensions, OR
                // 2) There are no video formats and no audio formats (e.g. photo posts on Instagram/X/Reddit)
                val explicitImageFormats = formats.filter { it.vcodec == null && it.acodec == null && isImageExt(it.ext) }
                if (explicitImageFormats.isNotEmpty()) {
                    val bestImg = explicitImageFormats.maxWithOrNull(
                        compareBy<ParsedFormat> { (it.width ?: 0) * (it.height ?: 0) }
                            .thenBy { it.filesize ?: 0L }
                    )
                    val imgUrl = bestImg?.url ?: root.optString("url").takeIf { it.isNotBlank() }
                    if (imgUrl != null && bestImg != null) {
                        candidates.add(
                            MediaCandidate(
                                candidateId = "ytdlp_image_$id",
                                mediaType = MediaType.IMAGE,
                                formatLabel = "Image (Original)",
                                estimatedBytes = bestImg.filesize,
                                durationMs = null,
                                width = bestImg.width,
                                height = bestImg.height,
                                source = AcquisitionSource.DirectHttp(url = imgUrl)
                            )
                        )
                    }
                } else if (videoFormats.isEmpty() && audioFormats.isEmpty()) {
                    val bestThumb = extractBestThumbnail(root)
                    if (bestThumb != null) {
                        candidates.add(
                            MediaCandidate(
                                candidateId = "ytdlp_image_$id",
                                mediaType = MediaType.IMAGE,
                                formatLabel = "Image (Original)",
                                estimatedBytes = null,
                                durationMs = null,
                                width = bestThumb.width,
                                height = bestThumb.height,
                                source = AcquisitionSource.DirectHttp(url = bestThumb.url)
                            )
                        )
                    }
                }
            }

            if (candidates.isEmpty()) {
                InspectionResult.Unsupported("No downloadable media formats found for this link")
            } else {
                InspectionResult.Available(
                    platform = platform,
                    title = title,
                    thumbnailUrl = thumbnailUrl,
                    candidates = candidates
                )
            }
        } catch (e: Exception) {
            InspectionResult.Failed(
                code = FailureCode.EXTRACTOR_BROKEN,
                message = e.localizedMessage ?: "Failed to parse yt-dlp metadata output",
                retryable = false
            )
        }
    }

    /**
     * Attaches an OpenGraph fallback preview image candidate if original extraction failed or found no formats.
     */
    fun attachPreviewImageFallback(
        result: InspectionResult,
        previewImageUrl: String?,
        platform: String = "Web"
    ): InspectionResult {
        if (previewImageUrl.isNullOrBlank() || !previewImageUrl.startsWith("http")) {
            return result
        }
        val ogCandidate = MediaCandidate(
            candidateId = "og_preview_${System.currentTimeMillis()}",
            mediaType = MediaType.IMAGE,
            formatLabel = "Preview image (Open Graph fallback)",
            estimatedBytes = null,
            durationMs = null,
            width = null,
            height = null,
            source = AcquisitionSource.DirectHttp(url = previewImageUrl),
            isOgFallback = true
        )

        return when (result) {
            is InspectionResult.Available -> {
                if (result.candidates.isEmpty()) {
                    result.copy(candidates = listOf(ogCandidate))
                } else {
                    result
                }
            }
            is InspectionResult.Unsupported,
            is InspectionResult.Failed,
            is InspectionResult.NeedsAuth -> {
                InspectionResult.Available(
                    platform = platform,
                    title = "Web Preview",
                    thumbnailUrl = previewImageUrl,
                    candidates = listOf(ogCandidate)
                )
            }
            is InspectionResult.MetadataOnly -> {
                InspectionResult.Available(
                    platform = platform,
                    title = result.title,
                    thumbnailUrl = previewImageUrl,
                    artist = result.artist,
                    candidates = listOf(ogCandidate)
                )
            }
        }
    }

    private fun addVideoCandidates(
        candidates: MutableList<MediaCandidate>,
        formats: List<ParsedFormat>,
        originalUrl: String,
        id: String,
        durationMs: Long?,
        labelSuffix: String = ""
    ) {
        val videoFormats = formats.filter { it.vcodec != null }
        val audioFormats = formats.filter { it.acodec != null }
        if (videoFormats.isEmpty()) return

        val bestVideo = videoFormats.maxWithOrNull(
            compareBy<ParsedFormat> { it.height ?: 0 }
                .thenBy { it.tbr ?: 0.0 }
                .thenBy { it.filesize ?: 0L }
        )

        val bestAudio = audioFormats.maxWithOrNull(
            compareBy<ParsedFormat> { it.tbr ?: 0.0 }
                .thenBy { it.filesize ?: 0L }
        )

        val maxHeight = bestVideo?.height
        val maxWidth = bestVideo?.width
        val qualityLabel = if (maxHeight != null) "${maxHeight}p mp4" else "Best Video (mp4)"

        val estimatedVideoBytes = when {
            bestVideo?.filesize != null && bestAudio?.filesize != null -> {
                if (bestVideo.acodec != null) bestVideo.filesize else bestVideo.filesize + bestAudio.filesize
            }
            bestVideo?.filesize != null -> bestVideo.filesize
            else -> null
        }

        candidates.add(
            MediaCandidate(
                candidateId = "ytdlp_best_video_$id",
                mediaType = MediaType.VIDEO,
                formatLabel = "$qualityLabel (Best)$labelSuffix",
                estimatedBytes = estimatedVideoBytes,
                durationMs = durationMs,
                width = maxWidth,
                height = maxHeight,
                source = AcquisitionSource.YtDlp(
                    url = originalUrl,
                    formatSelector = "bestvideo[ext=mp4]+bestaudio[ext=m4a]/bestvideo+bestaudio/best[ext=mp4]/best",
                    expectedExt = "mp4"
                )
            )
        )

        // 2. Standard / Data-Saver Video (only if best video > 480p)
        if (maxHeight != null && maxHeight > 480) {
            val saverVideo = videoFormats.filter { (it.height ?: 0) in 1..480 }
                .maxByOrNull { it.height ?: 0 }

            val saverBytes = when {
                saverVideo?.filesize != null && bestAudio?.filesize != null -> {
                    if (saverVideo.acodec != null) saverVideo.filesize else saverVideo.filesize + bestAudio.filesize
                }
                saverVideo?.filesize != null -> saverVideo.filesize
                else -> null
            }

            val saverHeight = saverVideo?.height ?: 480
            candidates.add(
                MediaCandidate(
                    candidateId = "ytdlp_saver_video_$id",
                    mediaType = MediaType.VIDEO,
                    formatLabel = "480p mp4 (Data Saver)$labelSuffix",
                    estimatedBytes = saverBytes,
                    durationMs = durationMs,
                    width = saverVideo?.width ?: 854,
                    height = saverHeight,
                    source = AcquisitionSource.YtDlp(
                        url = originalUrl,
                        formatSelector = "bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=480]+bestaudio/best[height<=480][vcodec!=none]/best[height<=480]/best",
                        expectedExt = "mp4"
                    )
                )
            )
        }
    }

    private fun addAudioCandidate(
        candidates: MutableList<MediaCandidate>,
        audioFormats: List<ParsedFormat>,
        originalUrl: String,
        id: String,
        durationMs: Long?
    ) {
        val bestAudio = audioFormats.maxWithOrNull(
            compareBy<ParsedFormat> { it.tbr ?: 0.0 }
                .thenBy { it.filesize ?: 0L }
        )

        candidates.add(
            MediaCandidate(
                candidateId = "ytdlp_audio_$id",
                mediaType = MediaType.AUDIO,
                formatLabel = "Audio (m4a)",
                estimatedBytes = bestAudio?.filesize,
                durationMs = durationMs,
                width = null,
                height = null,
                source = AcquisitionSource.YtDlp(
                    url = originalUrl,
                    formatSelector = "bestaudio[ext=m4a]/bestaudio/best",
                    expectedExt = "m4a"
                )
            )
        )
    }

    private fun parseFormats(formatsArray: JSONArray): List<ParsedFormat> {
        val list = mutableListOf<ParsedFormat>()
        for (i in 0 until formatsArray.length()) {
            val fObj = formatsArray.optJSONObject(i) ?: continue
            val fId = fObj.optString("format_id")
            val ext = fObj.optString("ext")
            val url = fObj.optString("url").takeIf { it.isNotBlank() }
            val vcodec = fObj.optString("vcodec").takeIf { it.isNotBlank() && it != "none" }
            val acodec = fObj.optString("acodec").takeIf { it.isNotBlank() && it != "none" }
            val width = fObj.optInt("width").takeIf { it > 0 }
            val height = fObj.optInt("height").takeIf { it > 0 }
            val filesize = fObj.optLong("filesize").takeIf { it > 0 }
                ?: fObj.optLong("filesize_approx").takeIf { it > 0 }
            val tbr = fObj.optDouble("tbr").takeIf { !it.isNaN() && it > 0 }

            list.add(
                ParsedFormat(
                    formatId = fId,
                    ext = ext,
                    url = url,
                    vcodec = vcodec,
                    acodec = acodec,
                    width = width,
                    height = height,
                    filesize = filesize,
                    tbr = tbr
                )
            )
        }
        return list
    }

    private fun parseDurationMs(json: JSONObject): Long? {
        val durationSec = json.optDouble("duration", 0.0)
        return if (durationSec > 0) (durationSec * 1000).toLong() else null
    }

    private fun isImageExt(ext: String?): Boolean {
        if (ext.isNullOrBlank()) return false
        return ext.lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif")
    }

    private fun extractBestImageUrl(json: JSONObject, formats: List<ParsedFormat>): String? {
        // 1. Look for explicit image format with a direct URL
        val imageFormats = formats.filter { isImageExt(it.ext) && !it.url.isNullOrBlank() }
        if (imageFormats.isNotEmpty()) {
            val best = imageFormats.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }
            if (best?.url != null) return best.url
        }

        // 2. Look for highest-resolution thumbnail
        val bestThumb = extractBestThumbnail(json)
        if (bestThumb != null) return bestThumb.url

        // 3. Fallback to direct url property if present and looks like image
        val directUrl = json.optString("url").takeIf { it.isNotBlank() }
        if (directUrl != null && (isImageExt(directUrl.substringAfterLast(".", "")) || directUrl.contains("image"))) {
            return directUrl
        }

        return json.optString("thumbnail").takeIf { it.isNotBlank() }
    }

    private fun extractDimensions(json: JSONObject, formats: List<ParsedFormat>): Pair<Int?, Int?> {
        val imageFormats = formats.filter { isImageExt(it.ext) && it.width != null && it.height != null }
        if (imageFormats.isNotEmpty()) {
            val best = imageFormats.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }
            if (best?.width != null && best.height != null) {
                return Pair(best.width, best.height)
            }
        }
        val bestThumb = extractBestThumbnail(json)
        if (bestThumb != null && bestThumb.width != null && bestThumb.height != null) {
            return Pair(bestThumb.width, bestThumb.height)
        }
        val w = json.optInt("width").takeIf { it > 0 }
        val h = json.optInt("height").takeIf { it > 0 }
        return Pair(w, h)
    }

    private fun extractBestThumbnail(json: JSONObject): ThumbnailInfo? {
        val thumbnailsArray = json.optJSONArray("thumbnails") ?: return null
        var bestUrl: String? = null
        var bestWidth: Int? = null
        var bestHeight: Int? = null
        var maxDim = 0

        for (i in 0 until thumbnailsArray.length()) {
            val tObj = thumbnailsArray.optJSONObject(i) ?: continue
            val tUrl = tObj.optString("url").takeIf { it.isNotBlank() } ?: continue
            val w = tObj.optInt("width", 0)
            val h = tObj.optInt("height", 0)
            val dim = if (w > 0 && h > 0) w * h else 1
            if (dim >= maxDim) {
                maxDim = dim
                bestUrl = tUrl
                bestWidth = if (w > 0) w else null
                bestHeight = if (h > 0) h else null
            }
        }

        return if (bestUrl != null) ThumbnailInfo(bestUrl, bestWidth, bestHeight) else null
    }

    fun parseError(errorMessage: String): InspectionResult {
        val lower = errorMessage.lowercase()
        val code = parseFailureCode(lower)

        return when (code) {
            FailureCode.YOUTUBE_CHALLENGE_ERROR -> InspectionResult.Failed(
                code = FailureCode.YOUTUBE_CHALLENGE_ERROR,
                message = "YouTube requested a bot verification check (challenge). Try again later.",
                retryable = false
            )
            FailureCode.LOGIN_REQUIRED -> InspectionResult.NeedsAuth(
                reason = "Login or account authentication required to access this media."
            )
            FailureCode.UNSUPPORTED_URL -> InspectionResult.Unsupported(
                reason = "No supported extractor or downloadable media found at this URL."
            )
            else -> InspectionResult.Failed(
                code = code,
                message = errorMessage.trim(),
                retryable = code.isRetryable
            )
        }
    }

    fun parseFailureCode(errorText: String): FailureCode {
        val lower = errorText.lowercase()
        return when {
            lower.contains("private") || lower.contains("members-only") ->
                FailureCode.PRIVATE_CONTENT

            lower.contains("confirm you're not a robot") || lower.contains("confirm you’re not a robot") ||
                lower.contains("sign in to confirm you're not a bot") || lower.contains("bot verification") ||
                lower.contains("captcha") || lower.contains("challenge") ||
                lower.contains("sign in to confirm your age") ||
                (lower.contains("youtube") && lower.contains("sign in to confirm")) ->
                FailureCode.YOUTUBE_CHALLENGE_ERROR

            lower.contains("linkedin") && (lower.contains("unable to extract video") || lower.contains("cannot extract") || lower.contains("failed to extract")) ->
                FailureCode.EXTRACTOR_BROKEN

            lower.contains("connection timed out") || lower.contains("socket timed out") ||
                lower.contains("socket timeout") || lower.contains("sockettimeout") ||
                lower.contains("connection refused") || lower.contains("connect timed out") ->
                FailureCode.CONNECTION_TIMEOUT

            lower.contains("extraction timed out") || lower.contains("operation timed out") ||
                lower.contains("read timed out") || lower.contains("timed out") ->
                FailureCode.EXTRACTION_TIMEOUT

            lower.contains("traceback (most recent call last)") || lower.contains("modulenotfounderror") ||
                lower.contains("importerror") || lower.contains("syntaxerror") || lower.contains("python runtime") ->
                FailureCode.PYTHON_RUNTIME_ERROR

            lower.contains("sign in") || lower.contains("login required") ||
                lower.contains("authentication required") || lower.contains("account required") ->
                FailureCode.LOGIN_REQUIRED

            lower.contains("georestricted") || lower.contains("not available in your country") ||
                lower.contains("blocked in your country") || lower.contains("geo-blocked") ||
                lower.contains("geographic") ->
                FailureCode.GEO_BLOCKED

            lower.contains("too many requests") || lower.contains("http error 429") ||
                lower.contains("rate limit") ->
                FailureCode.RATE_LIMITED

            lower.contains("unsupported url") || lower.contains("no video formats found") ->
                FailureCode.UNSUPPORTED_URL

            lower.contains("extractorerror") || lower.contains("unable to extract") ->
                FailureCode.EXTRACTOR_BROKEN

            lower.contains("unable to download webpage") || lower.contains("temporary failure in name resolution") ||
                lower.contains("network is unreachable") || lower.contains("no internet") ->
                FailureCode.NETWORK_ERROR

            else -> FailureCode.UNKNOWN
        }
    }

    private data class ParsedFormat(
        val formatId: String,
        val ext: String,
        val url: String? = null,
        val vcodec: String?,
        val acodec: String?,
        val width: Int?,
        val height: Int?,
        val filesize: Long?,
        val tbr: Double?
    )

    private data class ThumbnailInfo(
        val url: String,
        val width: Int?,
        val height: Int?
    )
}
