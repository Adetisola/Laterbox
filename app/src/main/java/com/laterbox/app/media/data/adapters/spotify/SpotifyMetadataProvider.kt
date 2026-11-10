package com.laterbox.app.media.data.adapters.spotify

import com.laterbox.app.media.domain.model.SpotifyTrackMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.regex.Pattern

interface SpotifyMetadataProvider {
    suspend fun fetchTrackMetadata(trackId: String): Result<SpotifyTrackMetadata>
}

class SpotifyMetadataProviderImpl(
    private val client: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://open.spotify.com"
) : SpotifyMetadataProvider {

    private val nextDataPattern = Pattern.compile(
        """<script\s+id="__NEXT_DATA__"[^>]*>(.*?)</script>""",
        Pattern.DOTALL or Pattern.CASE_INSENSITIVE
    )

    private val ogDescriptionPattern = Pattern.compile(
        """<meta\s+property="og:description"\s+content="([^"]*)"""",
        Pattern.CASE_INSENSITIVE
    )

    override suspend fun fetchTrackMetadata(trackId: String): Result<SpotifyTrackMetadata> = withContext(Dispatchers.IO) {
        val cleanTrackId = trackId.trim()
        if (cleanTrackId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Track ID cannot be blank"))
        }

        // Tier 1: Embed scraper
        val tier1Result = runCatching { fetchFromEmbed(cleanTrackId) }
        if (tier1Result.isSuccess && tier1Result.getOrNull() != null) {
            return@withContext Result.success(tier1Result.getOrThrow()!!)
        }

        // Tier 2: oEmbed fallback
        val tier2Result = runCatching { fetchFromOembed(cleanTrackId) }
        if (tier2Result.isSuccess && tier2Result.getOrNull() != null) {
            return@withContext Result.success(tier2Result.getOrThrow()!!)
        }

        val cause = tier1Result.exceptionOrNull() ?: tier2Result.exceptionOrNull() ?: Exception("Failed to extract Spotify metadata")
        Result.failure(cause)
    }

    private fun fetchFromEmbed(trackId: String): SpotifyTrackMetadata? {
        val embedUrl = "$baseUrl/embed/track/$trackId"
        val request = Request.Builder()
            .url(embedUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        val response = client.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) return null
            val html = resp.body?.string().orEmpty()
            if (html.isBlank()) return null

            val matcher = nextDataPattern.matcher(html)
            if (!matcher.find()) return null

            val jsonContent = matcher.group(1).orEmpty()
            if (jsonContent.isBlank()) return null

            val root = JSONObject(jsonContent)
            val entity = root.optJSONObject("props")
                ?.optJSONObject("pageProps")
                ?.optJSONObject("state")
                ?.optJSONObject("data")
                ?.optJSONObject("entity")
                ?: return null

            val title = entity.optString("title").ifBlank { entity.optString("name") }
            if (title.isBlank()) return null

            val artistsList = mutableListOf<String>()
            val artistsArray = entity.optJSONArray("artists")
            if (artistsArray != null) {
                for (i in 0 until artistsArray.length()) {
                    val aObj = artistsArray.optJSONObject(i)
                    val aName = aObj?.optString("name").orEmpty().trim()
                    if (aName.isNotBlank()) {
                        artistsList.add(aName)
                    }
                }
            }

            val primaryArtist = artistsList.firstOrNull() ?: "Unknown Artist"
            val additionalArtists = if (artistsList.size > 1) artistsList.drop(1) else emptyList()

            val durationMs = entity.optLong("duration", 0L).takeIf { it > 0 }
            val isExplicit = entity.optBoolean("isExplicit", false)
            val releaseDate = entity.optJSONObject("releaseDate")?.optString("isoString")?.take(10)

            // Select highest resolution image from visualIdentity.image
            var artworkUrl: String? = null
            var maxImageDimension = -1
            val imageArray = entity.optJSONObject("visualIdentity")?.optJSONArray("image")
            if (imageArray != null) {
                for (i in 0 until imageArray.length()) {
                    val imgObj = imageArray.optJSONObject(i) ?: continue
                    val url = imgObj.optString("url")
                    val width = imgObj.optInt("maxWidth", 0)
                    if (url.isNotBlank() && width >= maxImageDimension) {
                        maxImageDimension = width
                        artworkUrl = url
                    }
                }
            }

            // Extract album name from og:description if available:
            // e.g. "Rick Astley · Whenever You Need Somebody · Song · 1987"
            var albumName: String? = null
            val ogMatcher = ogDescriptionPattern.matcher(html)
            if (ogMatcher.find()) {
                val ogDesc = ogMatcher.group(1).orEmpty()
                val parts = ogDesc.split("·", "\u00b7").map { it.trim() }
                if (parts.size >= 3) {
                    // Part 0 is artist(s), Part 1 is album name, Part 2 is "Song" or type
                    albumName = parts[1].takeIf { it.isNotBlank() }
                }
            }

            val audioPreviewUrl = entity.optJSONObject("audioPreview")?.optString("url")?.takeIf { it.isNotBlank() }

            return SpotifyTrackMetadata(
                trackId = trackId,
                title = title,
                primaryArtist = primaryArtist,
                additionalArtists = additionalArtists,
                albumName = albumName,
                artworkUrl = artworkUrl,
                durationMs = durationMs,
                releaseDate = releaseDate,
                isExplicit = isExplicit,
                audioPreviewUrl = audioPreviewUrl
            )
        }
    }

    private fun fetchFromOembed(trackId: String): SpotifyTrackMetadata? {
        val canonicalUrl = "https://open.spotify.com/track/$trackId"
        val oembedUrl = "$baseUrl/oembed?url=${URLEncoder.encode(canonicalUrl, "UTF-8")}"
        val request = Request.Builder()
            .url(oembedUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Laterbox)")
            .build()

        val response = client.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val json = JSONObject(body)

            val title = json.optString("title").trim()
            if (title.isBlank()) return null

            // oEmbed typically doesn't separate artist cleanly in title, but provides thumbnail_url
            val artworkUrl = json.optString("thumbnail_url").takeIf { it.isNotBlank() }

            return SpotifyTrackMetadata(
                trackId = trackId,
                title = title,
                primaryArtist = "Spotify Track",
                additionalArtists = emptyList(),
                albumName = null,
                artworkUrl = artworkUrl,
                durationMs = null,
                releaseDate = null,
                isExplicit = false,
                audioPreviewUrl = null
            )
        }
    }
}
