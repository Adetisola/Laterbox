package com.laterbox.app.media.data.inspection

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.laterbox.app.media.data.adapters.direct.DirectUrlAdapter
import com.laterbox.app.media.data.adapters.metadata.MetadataOnlyAdapter
import com.laterbox.app.media.domain.matching.MusicDiscoveryProvider
import com.laterbox.app.media.domain.matching.RawMusicCandidate
import com.laterbox.app.media.domain.model.SpotifyTrackMetadata
import com.laterbox.app.media.data.adapters.spotify.SpotifyMusicAdapter
import com.laterbox.app.media.data.adapters.x.XMediaAdapter
import com.laterbox.app.media.data.adapters.ytdlp.YtDlpAdapter
import com.laterbox.app.media.data.adapters.ytdlp.YtDlpOutputParser
import com.laterbox.app.media.domain.adapter.AdapterRegistry
import com.laterbox.app.media.domain.adapter.MediaPlatformAdapter
import com.laterbox.app.media.domain.adapter.PlatformResolver
import com.laterbox.app.media.domain.model.FailureCode
import com.laterbox.app.media.domain.model.InspectionResult
import com.laterbox.app.media.domain.model.MediaType
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InspectionTimeoutRegressionTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun regression_youtubeAdapter_has90SecondTimeoutPolicy() {
        val adapter = YtDlpAdapter(context)
        assertEquals("YouTube / yt-dlp adapter default budget must be 90 seconds", 90_000L, adapter.inspectionTimeoutMs)
        assertEquals(90_000L, YtDlpAdapter.DEFAULT_INSPECTION_TIMEOUT_MS)
    }

    @Test
    fun regression_extractionTakingLongerThanLegacy25s_isNotPrematurelyCancelledUnderExtendedBudget() = runBlocking {
        val sampleJson = """
            {
              "id": "slow_video_id",
              "title": "Slow Extracting Video",
              "extractor": "youtube",
              "duration": 120,
              "formats": [
                {
                  "format_id": "18",
                  "ext": "mp4",
                  "vcodec": "avc1",
                  "acodec": "mp4a",
                  "width": 640,
                  "height": 360,
                  "filesize": 5000000
                }
              ]
            }
        """.trimIndent()

        val sampleResponse = YoutubeDLResponse(emptyList(), 0, 80L, sampleJson, "")

        // In the legacy code, inspection had a 25s inner and 30s outer timeout.
        // Under our new architecture, the adapter receives its full budget (tested here scaled to 250ms for an 80ms extraction)
        // proving an extraction exceeding the legacy cutoff (e.g. 50ms) is not prematurely cancelled.
        val adapter = YtDlpAdapter(context, customExecutor = {
            Thread.sleep(80L)
            sampleResponse
        }, timeoutMs = 250L)

        val registry = AdapterRegistry(listOf(adapter))
        val resolver = PlatformResolver(registry)

        val result = resolver.resolveAndInspect("https://www.youtube.com/watch?v=slow_video_id")
        assertTrue("Inspection taking 80ms must succeed within 250ms budget", result is InspectionResult.Available)
        val available = result as InspectionResult.Available
        assertEquals("Slow Extracting Video", available.title)
        assertTrue(available.candidates.isNotEmpty())
    }

    @Test
    fun regression_youtubeDeadline_isEnforced_producingExtractionTimeout() = runBlocking {
        // Simulates an extraction hanging past its configured deadline
        val adapter = FakeHangingAdapter(
            id = "ytdlp",
            inspectionTimeoutMs = 60L,
            delayMs = 200L
        )

        val registry = AdapterRegistry(listOf(adapter))
        val resolver = PlatformResolver(registry)

        val result = resolver.resolveAndInspect("https://www.youtube.com/watch?v=hanging_video")
        assertTrue("Inspection hanging past deadline must fail with EXTRACTION_TIMEOUT", result is InspectionResult.Failed)
        val failed = result as InspectionResult.Failed
        assertEquals(FailureCode.EXTRACTION_TIMEOUT, failed.code)
        assertTrue(failed.retryable)
    }

    @Test
    fun regression_otherAdapters_retainAppropriateTimeouts() {
        val directAdapter = DirectUrlAdapter()
        assertEquals("DirectUrlAdapter must have 15s timeout", 15_000L, directAdapter.inspectionTimeoutMs)

        val metadataAdapter = MetadataOnlyAdapter()
        assertEquals("MetadataOnlyAdapter must have 15s timeout", 15_000L, metadataAdapter.inspectionTimeoutMs)

        val fakeSpotifyDiscovery = object : MusicDiscoveryProvider {
            override suspend fun discoverCandidates(spotify: SpotifyTrackMetadata): List<RawMusicCandidate> = emptyList()
        }
        val spotifyAdapter = SpotifyMusicAdapter(musicDiscoveryProvider = fakeSpotifyDiscovery)
        assertEquals("SpotifyMusicAdapter must have 30s timeout", 30_000L, spotifyAdapter.inspectionTimeoutMs)

        val ytDlpAdapter = YtDlpAdapter(context)
        assertEquals("YtDlpAdapter must have 90s timeout", 90_000L, ytDlpAdapter.inspectionTimeoutMs)

        val xAdapterWithFallback = XMediaAdapter(fallbackYtDlpAdapter = ytDlpAdapter)
        assertEquals("XMediaAdapter with fallback must inherit yt-dlp timeout (90s)", 90_000L, xAdapterWithFallback.inspectionTimeoutMs)

        val xAdapterWithoutFallback = XMediaAdapter(fallbackYtDlpAdapter = null)
        assertEquals("XMediaAdapter without fallback must have 25s timeout", 25_000L, xAdapterWithoutFallback.inspectionTimeoutMs)
    }

    @Test
    fun regression_cancellation_terminatesExecutionCleanly() = runBlocking {
        val adapter = YtDlpAdapter(context, customExecutor = {
            throw kotlinx.coroutines.CancellationException("Inspection cancelled by user")
        })

        try {
            adapter.inspect("https://youtube.com/watch?v=cancelled_id", processId = "inspect_user_cancel")
            org.junit.Assert.fail("Expected CancellationException to be thrown")
        } catch (ce: kotlinx.coroutines.CancellationException) {
            assertEquals("Inspection cancelled by user", ce.message)
        }
    }

    @Test
    fun regression_successfulInspection_returnsActualDownloadableFormatCandidates_notMerelyMetadataOrOg() {
        val youtubeDumpJson = """
            {
              "id": "dQw4w9WgXcQ",
              "title": "Rick Astley - Never Gonna Give You Up",
              "extractor": "youtube",
              "duration": 213,
              "thumbnail": "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
              "formats": [
                {
                  "format_id": "18",
                  "ext": "mp4",
                  "vcodec": "avc1.42001E",
                  "acodec": "mp4a.40.2",
                  "width": 640,
                  "height": 360,
                  "filesize": 15000000
                },
                {
                  "format_id": "137",
                  "ext": "mp4",
                  "vcodec": "avc1.640028",
                  "acodec": "none",
                  "width": 1920,
                  "height": 1080,
                  "filesize": 45000000
                },
                {
                  "format_id": "140",
                  "ext": "m4a",
                  "vcodec": "none",
                  "acodec": "mp4a.40.2",
                  "filesize": 3500000
                }
              ]
            }
        """.trimIndent()

        val parsed = YtDlpOutputParser.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ", youtubeDumpJson)
        assertTrue(parsed is InspectionResult.Available)
        val available = parsed as InspectionResult.Available
        assertEquals("youtube", available.platform.lowercase())
        assertEquals("Rick Astley - Never Gonna Give You Up", available.title)

        // Must have real video candidates
        val videoCandidates = available.candidates.filter { it.mediaType == MediaType.VIDEO }
        assertTrue("Must return downloadable video format candidates", videoCandidates.isNotEmpty())

        // Must not be audio-only
        val hasVideoPayload = videoCandidates.any { it.width != null && it.height != null }
        assertTrue("Video candidate must have video dimensions", hasVideoPayload)

        // Verifying that a merge format candidate exists for 1080p
        val hdCandidate = videoCandidates.find { it.height == 1080 }
        assertNotNull(hdCandidate)
        assertTrue("1080p candidate without native audio must require merge", hdCandidate?.requiresMerge == true)

        // Failure/timeout must never convert to available with fake preview candidates
        val timeoutResult = InspectionResult.Failed(FailureCode.EXTRACTION_TIMEOUT, "Timed out", retryable = true)
        val preservedResult = YtDlpOutputParser.attachPreviewImageFallback(timeoutResult, "https://example.com/og.jpg")
        assertTrue("Timeout must remain Failed, never converted to Available", preservedResult is InspectionResult.Failed)
        assertEquals(FailureCode.EXTRACTION_TIMEOUT, (preservedResult as InspectionResult.Failed).code)
    }

    private class FakeHangingAdapter(
        override val id: String,
        override val inspectionTimeoutMs: Long,
        private val delayMs: Long
    ) : MediaPlatformAdapter {
        override fun canHandle(url: String): Boolean = true
        override suspend fun inspect(url: String): InspectionResult {
            delay(delayMs)
            return InspectionResult.Unsupported("Done")
        }
    }
}
