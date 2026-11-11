package com.laterbox.app.media.data.adapters.direct

import com.laterbox.app.media.domain.adapter.MediaPlatformAdapter
import com.laterbox.app.media.domain.model.AcquisitionSource
import com.laterbox.app.media.domain.model.FailureCode
import com.laterbox.app.media.domain.model.InspectionResult
import com.laterbox.app.media.domain.model.MediaCandidate
import com.laterbox.app.media.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

class DirectUrlAdapter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) : MediaPlatformAdapter {

    override val id: String = "direct_url"
    override val inspectionTimeoutMs: Long = 15_000L

    private val directExtensions = mapOf(
        // Images
        "jpg" to MediaType.IMAGE, "jpeg" to MediaType.IMAGE, "png" to MediaType.IMAGE,
        "webp" to MediaType.IMAGE, "gif" to MediaType.IMAGE, "svg" to MediaType.IMAGE,
        // Videos
        "mp4" to MediaType.VIDEO, "webm" to MediaType.VIDEO, "mkv" to MediaType.VIDEO,
        "mov" to MediaType.VIDEO, "m4v" to MediaType.VIDEO,
        // Audio
        "mp3" to MediaType.AUDIO, "m4a" to MediaType.AUDIO, "wav" to MediaType.AUDIO,
        "ogg" to MediaType.AUDIO, "flac" to MediaType.AUDIO, "aac" to MediaType.AUDIO,
        // Documents
        "pdf" to MediaType.DOCUMENT, "epub" to MediaType.DOCUMENT
    )

    override fun canHandle(url: String): Boolean {
        val ext = extractExtension(url)
        return ext != null && directExtensions.containsKey(ext)
    }

    override suspend fun inspect(url: String): InspectionResult = withContext(Dispatchers.IO) {
        val ext = extractExtension(url)
        var detectedType = ext?.let { directExtensions[it] }

        val headRequest = Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Laterbox) AppleWebKit/537.36")
            .build()

        try {
            val response = client.newCall(headRequest).execute()
            response.use { resp ->
                if (resp.code in listOf(401, 403)) {
                    return@withContext InspectionResult.NeedsAuth("Authentication required (HTTP ${resp.code})")
                }
                if (resp.code == 404) {
                    return@withContext InspectionResult.Unsupported("File not found (HTTP 404)")
                }
                if (!resp.isSuccessful) {
                    return@withContext InspectionResult.Failed(
                        code = FailureCode.HTTP_ERROR,
                        message = "Server returned HTTP ${resp.code}",
                        retryable = resp.code >= 500
                    )
                }

                val rawContentType = resp.header("Content-Type")?.split(";")?.firstOrNull()?.trim()?.lowercase(Locale.ROOT)
                val contentLength = resp.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }

                if (detectedType == null && rawContentType != null) {
                    detectedType = when {
                        rawContentType.startsWith("image/") -> MediaType.IMAGE
                        rawContentType.startsWith("video/") -> MediaType.VIDEO
                        rawContentType.startsWith("audio/") -> MediaType.AUDIO
                        rawContentType == "application/pdf" -> MediaType.DOCUMENT
                        else -> null
                    }
                }

                val finalType = detectedType
                    ?: return@withContext InspectionResult.Unsupported("URL does not serve supported media content")

                val filename = extractFilename(url, resp.header("Content-Disposition"))
                val formatLabel = buildFormatLabel(finalType, ext, rawContentType)

                val candidate = MediaCandidate(
                    candidateId = "direct_1",
                    mediaType = finalType,
                    formatLabel = formatLabel,
                    estimatedBytes = contentLength,
                    durationMs = null,
                    width = null,
                    height = null,
                    source = AcquisitionSource.DirectHttp(
                        url = url,
                        headers = emptyMap(),
                        mimeType = rawContentType
                    )
                )

                InspectionResult.Available(
                    platform = "Direct",
                    title = filename,
                    thumbnailUrl = if (finalType == MediaType.IMAGE) url else null,
                    candidates = listOf(candidate)
                )
            }
        } catch (e: Exception) {
            val fallbackType = ext?.let { directExtensions[it] }
            if (fallbackType != null) {
                val candidate = MediaCandidate(
                    candidateId = "direct_1",
                    mediaType = fallbackType,
                    formatLabel = ext.uppercase(Locale.ROOT),
                    estimatedBytes = null,
                    durationMs = null,
                    width = null,
                    height = null,
                    source = AcquisitionSource.DirectHttp(url = url)
                )
                return@withContext InspectionResult.Available(
                    platform = "Direct",
                    title = extractFilename(url, null),
                    thumbnailUrl = if (fallbackType == MediaType.IMAGE) url else null,
                    candidates = listOf(candidate)
                )
            }
            InspectionResult.Failed(
                code = FailureCode.NETWORK_ERROR,
                message = e.localizedMessage ?: "Failed to inspect URL",
                retryable = true
            )
        }
    }

    private fun extractExtension(url: String): String? {
        return try {
            val uri = URI(url)
            val path = uri.path ?: return null
            val lastDot = path.lastIndexOf('.')
            if (lastDot != -1 && lastDot < path.length - 1) {
                val candidate = path.substring(lastDot + 1).lowercase(Locale.ROOT)
                if (candidate.length in 2..5 && candidate.all { it.isLetterOrDigit() }) {
                    candidate
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractFilename(url: String, contentDisposition: String?): String {
        if (!contentDisposition.isNullOrBlank()) {
            val match = Regex("""filename\*?=['"]?(?:UTF-\d['"]*)?([^'";\r\n]+)['"]?""", RegexOption.IGNORE_CASE)
                .find(contentDisposition)
            if (match != null) {
                val name = match.groupValues[1].trim()
                if (name.isNotBlank()) return name
            }
        }
        return try {
            val uri = URI(url)
            val path = uri.path
            val name = path?.substringAfterLast('/')?.ifEmpty { null }
            name ?: uri.host ?: "Media File"
        } catch (_: Exception) {
            "Media File"
        }
    }

    private fun buildFormatLabel(mediaType: MediaType, ext: String?, mime: String?): String {
        val extUpper = ext?.uppercase(Locale.ROOT)
        return when (mediaType) {
            MediaType.IMAGE -> extUpper ?: "IMAGE"
            MediaType.VIDEO -> extUpper ?: "VIDEO"
            MediaType.AUDIO -> extUpper ?: "AUDIO"
            MediaType.DOCUMENT -> extUpper ?: "DOCUMENT"
            MediaType.OTHER -> mime ?: "FILE"
        }
    }
}
