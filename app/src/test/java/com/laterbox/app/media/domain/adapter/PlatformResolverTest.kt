package com.laterbox.app.media.domain.adapter

import com.laterbox.app.media.domain.model.AcquisitionSource
import com.laterbox.app.media.domain.model.FailureCode
import com.laterbox.app.media.domain.model.InspectionResult
import com.laterbox.app.media.domain.model.MediaCandidate
import com.laterbox.app.media.domain.model.MediaType
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformResolverTest {

    private class FakeAdapter(
        override val id: String,
        private val handles: (String) -> Boolean,
        private val resultProvider: suspend (String) -> InspectionResult
    ) : MediaPlatformAdapter {
        override fun canHandle(url: String): Boolean = handles(url)
        override suspend fun inspect(url: String): InspectionResult = resultProvider(url)
    }

    @Test
    fun platformResolver_selectsFirstMatchingAdapter() = runBlocking {
        val adapter1 = FakeAdapter(
            id = "adapter_direct",
            handles = { it.endsWith(".mp4") },
            resultProvider = {
                InspectionResult.Available(
                    platform = "Direct",
                    title = "Direct Video",
                    thumbnailUrl = null,
                    candidates = listOf(
                        MediaCandidate(
                            candidateId = "c1",
                            mediaType = MediaType.VIDEO,
                            formatLabel = "MP4",
                            estimatedBytes = 1024L,
                            durationMs = null,
                            width = null,
                            height = null,
                            source = AcquisitionSource.DirectHttp(it)
                        )
                    )
                )
            }
        )

        val adapter2 = FakeAdapter(
            id = "adapter_metadata",
            handles = { it.contains("spotify") },
            resultProvider = {
                InspectionResult.MetadataOnly(
                    title = "Spotify Track",
                    artist = "Artist",
                    artworkUrl = null
                )
            }
        )

        val registry = AdapterRegistry(listOf(adapter1, adapter2))
        val resolver = PlatformResolver(registry)

        val resultDirect = resolver.resolveAndInspect("https://example.com/clip.mp4")
        assertTrue(resultDirect is InspectionResult.Available)
        assertEquals("Direct", (resultDirect as InspectionResult.Available).platform)

        val resultSpotify = resolver.resolveAndInspect("https://open.spotify.com/track/123")
        assertTrue(resultSpotify is InspectionResult.MetadataOnly)
        assertEquals("Spotify Track", (resultSpotify as InspectionResult.MetadataOnly).title)
    }

    @Test
    fun platformResolver_returnsUnsupportedWhenNoAdapterCanHandle() = runBlocking {
        val registry = AdapterRegistry(emptyList())
        val resolver = PlatformResolver(registry)

        val result = resolver.resolveAndInspect("https://unsupported.com")
        assertTrue("Must return Unsupported when no adapter handles URL", result is InspectionResult.Unsupported)
    }

    @Test
    fun platformResolver_timesOutWhenAdapterExceeds30Seconds() = runBlocking {
        val slowAdapter = FakeAdapter(
            id = "slow_adapter",
            handles = { true },
            resultProvider = {
                // Simulates hanging inspection exceeding timeout
                delay(200L)
                InspectionResult.Unsupported("Done")
            }
        )

        val registry = AdapterRegistry(listOf(slowAdapter))
        val resolver = PlatformResolver(registry, timeoutMs = 50L)

        val result = resolver.resolveAndInspect("https://slow.com")
        assertTrue("Must fail with timeout", result is InspectionResult.Failed)
        val failed = result as InspectionResult.Failed
        assertEquals(FailureCode.NETWORK_ERROR, failed.code)
        assertTrue(failed.retryable)
    }

    @Test
    fun metadataOnlyAndUnsupported_produceNoCandidates() {
        val metadataOnly = InspectionResult.MetadataOnly("Song", "Singer", null)
        val unsupported = InspectionResult.Unsupported("Not supported")

        // In the adapter contract, neither MetadataOnly nor Unsupported have download candidates
        assertTrue("MetadataOnly is not Available", metadataOnly !is InspectionResult.Available)
        assertTrue("Unsupported is not Available", unsupported !is InspectionResult.Available)
    }
}
