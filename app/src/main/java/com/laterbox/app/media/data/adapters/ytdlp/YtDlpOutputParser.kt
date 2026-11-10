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

            // Playlist or multi-item entries
            val entriesArray = root.optJSONArray("entries")
            val targetJson = if (entriesArray != null && entriesArray.length() > 0) {
                entriesArray.optJSONObject(0) ?: root
            } else {
                root
            }

            val id = targetJson.optString("id").ifBlank { "media" }
            val title = targetJson.optString("title").ifBlank { null }
            val thumbnailUrl = targetJson.optString("thumbnail").ifBlank { null }
            val durationSec = targetJson.optDouble("duration", 0.0)
            val durationMs = if (durationSec > 0) (durationSec * 1000).toLong() else null
            val platform = targetJson.optString("extractor_key").ifBlank {
                targetJson.optString("extractor").ifBlank { "yt-dlp" }
            }

            val formatsArray = targetJson.optJSONArray("formats") ?: JSONArray()
            val formats = mutableListOf<ParsedFormat>()
            for (i in 0 until formatsArray.length()) {
                val fObj = formatsArray.optJSONObject(i) ?: continue
                val fId = fObj.optString("format_id")
                val ext = fObj.optString("ext")
                val vcodec = fObj.optString("vcodec").takeIf { it.isNotBlank() && it != "none" }
                val acodec = fObj.optString("acodec").takeIf { it.isNotBlank() && it != "none" }
                val width = fObj.optInt("width").takeIf { it > 0 }
                val height = fObj.optInt("height").takeIf { it > 0 }
                val filesize = fObj.optLong("filesize").takeIf { it > 0 }
                    ?: fObj.optLong("filesize_approx").takeIf { it > 0 }
                val tbr = fObj.optDouble("tbr").takeIf { !it.isNaN() && it > 0 }

                formats.add(
                    ParsedFormat(
                        formatId = fId,
                        ext = ext,
                        vcodec = vcodec,
                        acodec = acodec,
                        width = width,
                        height = height,
                        filesize = filesize,
                        tbr = tbr
                    )
                )
            }

            val candidates = mutableListOf<MediaCandidate>()

            // 1. Video candidates
            val videoFormats = formats.filter { it.vcodec != null }
            val audioFormats = formats.filter { it.acodec != null }

            if (videoFormats.isNotEmpty()) {
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
                        // If bestVideo is already a combined format (has acodec), don't add audio size twice
                        if (bestVideo.acodec != null) bestVideo.filesize else bestVideo.filesize + bestAudio.filesize
                    }
                    bestVideo?.filesize != null -> bestVideo.filesize
                    else -> null
                }

                candidates.add(
                    MediaCandidate(
                        candidateId = "ytdlp_best_video_$id",
                        mediaType = MediaType.VIDEO,
                        formatLabel = "$qualityLabel (Best)",
                        estimatedBytes = estimatedVideoBytes,
                        durationMs = durationMs,
                        width = maxWidth,
                        height = maxHeight,
                        source = AcquisitionSource.YtDlp(
                            url = originalUrl,
                            formatSelector = "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
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

                    candidates.add(
                        MediaCandidate(
                            candidateId = "ytdlp_saver_video_$id",
                            mediaType = MediaType.VIDEO,
                            formatLabel = "480p mp4 (Data Saver)",
                            estimatedBytes = saverBytes,
                            durationMs = durationMs,
                            width = saverVideo?.width ?: 854,
                            height = saverVideo?.height ?: 480,
                            source = AcquisitionSource.YtDlp(
                                url = originalUrl,
                                formatSelector = "bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/best[height<=480]",
                                expectedExt = "mp4"
                            )
                        )
                    )
                }
            }

            // 3. Audio Only Candidate
            if (audioFormats.isNotEmpty()) {
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
                            formatSelector = "bestaudio[ext=m4a]/bestaudio",
                            expectedExt = "m4a"
                        )
                    )
                )
            }

            // 4. Best-effort Image / Carousel Items
            // If no video or audio formats found, or if thumbnails exist
            if (candidates.isEmpty()) {
                val thumbnailsArray = targetJson.optJSONArray("thumbnails")
                if (thumbnailsArray != null && thumbnailsArray.length() > 0) {
                    var bestThumbUrl: String? = null
                    var bestThumbWidth: Int? = null
                    var bestThumbHeight: Int? = null
                    var maxDim = 0

                    for (i in 0 until thumbnailsArray.length()) {
                        val tObj = thumbnailsArray.optJSONObject(i) ?: continue
                        val tUrl = tObj.optString("url").takeIf { it.isNotBlank() } ?: continue
                        val w = tObj.optInt("width", 0)
                        val h = tObj.optInt("height", 0)
                        val dim = if (w > 0 && h > 0) w * h else 1
                        if (dim >= maxDim) {
                            maxDim = dim
                            bestThumbUrl = tUrl
                            bestThumbWidth = if (w > 0) w else null
                            bestThumbHeight = if (h > 0) h else null
                        }
                    }

                    if (bestThumbUrl != null) {
                        candidates.add(
                            MediaCandidate(
                                candidateId = "ytdlp_image_$id",
                                mediaType = MediaType.IMAGE,
                                formatLabel = "Image (Original)",
                                estimatedBytes = null,
                                durationMs = null,
                                width = bestThumbWidth,
                                height = bestThumbHeight,
                                source = AcquisitionSource.DirectHttp(
                                    url = bestThumbUrl
                                )
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

    fun parseError(errorMessage: String): InspectionResult {
        val lower = errorMessage.lowercase()
        val code = parseFailureCode(lower)

        return when (code) {
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

            lower.contains("sign in") || lower.contains("login required") ||
                lower.contains("authentication required") || lower.contains("confirm you're not a robot") ||
                lower.contains("confirm you’re not a robot") || lower.contains("account required") ->
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

            lower.contains("extractorerror") || lower.contains("unable to extract") ||
                lower.contains("unsupported url") ->
                FailureCode.EXTRACTOR_BROKEN

            lower.contains("unable to download webpage") || lower.contains("temporary failure in name resolution") ||
                lower.contains("connection refused") || lower.contains("timed out") ||
                lower.contains("network is unreachable") || lower.contains("no internet") ->
                FailureCode.NETWORK_ERROR

            else -> FailureCode.UNKNOWN
        }
    }

    private data class ParsedFormat(
        val formatId: String,
        val ext: String,
        val vcodec: String?,
        val acodec: String?,
        val width: Int?,
        val height: Int?,
        val filesize: Long?,
        val tbr: Double?
    )
}
