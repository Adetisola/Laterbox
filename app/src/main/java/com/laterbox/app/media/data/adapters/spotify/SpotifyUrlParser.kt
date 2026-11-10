package com.laterbox.app.media.data.adapters.spotify

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.regex.Pattern

sealed interface SpotifyLink {
    data class Track(
        val trackId: String,
        val canonicalUrl: String = "https://open.spotify.com/track/$trackId"
    ) : SpotifyLink

    data class ShortLink(
        val rawUrl: String
    ) : SpotifyLink

    data class UnsupportedType(
        val type: String,
        val rawUrl: String
    ) : SpotifyLink

    data object Invalid : SpotifyLink
}

object SpotifyUrlParser {

    private val TRACK_ID_REGEX = Pattern.compile("^[a-zA-Z0-9]{15,30}$")

    // Matches /track/{trackId} or /intl-{region}/track/{trackId}
    private val WEB_TRACK_REGEX = Pattern.compile(
        """https?://open\.spotify\.com/(?:intl-[a-zA-Z]{2,5}/)?track/([a-zA-Z0-9]+)(?:[/?#].*)?""",
        Pattern.CASE_INSENSITIVE
    )

    // Matches other entity types like /album/{id}, /playlist/{id}, /artist/{id}, /show/{id}, /episode/{id}
    private val WEB_NON_TRACK_REGEX = Pattern.compile(
        """https?://open\.spotify\.com/(?:intl-[a-zA-Z]{2,5}/)?(album|playlist|artist|show|episode)/([a-zA-Z0-9]+)(?:[/?#].*)?""",
        Pattern.CASE_INSENSITIVE
    )

    // Matches spotify:track:{trackId}
    private val URI_TRACK_REGEX = Pattern.compile(
        """^spotify:track:([a-zA-Z0-9]+)$""",
        Pattern.CASE_INSENSITIVE
    )

    // Matches spotify:album:{id}, spotify:playlist:{id}, etc.
    private val URI_NON_TRACK_REGEX = Pattern.compile(
        """^spotify:(album|playlist|artist|show|episode):([a-zA-Z0-9]+)$""",
        Pattern.CASE_INSENSITIVE
    )

    // Matches spotify.link/{slug}
    private val SHORT_LINK_REGEX = Pattern.compile(
        """https?://(?:www\.)?spotify\.link/[a-zA-Z0-9]+""",
        Pattern.CASE_INSENSITIVE
    )

    fun isSpotifyUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.startsWith("spotify:", ignoreCase = true)) return true
        val host = try {
            URI(trimmed).host?.lowercase() ?: ""
        } catch (_: Exception) {
            ""
        }
        return host.contains("spotify.com") || host.contains("spotify.link") || host.endsWith(".spotify.com")
    }

    fun parse(rawInput: String): SpotifyLink {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return SpotifyLink.Invalid

        // 1. Spotify URI: spotify:track:...
        val uriTrackMatcher = URI_TRACK_REGEX.matcher(trimmed)
        if (uriTrackMatcher.matches()) {
            val trackId = uriTrackMatcher.group(1).orEmpty()
            return if (isValidTrackId(trackId)) {
                SpotifyLink.Track(trackId)
            } else {
                SpotifyLink.Invalid
            }
        }

        // 2. Spotify URI non-track: spotify:album:..., spotify:playlist:...
        val uriNonTrackMatcher = URI_NON_TRACK_REGEX.matcher(trimmed)
        if (uriNonTrackMatcher.matches()) {
            val type = uriNonTrackMatcher.group(1).orEmpty().lowercase()
            return SpotifyLink.UnsupportedType(type = type, rawUrl = trimmed)
        }

        // 3. Web URL: open.spotify.com/track/...
        val webTrackMatcher = WEB_TRACK_REGEX.matcher(trimmed)
        if (webTrackMatcher.find()) {
            val trackId = webTrackMatcher.group(1).orEmpty()
            return if (isValidTrackId(trackId)) {
                SpotifyLink.Track(trackId)
            } else {
                SpotifyLink.Invalid
            }
        }

        // 4. Web URL non-track: open.spotify.com/album/..., open.spotify.com/playlist/...
        val webNonTrackMatcher = WEB_NON_TRACK_REGEX.matcher(trimmed)
        if (webNonTrackMatcher.find()) {
            val type = webNonTrackMatcher.group(1).orEmpty().lowercase()
            return SpotifyLink.UnsupportedType(type = type, rawUrl = trimmed)
        }

        // 5. Short link: spotify.link/...
        val shortMatcher = SHORT_LINK_REGEX.matcher(trimmed)
        if (shortMatcher.find()) {
            return SpotifyLink.ShortLink(rawUrl = trimmed)
        }

        return SpotifyLink.Invalid
    }

    /**
     * Resolves a spotify.link redirect to a canonical open.spotify.com URL if needed.
     */
    fun resolveCanonicalLink(client: OkHttpClient, link: SpotifyLink): SpotifyLink {
        return when (link) {
            is SpotifyLink.Track -> link
            is SpotifyLink.ShortLink -> {
                try {
                    val request = Request.Builder()
                        .url(link.rawUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .build()

                    // OkHttpClient with default redirect following will reach the final URL
                    val response = client.newCall(request).execute()
                    response.use { resp ->
                        val finalUrl = resp.request.url.toString()
                        parse(finalUrl)
                    }
                } catch (_: Exception) {
                    SpotifyLink.Invalid
                }
            }
            else -> link
        }
    }

    private fun isValidTrackId(id: String): Boolean {
        return TRACK_ID_REGEX.matcher(id).matches()
    }
}
