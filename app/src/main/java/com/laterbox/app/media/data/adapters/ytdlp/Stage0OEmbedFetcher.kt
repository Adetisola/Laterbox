package com.laterbox.app.media.data.adapters.ytdlp

import com.laterbox.app.media.domain.model.AcquisitionSource
import com.laterbox.app.media.domain.model.CandidateStatus
import com.laterbox.app.media.domain.model.InspectionResult
import com.laterbox.app.media.domain.model.MediaCandidate
import com.laterbox.app.media.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class Stage0Result(
    val title: String?,
    val author: String?,
    val thumbnailUrl: String?,
    val platform: String
)

class Stage0OEmbedFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {

    suspend fun fetch(url: String): Stage0Result? = withContext(Dispatchers.IO) {
        val host = try { URI(url).host?.lowercase() ?: "" } catch (_: Throwable) { "" }

        when {
            host.contains("youtube.com") || host.contains("youtu.be") -> {
                fetchYouTubeOEmbed(url) ?: fetchOpenGraph(url, "YouTube")
            }
            host.contains("tiktok.com") -> {
                fetchTikTokOEmbed(url) ?: fetchOpenGraph(url, "TikTok")
            }
            host.contains("twitter.com") || host.contains("x.com") -> {
                fetchTwitterOEmbed(url) ?: fetchOpenGraph(url, "Twitter")
            }
            else -> {
                val platform = when {
                    host.contains("instagram.com") -> "Instagram"
                    host.contains("reddit.com") -> "Reddit"
                    host.contains("facebook.com") -> "Facebook"
                    else -> "Web"
                }
                fetchOpenGraph(url, platform)
            }
        }
    }

    private fun fetchYouTubeOEmbed(url: String): Stage0Result? {
        return try {
            val endpoint = "https://www.youtube.com/oembed?url=" + URLEncoder.encode(url, "UTF-8") + "&format=json"
            val request = Request.Builder().url(endpoint).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val title = json.optString("title").takeIf { it.isNotBlank() }
                val author = json.optString("author_name").takeIf { it.isNotBlank() }
                val thumb = json.optString("thumbnail_url").takeIf { it.isNotBlank() }
                Stage0Result(title = title, author = author, thumbnailUrl = thumb, platform = "YouTube")
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun fetchTikTokOEmbed(url: String): Stage0Result? {
        return try {
            val endpoint = "https://www.tiktok.com/oembed?url=" + URLEncoder.encode(url, "UTF-8")
            val request = Request.Builder().url(endpoint).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val title = json.optString("title").takeIf { it.isNotBlank() }
                val author = json.optString("author_name").takeIf { it.isNotBlank() }
                val thumb = json.optString("thumbnail_url").takeIf { it.isNotBlank() }
                Stage0Result(title = title, author = author, thumbnailUrl = thumb, platform = "TikTok")
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun fetchTwitterOEmbed(url: String): Stage0Result? {
        return try {
            val endpoint = "https://publish.twitter.com/oembed?url=" + URLEncoder.encode(url, "UTF-8")
            val request = Request.Builder().url(endpoint).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val author = json.optString("author_name").takeIf { it.isNotBlank() }
                // Twitter oEmbed HTML often contains the text of the tweet
                val html = json.optString("html")
                val title = if (html.isNotBlank()) {
                    try { Jsoup.parse(html).text().take(120) } catch (_: Throwable) { null }
                } else null
                Stage0Result(title = title, author = author, thumbnailUrl = null, platform = "Twitter")
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun fetchOpenGraph(url: String, defaultPlatform: String): Stage0Result? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val html = response.body?.string() ?: return null
                val doc = Jsoup.parse(html)
                val ogTitle = doc.select("meta[property=og:title]").attr("content").takeIf { it.isNotBlank() }
                    ?: doc.title().takeIf { it.isNotBlank() }
                val ogImage = doc.select("meta[property=og:image]").attr("content").takeIf { it.isNotBlank() }
                val ogSite = doc.select("meta[property=og:site_name]").attr("content").takeIf { it.isNotBlank() }

                if (ogTitle.isNullOrBlank() && ogImage.isNullOrBlank()) {
                    null
                } else {
                    Stage0Result(
                        title = ogTitle,
                        author = null,
                        thumbnailUrl = ogImage,
                        platform = ogSite ?: defaultPlatform
                    )
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun toInspectionResult(stage0: Stage0Result): InspectionResult.Available {
        val candidates = mutableListOf<MediaCandidate>()
        if (!stage0.thumbnailUrl.isNullOrBlank()) {
            candidates.add(
                MediaCandidate(
                    candidateId = "stage0_preview_thumbnail",
                    mediaType = MediaType.IMAGE,
                    formatLabel = "Preview image only",
                    estimatedBytes = null,
                    durationMs = null,
                    width = null,
                    height = null,
                    source = AcquisitionSource.DirectHttp(url = stage0.thumbnailUrl),
                    isOgFallback = true,
                    isThumbnail = true,
                    status = CandidateStatus.AVAILABLE,
                    extractor = stage0.platform
                )
            )
        }

        return InspectionResult.Available(
            platform = stage0.platform,
            title = stage0.title,
            thumbnailUrl = stage0.thumbnailUrl,
            candidates = candidates,
            isComplete = false
        )
    }
}
