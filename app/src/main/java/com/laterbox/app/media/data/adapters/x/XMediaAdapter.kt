package com.laterbox.app.media.data.adapters.x

import com.laterbox.app.media.domain.adapter.MediaPlatformAdapter
import com.laterbox.app.media.domain.model.AcquisitionSource
import com.laterbox.app.media.domain.model.FailureCode
import com.laterbox.app.media.domain.model.InspectionResult
import com.laterbox.app.media.domain.model.MediaCandidate
import com.laterbox.app.media.domain.model.MediaType
import okhttp3.OkHttpClient
import java.net.URI

class XMediaAdapter(
    private val fxClient: FxEmbedClient = FxEmbedClient(),
    private val fallbackYtDlpAdapter: MediaPlatformAdapter? = null
) : MediaPlatformAdapter {

    constructor(
        okHttpClient: OkHttpClient,
        fallbackYtDlpAdapter: MediaPlatformAdapter? = null
    ) : this(
        fxClient = FxEmbedClient(okHttpClient),
        fallbackYtDlpAdapter = fallbackYtDlpAdapter
    )

    override val id: String = "x_twitter"
    override val inspectionTimeoutMs: Long
        get() = fallbackYtDlpAdapter?.inspectionTimeoutMs ?: 25_000L

    private val statusRegex = Regex("""/status/(\d+)""", RegexOption.IGNORE_CASE)

    override fun canHandle(url: String): Boolean {
        val host = try {
            URI(url).host?.lowercase() ?: ""
        } catch (_: Exception) {
            ""
        }
        if (host.isBlank()) return false

        val isXHost = host.contains("twitter.com") ||
            host.contains("x.com") ||
            host.contains("vxtwitter.com") ||
            host.contains("fxtwitter.com") ||
            host.contains("fixupx.com")

        return isXHost && statusRegex.containsMatchIn(url)
    }

    override suspend fun inspect(url: String): InspectionResult = inspect(url, null)

    override suspend fun inspect(url: String, processId: String?): InspectionResult {
        val match = statusRegex.find(url)
        val statusId = match?.groupValues?.getOrNull(1)

        if (statusId.isNullOrBlank()) {
            return InspectionResult.Unsupported("No tweet status ID found in URL")
        }

        when (val fxRes = fxClient.getTweet(statusId)) {
            is FxResult.NotFound -> {
                return InspectionResult.Failed(
                    code = FailureCode.UNSUPPORTED_URL,
                    message = fxRes.message,
                    retryable = false
                )
            }
            is FxResult.PrivateContent -> {
                return InspectionResult.NeedsAuth(
                    reason = fxRes.message
                )
            }
            is FxResult.Success -> {
                val tweet = fxRes.tweet
                val candidates = mutableListOf<MediaCandidate>()
                val xHeaders = mapOf("Referer" to "https://x.com/")

                // 1. Photos
                for ((idx, photo) in tweet.photos.withIndex()) {
                    val label = if (photo.width != null && photo.height != null) {
                        if (tweet.photos.size > 1) "Photo ${idx + 1} — ${photo.width} × ${photo.height}"
                        else "Photo — ${photo.width} × ${photo.height}"
                    } else {
                        if (tweet.photos.size > 1) "Photo ${idx + 1} — Original"
                        else "Photo — Original"
                    }

                    val ext = photo.url.substringAfterLast(".").substringBefore("?").ifBlank { "jpg" }

                    candidates.add(
                        MediaCandidate(
                            candidateId = "x_photo_${tweet.id}_$idx",
                            mediaType = MediaType.IMAGE,
                            formatLabel = label,
                            estimatedBytes = null,
                            durationMs = null,
                            width = photo.width,
                            height = photo.height,
                            source = AcquisitionSource.DirectHttp(url = photo.url, headers = xHeaders),
                            isOgFallback = false,
                            formatId = null,
                            ext = ext,
                            hasVideo = false,
                            hasAudio = false,
                            isBest = (idx == 0 && tweet.videos.isEmpty()),
                            isThumbnail = false,
                            qualityLabel = if (photo.width != null && photo.height != null) "${photo.width} × ${photo.height}" else "Original",
                            requiresMerge = false,
                            audioTrackMissing = false
                        )
                    )
                }

                // 2. Videos
                for ((vIdx, video) in tweet.videos.withIndex()) {
                    val durMs = video.duration?.let { (it * 1000).toLong() }

                    for ((fIdx, fmt) in video.formats.withIndex()) {
                        val isBest = (vIdx == 0 && fIdx == 0)
                        val bestSuffix = if (isBest) " (Best)" else ""
                        val resLabel = if (fmt.height != null) "${fmt.height}p" else "Video"
                        val bitrateLabel = if (fmt.bitrate != null) " (${fmt.bitrate / 1000} kbps)" else ""
                        val label = "Video — $resLabel$bitrateLabel$bestSuffix"

                        candidates.add(
                            MediaCandidate(
                                candidateId = "x_vid_${tweet.id}_${vIdx}_${fmt.height ?: 0}p_${fIdx}",
                                mediaType = MediaType.VIDEO,
                                formatLabel = label,
                                estimatedBytes = null,
                                durationMs = durMs,
                                width = fmt.width,
                                height = fmt.height,
                                source = AcquisitionSource.DirectHttp(url = fmt.url, headers = xHeaders),
                                isOgFallback = false,
                                formatId = null,
                                ext = "mp4",
                                hasVideo = true,
                                hasAudio = true,
                                isBest = isBest,
                                isThumbnail = false,
                                qualityLabel = resLabel,
                                requiresMerge = false,
                                audioTrackMissing = false
                            )
                        )
                    }

                    // Video poster thumbnail
                    if (!video.thumbnailUrl.isNullOrBlank()) {
                        candidates.add(
                            MediaCandidate(
                                candidateId = "x_thumb_${tweet.id}_$vIdx",
                                mediaType = MediaType.IMAGE,
                                formatLabel = "Thumbnail — Video Poster",
                                estimatedBytes = null,
                                durationMs = null,
                                width = video.width,
                                height = video.height,
                                source = AcquisitionSource.DirectHttp(url = video.thumbnailUrl, headers = xHeaders),
                                isOgFallback = false,
                                formatId = null,
                                ext = "jpg",
                                hasVideo = false,
                                hasAudio = false,
                                isBest = false,
                                isThumbnail = true,
                                qualityLabel = "Poster",
                                requiresMerge = false,
                                audioTrackMissing = false
                            )
                        )
                    }
                }

                val title = tweet.text?.take(100)
                    ?: tweet.authorName?.let { "Post by $it" }
                    ?: "X Post"

                val primaryThumbnail = tweet.photos.firstOrNull()?.url
                    ?: tweet.videos.firstOrNull()?.thumbnailUrl
                    ?: tweet.authorAvatarUrl

                if (candidates.isNotEmpty()) {
                    return InspectionResult.Available(
                        platform = "X",
                        title = title,
                        thumbnailUrl = primaryThumbnail,
                        artist = tweet.authorName,
                        candidates = candidates
                    )
                }

                // Text-only tweet
                return InspectionResult.MetadataOnly(
                    title = title,
                    artist = tweet.authorName,
                    artworkUrl = tweet.authorAvatarUrl
                )
            }
            is FxResult.Error -> {
                // Fall back to yt-dlp adapter if available
                if (fallbackYtDlpAdapter != null) {
                    return fallbackYtDlpAdapter.inspect(url, processId)
                }
                return InspectionResult.Failed(
                    code = FailureCode.NETWORK_ERROR,
                    message = fxRes.message,
                    retryable = true
                )
            }
        }
    }
}
