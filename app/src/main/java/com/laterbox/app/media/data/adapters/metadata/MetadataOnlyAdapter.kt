package com.laterbox.app.media.data.adapters.metadata

import com.laterbox.app.media.domain.adapter.MediaPlatformAdapter
import com.laterbox.app.media.domain.model.InspectionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI

class MetadataOnlyAdapter(
    private val client: OkHttpClient = OkHttpClient()
) : MediaPlatformAdapter {

    override val id: String = "metadata_only"
    override val inspectionTimeoutMs: Long = 15_000L

    override fun canHandle(url: String): Boolean {
        val host = try {
            URI(url).host?.lowercase() ?: ""
        } catch (_: Exception) {
            ""
        }
        return host.contains("spotify.com") || host.contains("spotify.link") || host.endsWith(".spotify.com")
    }

    override suspend fun inspect(url: String): InspectionResult = withContext(Dispatchers.IO) {
        val host = try {
            URI(url).host?.lowercase() ?: ""
        } catch (_: Exception) {
            ""
        }

        if (host.contains("spotify")) {
            // Attempt Spotify oEmbed endpoint: https://open.spotify.com/oembed?url=...
            try {
                val oEmbedUrl = "https://open.spotify.com/oembed?url=${java.net.URLEncoder.encode(url, "UTF-8")}"
                val request = Request.Builder()
                    .url(oEmbedUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Laterbox)")
                    .build()

                val response = client.newCall(request).execute()
                response.use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (body != null) {
                            val json = JSONObject(body)
                            val title = json.optString("title").ifBlank { null }
                            val artwork = json.optString("thumbnail_url").ifBlank { null }
                            return@withContext InspectionResult.MetadataOnly(
                                title = title,
                                artist = "Spotify",
                                artworkUrl = artwork
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Fall back to clean URL label
            }

            return@withContext InspectionResult.MetadataOnly(
                title = "Spotify Track",
                artist = "Spotify",
                artworkUrl = null
            )
        }

        // Generic fallback for any other metadata-only page
        InspectionResult.MetadataOnly(
            title = null,
            artist = null,
            artworkUrl = null
        )
    }
}
