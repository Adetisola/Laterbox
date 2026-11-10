package com.laterbox.app.media.data.adapters.spotify

import com.laterbox.app.media.domain.matching.MusicDiscoveryProvider
import com.laterbox.app.media.domain.matching.RawMusicCandidate
import com.laterbox.app.media.domain.model.AcquisitionSource
import com.laterbox.app.media.domain.model.InspectionResult
import com.laterbox.app.media.domain.model.MatchConfidence
import com.laterbox.app.media.domain.model.MediaCandidate
import com.laterbox.app.media.domain.model.MediaType
import com.laterbox.app.media.domain.model.SpotifyTrackMetadata
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyMusicAdapterTest {

    private val sampleMetadata = SpotifyTrackMetadata(
        trackId = "4cOdK2wGLETKBW3PvgPWqT",
        title = "Never Gonna Give You Up",
        primaryArtist = "Rick Astley",
        additionalArtists = emptyList(),
        albumName = "Whenever You Need Somebody",
        artworkUrl = "https://example.com/rick.jpg",
        durationMs = 213000L,
        releaseDate = "1987-11-12",
        isExplicit = false,
        audioPreviewUrl = null
    )

    private val sampleCandidate = RawMusicCandidate(
        id = "rick_yt",
        title = "Rick Astley - Never Gonna Give You Up (Official Music Video)",
        artist = "Rick Astley",
        durationMs = 214000L,
        downloadCandidate = MediaCandidate(
            candidateId = "rick_audio",
            mediaType = MediaType.AUDIO,
            formatLabel = "Audio (m4a)",
            estimatedBytes = 3500000L,
            durationMs = 214000L,
            width = null,
            height = null,
            source = AcquisitionSource.YtDlp(
                url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                formatSelector = "bestaudio",
                expectedExt = "m4a"
            )
        )
    )

    @Test
    fun testCanHandleOnlySpotifyUrls() {
        val adapter = SpotifyMusicAdapter(
            spotifyMetadataProvider = object : SpotifyMetadataProvider {
                override suspend fun fetchTrackMetadata(trackId: String) = Result.success(sampleMetadata)
            },
            musicDiscoveryProvider = object : MusicDiscoveryProvider {
                override suspend fun discoverCandidates(spotify: SpotifyTrackMetadata) = emptyList<RawMusicCandidate>()
            }
        )

        assertTrue(adapter.canHandle("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT"))
        assertTrue(adapter.canHandle("spotify:track:4cOdK2wGLETKBW3PvgPWqT"))
        assertFalse(adapter.canHandle("https://www.youtube.com/watch?v=123"))
        assertFalse(adapter.canHandle("https://example.com/audio.mp3"))
    }

    @Test
    fun testInspectNonTrackReturnsUnsupported() = runTest {
        val adapter = SpotifyMusicAdapter(
            spotifyMetadataProvider = object : SpotifyMetadataProvider {
                override suspend fun fetchTrackMetadata(trackId: String) = Result.success(sampleMetadata)
            },
            musicDiscoveryProvider = object : MusicDiscoveryProvider {
                override suspend fun discoverCandidates(spotify: SpotifyTrackMetadata) = emptyList<RawMusicCandidate>()
            }
        )

        val result = adapter.inspect("https://open.spotify.com/album/4cOdK2wGLETKBW3PvgPWqT")
        assertTrue(result is InspectionResult.Unsupported)
    }

    @Test
    fun testInspectWithCandidatesReturnsAvailableWithMatches() = runTest {
        val adapter = SpotifyMusicAdapter(
            spotifyMetadataProvider = object : SpotifyMetadataProvider {
                override suspend fun fetchTrackMetadata(trackId: String) = Result.success(sampleMetadata)
            },
            musicDiscoveryProvider = object : MusicDiscoveryProvider {
                override suspend fun discoverCandidates(spotify: SpotifyTrackMetadata) = listOf(sampleCandidate)
            }
        )

        val result = adapter.inspect("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT")
        assertTrue(result is InspectionResult.Available)

        val available = result as InspectionResult.Available
        assertEquals("Spotify", available.platform)
        assertEquals("Never Gonna Give You Up", available.title)
        assertEquals("Rick Astley", available.artist)
        assertEquals("Whenever You Need Somebody", available.album)
        assertEquals("4cOdK2wGLETKBW3PvgPWqT", available.spotifyTrackId)
        assertEquals(1, available.candidates.size)
        assertEquals(1, available.musicMatches.size)
        assertEquals(MatchConfidence.STRONG, available.musicMatches[0].confidence)
    }

    @Test
    fun testInspectWithNoCandidatesReturnsMetadataOnly() = runTest {
        val adapter = SpotifyMusicAdapter(
            spotifyMetadataProvider = object : SpotifyMetadataProvider {
                override suspend fun fetchTrackMetadata(trackId: String) = Result.success(sampleMetadata)
            },
            musicDiscoveryProvider = object : MusicDiscoveryProvider {
                override suspend fun discoverCandidates(spotify: SpotifyTrackMetadata) = emptyList<RawMusicCandidate>()
            }
        )

        val result = adapter.inspect("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT")
        assertTrue(result is InspectionResult.MetadataOnly)

        val metadata = result as InspectionResult.MetadataOnly
        assertEquals("Never Gonna Give You Up", metadata.title)
        assertEquals("Rick Astley", metadata.artist)
    }
}
