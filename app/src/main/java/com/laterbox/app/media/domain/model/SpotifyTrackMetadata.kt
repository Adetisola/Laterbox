package com.laterbox.app.media.domain.model

import java.util.Locale

/**
 * Public metadata extracted from a Spotify track link without user login or DRM decryption.
 */
data class SpotifyTrackMetadata(
    val trackId: String,
    val title: String,
    val primaryArtist: String,
    val additionalArtists: List<String> = emptyList(),
    val albumName: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long? = null,
    val releaseDate: String? = null,
    val isExplicit: Boolean = false,
    val audioPreviewUrl: String? = null
) {
    val allArtistsFormatted: String
        get() = (listOf(primaryArtist) + additionalArtists)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")

    val searchQuery: String
        get() = "$primaryArtist $title".trim()

    val durationFormatted: String
        get() {
            if (durationMs == null || durationMs <= 0) return "--:--"
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
}
