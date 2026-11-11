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
        override val inspectionTimeoutMs: Long = 30_000L,
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
            inspectionTimeoutMs = 15_000L,
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
            inspectionTimeoutMs = 30_000L,
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
    fun platformResolver_enforcesPerAdapterTimeout_producingExtractionTimeout() = runBlocking {
        val slowAdapter = FakeAdapter(
            id = "slow_adapter",
            handles = { true },
            inspectionTimeoutMs = 50L,
            resultProvider = {
                delay(200L)
                InspectionResult.Unsupported("Done")
            }
        )

        val registry = AdapterRegistry(listOf(slowAdapter))
        val resolver = PlatformResolver(registry)

        val result = resolver.resolveAndInspect("https://slow.com")
        assertTrue("Must fail with timeout", result is InspectionResult.Failed)
        val failed = result as InspectionResult.Failed
        assertEquals(FailureCode.EXTRACTION_TIMEOUT, failed.code)
        assertTrue(failed.retryable)
    }

    @Test
    fun platformResolver_doesNotPrematurelyCancelLongerBudgetAdapter() = runBlocking {
        val longBudgetAdapter = FakeAdapter(
            id = "long_budget_adapter",
            handles = { true },
            inspectionTimeoutMs = 200L,
            resultProvider = {
                delay(50L) // Completes after 50ms, well within 200ms
                InspectionResult.Available(
                    platform = "YouTube",
                    title = "Long Video",
                    thumbnailUrl = null,
                    candidates = emptyList()
                )
            }
        )

        val registry = AdapterRegistry(listOf(longBudgetAdapter))
        val resolver = PlatformResolver(registry)

        val result = resolver.resolveAndInspect("https://youtube.com/watch?v=123")
        assertTrue("Must succeed within budget", result is InspectionResult.Available)
        assertEquals("YouTube", (result as InspectionResult.Available).platform)
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
