package com.laterbox.app.media.data.adapters.spotify

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.laterbox.app.media.domain.model.SpotifyTrackMetadata
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MusicDiscoveryProviderImplTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private val sampleSpotify = SpotifyTrackMetadata(
        trackId = "0VjIjW4GlUZAMYd2vXMi3b",
        title = "Blinding Lights",
        primaryArtist = "The Weeknd",
        additionalArtists = emptyList(),
        albumName = "After Hours",
        artworkUrl = "https://example.com/art.jpg",
        durationMs = 200000L,
        releaseDate = "2020-03-20",
        isExplicit = false,
        audioPreviewUrl = "https://p.scdn.co/mp3-preview/fake"
    )

    @Test
    fun testDiscoverCandidatesIncludesPreviewAndExecutesSearch() = runTest {
        val mockJson = """
            {
              "_type": "playlist",
              "entries": [
                {
                  "id": "4NRXx6U8ABQ",
                  "title": "The Weeknd - Blinding Lights (Official Audio)",
                  "uploader": "The Weeknd",
                  "duration": 200.0,
                  "url": "https://www.youtube.com/watch?v=4NRXx6U8ABQ"
                },
                {
                  "id": "fHI8X489TSg",
                  "title": "The Weeknd - Blinding Lights (Official Video)",
                  "uploader": "The Weeknd",
                  "duration": 263.0,
                  "url": "https://www.youtube.com/watch?v=fHI8X489TSg"
                }
              ]
            }
        """.trimIndent()

        val provider = MusicDiscoveryProviderImpl(
            appContext = context,
            customExecutor = { request ->
                YoutubeDLResponse(
                    emptyList(),
                    0,
                    50L,
                    mockJson,
                    ""
                )
            }
        )

        val candidates = provider.discoverCandidates(sampleSpotify)

        // 1 preview candidate + 2 search candidates
        assertEquals(3, candidates.size)

        // Check preview candidate
        val preview = candidates.find { it.id.startsWith("spotify_preview_") }
        assertNotNull(preview)
        assertEquals("Spotify Public Preview Stream", preview?.sourceDescription)

        // Check yt candidates
        val topYt = candidates.find { it.id == "stream_4NRXx6U8ABQ" }
        assertNotNull(topYt)
        assertEquals("The Weeknd - Blinding Lights (Official Audio)", topYt?.title)
        assertEquals("The Weeknd", topYt?.artist)
        assertEquals(200000L, topYt?.durationMs)
    }

    @Test
    fun testDiscoverCandidatesGracefulWhenSearchFails() = runTest {
        val provider = MusicDiscoveryProviderImpl(
            appContext = context,
            customExecutor = {
                throw RuntimeException("Network down")
            }
        )

        val candidates = provider.discoverCandidates(sampleSpotify)

        // Should still contain the preview candidate gracefully
        assertEquals(1, candidates.size)
        assertTrue(candidates[0].id.startsWith("spotify_preview_"))
    }
}
