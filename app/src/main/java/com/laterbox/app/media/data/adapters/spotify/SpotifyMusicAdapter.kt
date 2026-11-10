package com.laterbox.app.media.data.adapters.spotify

import com.laterbox.app.media.domain.adapter.MediaPlatformAdapter
import com.laterbox.app.media.domain.matching.MusicDiscoveryProvider
import com.laterbox.app.media.domain.matching.MusicMatchingEngine
import com.laterbox.app.media.domain.model.FailureCode
import com.laterbox.app.media.domain.model.InspectionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class SpotifyMusicAdapter(
    private val spotifyMetadataProvider: SpotifyMetadataProvider = SpotifyMetadataProviderImpl(),
    private val musicDiscoveryProvider: MusicDiscoveryProvider,
    private val matchingEngine: MusicMatchingEngine = MusicMatchingEngine,
    private val okHttpClient: OkHttpClient = OkHttpClient()
) : MediaPlatformAdapter {

    override val id: String = "spotify"

    override fun canHandle(url: String): Boolean {
        return SpotifyUrlParser.isSpotifyUrl(url)
    }

    override suspend fun inspect(url: String): InspectionResult = withContext(Dispatchers.IO) {
        var parsedLink = SpotifyUrlParser.parse(url)
        if (parsedLink is SpotifyLink.ShortLink) {
            parsedLink = SpotifyUrlParser.resolveCanonicalLink(okHttpClient, parsedLink)
        }

        if (parsedLink !is SpotifyLink.Track) {
            return@withContext InspectionResult.Unsupported(
                "Only Spotify track links are supported for music audio matching."
            )
        }

        val trackId = parsedLink.trackId
        val metadataResult = spotifyMetadataProvider.fetchTrackMetadata(trackId)
        val spotifyMetadata = metadataResult.getOrElse { error ->
            return@withContext InspectionResult.Failed(
                code = FailureCode.UNKNOWN,
                message = error.message ?: "Failed to extract Spotify track metadata",
                retryable = true
            )
        }

        // Discover and rank matching audio candidates
        val rawCandidates = musicDiscoveryProvider.discoverCandidates(spotifyMetadata)
        val rankedMatches = matchingEngine.rankCandidates(spotifyMetadata, rawCandidates)

        val mediaCandidates = rankedMatches.map { it.downloadCandidate }

        if (mediaCandidates.isEmpty()) {
            return@withContext InspectionResult.MetadataOnly(
                title = spotifyMetadata.title,
                artist = spotifyMetadata.allArtistsFormatted,
                artworkUrl = spotifyMetadata.artworkUrl
            )
        }

        InspectionResult.Available(
            platform = "Spotify",
            title = spotifyMetadata.title,
            thumbnailUrl = spotifyMetadata.artworkUrl,
            candidates = mediaCandidates,
            artist = spotifyMetadata.primaryArtist,
            album = spotifyMetadata.albumName,
            musicMatches = rankedMatches,
            spotifyTrackId = spotifyMetadata.trackId
        )
    }
}
