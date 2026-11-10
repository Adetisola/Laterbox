package com.laterbox.app.media.data.adapters.ytdlp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.laterbox.app.media.domain.model.FailureCode
import com.laterbox.app.media.domain.model.InspectionResult
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YtDlpAdapterTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun canHandle_returnsTrueForWebUrlsExceptSpotify() {
        val adapter = YtDlpAdapter(context)

        assertTrue(adapter.canHandle("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(adapter.canHandle("https://x.com/someuser/status/123456789"))
        assertTrue(adapter.canHandle("https://www.tiktok.com/@user/video/123456789"))
        assertTrue(adapter.canHandle("https://www.reddit.com/r/Android/comments/abc/title/"))

        // Excludes Spotify
        assertFalse(adapter.canHandle("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT"))
        assertFalse(adapter.canHandle("https://spotify.link/xyz123"))

        // Excludes non-http
        assertFalse(adapter.canHandle("ftp://example.com/file"))
        assertFalse(adapter.canHandle("invalid-url"))
    }

    @Test
    fun inspect_withCustomExecutor_returnsParsedAvailableCandidates() = runBlocking {
        val sampleJson = """
            {
              "id": "mock_id",
              "title": "Mock Video Title",
              "extractor": "youtube",
              "duration": 60,
              "formats": [
                {
                  "format_id": "18",
                  "ext": "mp4",
                  "vcodec": "avc1",
                  "acodec": "mp4a",
                  "width": 640,
                  "height": 360,
                  "filesize": 10000000
                }
              ]
            }
        """.trimIndent()

        val mockResponse = YoutubeDLResponse(
            emptyList(),
            0,
            100L,
            sampleJson,
            ""
        )

        val adapter = YtDlpAdapter(context, customExecutor = { mockResponse })
        val result = adapter.inspect("https://youtube.com/watch?v=mock_id")

        assertTrue(result is InspectionResult.Available)
        val available = result as InspectionResult.Available
        assertEquals("Mock Video Title", available.title)
        assertEquals(2, available.candidates.size)
    }

    @Test
    fun inspect_withCustomExecutor_mapsErrorMessageToFailed() = runBlocking {
        val mockResponse = YoutubeDLResponse(
            emptyList(),
            1,
            50L,
            "",
            "ERROR: [youtube] video is private"
        )

        val adapter = YtDlpAdapter(context, customExecutor = { mockResponse })
        val result = adapter.inspect("https://youtube.com/watch?v=private_id")

        assertTrue(result is InspectionResult.Failed)
        assertEquals(FailureCode.PRIVATE_CONTENT, (result as InspectionResult.Failed).code)
    }
}
