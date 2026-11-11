package com.laterbox.app.media.data.adapters.ytdlp

import com.laterbox.app.media.domain.model.AcquisitionSource
import com.laterbox.app.media.domain.model.CandidateStatus
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
            val extractorVersion = root.optJSONObject("_version")?.optString("version")
                ?: root.optString("_version").takeIf { it.isNotBlank() }

            val candidates = mutableListOf<MediaCandidate>()
            val partialFailures = mutableListOf<String>()
            val entriesArray = root.optJSONArray("entries")

            if (entriesArray != null && entriesArray.length() > 0) {
                // Multi-item / carousel / playlist post
                val totalEntries = entriesArray.length()
                var videoIndex = 0
                var imageIndex = 0

                for (idx in 0 until totalEntries) {
                    val entryJson = entriesArray.optJSONObject(idx)
                    if (entryJson == null) {
                        partialFailures.add("Entry ${idx + 1}: Malformed or empty entry payload")
                        continue
                    }

                    try {
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
                                platform = platform,
                                labelSuffix = labelSuffix
                            )
                        }

                        // Extract image / thumbnail candidates for this entry
                        val entryImagesBefore = candidates.size
                        addExplicitImageCandidates(candidates, entryFormats, entryId, platform)
                        addThumbnailCandidates(
                            candidates = candidates,
                            json = entryJson,
                            id = entryId,
                            platform = platform,
                            isMultiEntry = true,
                            entryIndex = idx + 1,
                            totalEntries = totalEntries
                        )

                        // If neither video nor thumbnail candidate was found, try fallback image url
                        if (videoFormats.isEmpty() && candidates.size == entryImagesBefore) {
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
                                        source = AcquisitionSource.DirectHttp(url = imageUrl),
                                        extractor = platform
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        partialFailures.add("Entry ${idx + 1}: ${e.message ?: "Parsing failed"}")
                    }
                }

                // If no audio candidate was added yet and formats exist, add audio from first entry
                if (candidates.none { it.mediaType == MediaType.AUDIO }) {
                    for (i in 0 until totalEntries) {
                        val entry = entriesArray.optJSONObject(i) ?: continue
                        val entryFormats = parseFormats(entry.optJSONArray("formats") ?: JSONArray())
                        val audioFormats = entryFormats.filter { it.acodec != null && (it.vcodec == null || it.vcodec == "none") }
                        if (audioFormats.isNotEmpty()) {
                            val entryId = entry.optString("id").ifBlank { "media" }
                            addAudioCandidates(candidates, audioFormats, originalUrl, entryId, parseDurationMs(entry), platform)
                            break
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

                // 1. Video candidates across all discovered resolutions
                if (videoFormats.isNotEmpty()) {
                    addVideoCandidates(
                        candidates = candidates,
                        formats = formats,
                        originalUrl = originalUrl,
                        id = id,
                        durationMs = durationMs,
                        platform = platform
                    )
                }

                // 2. Audio candidates
                val audioOnlyFormats = audioFormats.filter { it.vcodec == null || it.vcodec == "none" }
                val targetAudioFormats = if (audioOnlyFormats.isNotEmpty()) {
                    audioOnlyFormats
                } else {
                    audioFormats.filter { it.acodec != null && it.acodec != "none" }
                }
                if (targetAudioFormats.isNotEmpty()) {
                    addAudioCandidates(
                        candidates = candidates,
                        audioFormats = targetAudioFormats,
                        originalUrl = originalUrl,
                        id = id,
                        durationMs = durationMs,
                        platform = platform
                    )
                }

                // 3. Explicit standalone image formats (e.g. photos/graphics exposed in format list)
                addExplicitImageCandidates(candidates, formats, id, platform)

                // 4. Discovered thumbnails (video thumbnails, poster images, photo thumbnails)
                addThumbnailCandidates(
                    candidates = candidates,
                    json = root,
                    id = id,
                    platform = platform,
                    isMultiEntry = false
                )
            }

            if (candidates.isEmpty()) {
                InspectionResult.Unsupported("No downloadable media formats found for this link")
            } else {
                InspectionResult.Available(
                    platform = platform,
                    title = title,
                    thumbnailUrl = thumbnailUrl,
                    candidates = candidates,
                    partialFailures = partialFailures,
                    extractorVersion = extractorVersion
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

    fun attachPreviewImageFallback(
        result: InspectionResult,
        previewImageUrl: String?
    ): InspectionResult {
        if (previewImageUrl.isNullOrBlank()) return result

        val ogCandidate = MediaCandidate(
            candidateId = "og_preview_fallback",
            mediaType = MediaType.IMAGE,
            formatLabel = "Preview image (Open Graph fallback)",
            estimatedBytes = null,
            durationMs = null,
            width = null,
            height = null,
            source = AcquisitionSource.DirectHttp(url = previewImageUrl),
            isOgFallback = true
        )

        val platform = when (result) {
            is InspectionResult.Available -> result.platform
            else -> "Web"
        }

        return when (result) {
            is InspectionResult.Available -> {
                if (result.candidates.none { it.mediaType == MediaType.IMAGE }) {
                    result.copy(candidates = result.candidates + ogCandidate)
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

    /**
     * Discovers all useful video resolutions dynamically from the extractor's real findings.
     * Groups by distinct resolution, selects compatible containers (preferring MP4),
     * constructs reliable format selectors (merging audio streams if needed),
     * and flags missing audio tracks when video has no sound.
     */
    private fun addVideoCandidates(
        candidates: MutableList<MediaCandidate>,
        formats: List<ParsedFormat>,
        originalUrl: String,
        id: String,
        durationMs: Long?,
        platform: String,
        labelSuffix: String = ""
    ) {
        val videoFormats = formats.filter { it.vcodec != null && it.vcodec != "none" }
        if (videoFormats.isEmpty()) return

        val audioFormats = formats.filter { it.acodec != null && it.acodec != "none" }
        val bestAudio = audioFormats.maxWithOrNull(
            compareBy<ParsedFormat> { it.tbr ?: 0.0 }
                .thenBy { it.filesize ?: it.filesizeApprox ?: 0L }
        )

        // Group video formats by distinct resolution
        // Use effective height (for vertical/portrait video, minOf(w, h) or height)
        val groupedByResolution = videoFormats.groupBy { fmt ->
            val h = fmt.height ?: 0
            val w = fmt.width ?: 0
            Pair(h, w)
        }

        // For each resolution bucket, select the best format
        val resolutionCandidates = mutableListOf<ParsedFormat>()
        for ((_, bucket) in groupedByResolution) {
            val bestInBucket = bucket.maxWithOrNull(
                compareBy<ParsedFormat> { if (it.ext.equals("mp4", ignoreCase = true)) 1 else 0 }
                    .thenBy { if (it.acodec != null && it.acodec != "none") 1 else 0 }
                    .thenBy { it.tbr ?: it.vbr ?: 0.0 }
                    .thenBy { it.filesize ?: it.filesizeApprox ?: 0L }
            )
            if (bestInBucket != null) {
                resolutionCandidates.add(bestInBucket)
            }
        }

        // Sort resolutions descending (highest resolution first)
        val sortedFormats = resolutionCandidates.sortedWith(
            compareByDescending<ParsedFormat> { (it.height ?: 0) * (it.width ?: 0) }
                .thenByDescending { it.height ?: 0 }
                .thenByDescending { it.tbr ?: it.vbr ?: 0.0 }
        )

        for ((index, fmt) in sortedFormats.withIndex()) {
            val isBest = index == 0
            val hasDirectAudio = fmt.acodec != null && fmt.acodec != "none"
            val requiresMerge = !hasDirectAudio && audioFormats.isNotEmpty()
            val audioTrackMissing = !hasDirectAudio && audioFormats.isEmpty()

            val formatSelector = if (requiresMerge) {
                "${fmt.formatId}+bestaudio[ext=m4a]/bestaudio/best"
            } else {
                fmt.formatId
            }

            val estimatedBytes = when {
                fmt.filesize != null && requiresMerge && bestAudio?.filesize != null -> fmt.filesize + bestAudio.filesize
                fmt.filesize != null -> fmt.filesize
                fmt.filesizeApprox != null && requiresMerge && (bestAudio?.filesizeApprox != null || bestAudio?.filesize != null) ->
                    fmt.filesizeApprox + (bestAudio.filesize ?: bestAudio.filesizeApprox ?: 0L)
                fmt.filesizeApprox != null -> fmt.filesizeApprox
                else -> null
            }

            val resLabel = if (fmt.height != null && fmt.height > 0) {
                "${fmt.height}p"
            } else if (fmt.width != null && fmt.width > 0) {
                "${fmt.width}w"
            } else {
                "Video"
            }

            val extLabel = fmt.ext.uppercase()
            val bestTag = if (isBest) " (Best)" else ""
            val silentTag = if (audioTrackMissing) " (No Audio)" else ""
            val formatLabel = "$resLabel $extLabel$bestTag$silentTag$labelSuffix"

            candidates.add(
                MediaCandidate(
                    candidateId = "ytdlp_video_${id}_${fmt.formatId}",
                    mediaType = MediaType.VIDEO,
                    formatLabel = formatLabel,
                    estimatedBytes = estimatedBytes,
                    durationMs = durationMs,
                    width = fmt.width,
                    height = fmt.height,
                    source = AcquisitionSource.YtDlp(
                        url = originalUrl,
                        formatSelector = formatSelector,
                        expectedExt = if (requiresMerge) "mp4" else fmt.ext
                    ),
                    formatId = fmt.formatId,
                    ext = if (requiresMerge) "mp4" else fmt.ext,
                    vcodec = fmt.vcodec,
                    acodec = fmt.acodec ?: bestAudio?.acodec,
                    fps = fmt.fps,
                    bitrate = fmt.tbr ?: fmt.vbr,
                    hasVideo = true,
                    hasAudio = hasDirectAudio || requiresMerge,
                    isBest = isBest,
                    isThumbnail = false,
                    qualityLabel = "$resLabel $extLabel",
                    requiresMerge = requiresMerge,
                    audioTrackMissing = audioTrackMissing,
                    status = CandidateStatus.AVAILABLE,
                    statusReason = if (audioTrackMissing) "Video stream contains no audio track" else null,
                    extractor = platform
                )
            )
        }
    }

    /**
     * Extracts distinct audio formats (e.g. M4A, WEBM, MP3) with reliable selectors and sizes.
     */
    private fun addAudioCandidates(
        candidates: MutableList<MediaCandidate>,
        audioFormats: List<ParsedFormat>,
        originalUrl: String,
        id: String,
        durationMs: Long?,
        platform: String
    ) {
        val audioOnly = audioFormats.filter { it.acodec != null && it.acodec != "none" }
        if (audioOnly.isEmpty()) return

        // Group by extension (e.g. m4a, webm, mp3)
        val groupedByExt = audioOnly.groupBy { it.ext.lowercase() }

        for ((ext, formatsWithExt) in groupedByExt) {
            val bestInExt = formatsWithExt.maxWithOrNull(
                compareBy<ParsedFormat> { it.tbr ?: it.abr ?: 0.0 }
                    .thenBy { it.filesize ?: it.filesizeApprox ?: 0L }
            ) ?: continue

            val selector = if (ext == "m4a") {
                "${bestInExt.formatId}/bestaudio[ext=m4a]/bestaudio/best"
            } else {
                "${bestInExt.formatId}/bestaudio/best"
            }

            candidates.add(
                MediaCandidate(
                    candidateId = "ytdlp_audio_${id}_${bestInExt.formatId}",
                    mediaType = MediaType.AUDIO,
                    formatLabel = "Audio (${ext.uppercase()})",
                    estimatedBytes = bestInExt.filesize ?: bestInExt.filesizeApprox,
                    durationMs = durationMs,
                    width = null,
                    height = null,
                    source = AcquisitionSource.YtDlp(
                        url = originalUrl,
                        formatSelector = selector,
                        expectedExt = ext
                    ),
                    formatId = bestInExt.formatId,
                    ext = ext,
                    vcodec = null,
                    acodec = bestInExt.acodec,
                    bitrate = bestInExt.tbr ?: bestInExt.abr,
                    hasVideo = false,
                    hasAudio = true,
                    isBest = false,
                    isThumbnail = false,
                    qualityLabel = "Audio $ext",
                    requiresMerge = false,
                    audioTrackMissing = false,
                    status = CandidateStatus.AVAILABLE,
                    extractor = platform
                )
            )
        }
    }

    /**
     * Extracts explicit image formats (e.g. photo posts on Instagram, Reddit, X).
     */
    private fun addExplicitImageCandidates(
        candidates: MutableList<MediaCandidate>,
        formats: List<ParsedFormat>,
        id: String,
        platform: String
    ) {
        val explicitImageFormats = formats.filter {
            (it.vcodec == null || it.vcodec == "none") &&
                (it.acodec == null || it.acodec == "none") &&
                isImageExt(it.ext) &&
                !it.url.isNullOrBlank()
        }

        for ((idx, imgFmt) in explicitImageFormats.withIndex()) {
            val w = imgFmt.width
            val h = imgFmt.height
            val label = if (w != null && h != null) "Image — $w × $h" else "Image (Original)"
            candidates.add(
                MediaCandidate(
                    candidateId = "ytdlp_explicit_img_${id}_${imgFmt.formatId}_$idx",
                    mediaType = MediaType.IMAGE,
                    formatLabel = label,
                    estimatedBytes = imgFmt.filesize ?: imgFmt.filesizeApprox,
                    durationMs = null,
                    width = w,
                    height = h,
                    source = AcquisitionSource.DirectHttp(url = imgFmt.url!!),
                    formatId = imgFmt.formatId,
                    ext = imgFmt.ext,
                    hasVideo = false,
                    hasAudio = false,
                    isBest = false,
                    isThumbnail = false,
                    qualityLabel = if (w != null && h != null) "$w × $h" else "Original",
                    status = CandidateStatus.AVAILABLE,
                    extractor = platform
                )
            )
        }
    }

    /**
     * Extracts every distinct, meaningful thumbnail variant discovered by yt-dlp.
     * Supports YouTube (long-form, Shorts, youtu.be), Instagram, TikTok, Reddit, etc.
     * Filters out storyboard sprite-sheets.
     * Independent of video/audio candidates.
     */
    private fun addThumbnailCandidates(
        candidates: MutableList<MediaCandidate>,
        json: JSONObject,
        id: String,
        platform: String,
        isMultiEntry: Boolean = false,
        entryIndex: Int = 1,
        totalEntries: Int = 1
    ) {
        val rawThumbnails = mutableListOf<ThumbnailInfo>()

        // 1. Inspect 'thumbnails' array
        val thumbnailsArray = json.optJSONArray("thumbnails")
        if (thumbnailsArray != null) {
            for (i in 0 until thumbnailsArray.length()) {
                val tObj = thumbnailsArray.optJSONObject(i) ?: continue
                val tUrl = tObj.optString("url").takeIf { it.isNotBlank() } ?: continue

                // Filter out storyboard sprite sheets
                val tId = tObj.optString("id")
                val preference = tObj.optInt("preference", 0)
                if (isStoryboardThumbnail(tId, tUrl, preference)) {
                    continue
                }

                val w = tObj.optInt("width").takeIf { it > 0 }
                val h = tObj.optInt("height").takeIf { it > 0 }
                rawThumbnails.add(ThumbnailInfo(url = tUrl, width = w, height = h))
            }
        }

        // 2. Inspect single 'thumbnail' string property
        val rootThumb = json.optString("thumbnail").takeIf { it.isNotBlank() }
        if (rootThumb != null && !isStoryboardThumbnail("", rootThumb, 0)) {
            val w = json.optInt("width").takeIf { it > 0 }
            val h = json.optInt("height").takeIf { it > 0 }
            rawThumbnails.add(ThumbnailInfo(url = rootThumb, width = w, height = h))
        }

        if (rawThumbnails.isEmpty()) return

        // 3. Deduplicate thumbnail candidates by URL and distinct resolution
        val seenUrls = mutableSetOf<String>()
        val seenDimensions = mutableSetOf<Pair<Int?, Int?>>()
        val distinctThumbnails = mutableListOf<ThumbnailInfo>()

        // Sort descending by resolution (width * height)
        val sorted = rawThumbnails.sortedWith(
            compareByDescending<ThumbnailInfo> { (it.width ?: 0) * (it.height ?: 0) }
                .thenByDescending { it.width ?: 0 }
        )

        for (thumb in sorted) {
            val normalizedUrl = thumb.url.substringBefore("?")
            val dimPair = Pair(thumb.width, thumb.height)

            // Keep if URL is new AND (dimensions are null OR dimensions haven't been seen)
            if (thumb.url !in seenUrls && (dimPair.first == null || dimPair !in seenDimensions)) {
                seenUrls.add(thumb.url)
                if (dimPair.first != null) {
                    seenDimensions.add(dimPair)
                }
                distinctThumbnails.add(thumb)
            }
        }

        // 4. Create independent MediaCandidate for each distinct thumbnail variant
        for ((idx, thumb) in distinctThumbnails.withIndex()) {
            val isBest = idx == 0
            val bestSuffix = if (isBest) " (Best)" else ""
            val entrySuffix = if (isMultiEntry && totalEntries > 1) " (Slide $entryIndex)" else ""

            val label = if (thumb.width != null && thumb.height != null) {
                "Thumbnail — ${thumb.width} × ${thumb.height}$bestSuffix$entrySuffix"
            } else {
                "Thumbnail — Original$bestSuffix$entrySuffix"
            }

            val ext = thumb.url.substringAfterLast(".").substringBefore("?")
                .takeIf { isImageExt(it) } ?: "jpg"

            candidates.add(
                MediaCandidate(
                    candidateId = "ytdlp_thumb_${id}_${thumb.width ?: 0}x${thumb.height ?: 0}_$idx",
                    mediaType = MediaType.IMAGE,
                    formatLabel = label,
                    estimatedBytes = null,
                    durationMs = null,
                    width = thumb.width,
                    height = thumb.height,
                    source = AcquisitionSource.DirectHttp(url = thumb.url),
                    isOgFallback = false,
                    formatId = null,
                    ext = ext,
                    hasVideo = false,
                    hasAudio = false,
                    isBest = isBest,
                    isThumbnail = true,
                    qualityLabel = if (thumb.width != null && thumb.height != null) "${thumb.width} × ${thumb.height}" else "Original",
                    requiresMerge = false,
                    audioTrackMissing = false,
                    status = CandidateStatus.AVAILABLE,
                    extractor = platform
                )
            )
        }
    }

    /**
     * Checks if a thumbnail entry represents a storyboard or sprite sheet rather than a real full image.
     */
    private fun isStoryboardThumbnail(id: String, url: String, preference: Int): Boolean {
        if (preference < -10) return true
        val lowerId = id.lowercase()
        if (lowerId.contains("storyboard") || lowerId.startsWith("sb") || lowerId.contains("dash-preview")) {
            return true
        }
        val lowerUrl = url.lowercase()
        if (lowerUrl.contains("/sb/") || lowerUrl.contains("storyboard_") || lowerUrl.contains("sigh=")) {
            return true
        }
        return false
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
            val filesizeApprox = fObj.optLong("filesize_approx").takeIf { it > 0 }
            val fps = fObj.optDouble("fps").takeIf { !it.isNaN() && it > 0 }
            val tbr = fObj.optDouble("tbr").takeIf { !it.isNaN() && it > 0 }
            val vbr = fObj.optDouble("vbr").takeIf { !it.isNaN() && it > 0 }
            val abr = fObj.optDouble("abr").takeIf { !it.isNaN() && it > 0 }
            val formatNote = fObj.optString("format_note").takeIf { it.isNotBlank() }

            list.add(
                ParsedFormat(
                    formatId = fId,
                    ext = ext,
                    url = url,
                    vcodec = vcodec,
                    acodec = acodec,
                    width = width,
                    height = height,
                    fps = fps,
                    filesize = filesize,
                    filesizeApprox = filesizeApprox,
                    tbr = tbr,
                    vbr = vbr,
                    abr = abr,
                    formatNote = formatNote
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
        val imageFormats = formats.filter { isImageExt(it.ext) && !it.url.isNullOrBlank() }
        if (imageFormats.isNotEmpty()) {
            val best = imageFormats.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }
            if (best?.url != null) return best.url
        }

        val bestThumb = extractBestThumbnail(json)
        if (bestThumb != null) return bestThumb.url

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
            val tId = tObj.optString("id")
            val pref = tObj.optInt("preference", 0)
            if (isStoryboardThumbnail(tId, tUrl, pref)) continue

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
        val fps: Double? = null,
        val filesize: Long? = null,
        val filesizeApprox: Long? = null,
        val tbr: Double? = null,
        val vbr: Double? = null,
        val abr: Double? = null,
        val formatNote: String? = null
    )

    private data class ThumbnailInfo(
        val url: String,
        val width: Int?,
        val height: Int?
    )
}
