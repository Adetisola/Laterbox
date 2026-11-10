package com.laterbox.app.media.data.adapters.direct

import com.laterbox.app.media.domain.model.InspectionResult
import com.laterbox.app.media.domain.model.MediaType
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DirectUrlAdapterTest {

    private lateinit var server: MockWebServer
    private lateinit var adapter: DirectUrlAdapter

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        adapter = DirectUrlAdapter()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun canHandle_matchesExpectedExtensions() {
        assertTrue(adapter.canHandle("https://example.com/photo.jpg"))
        assertTrue(adapter.canHandle("https://example.com/clip.mp4"))
        assertTrue(adapter.canHandle("https://example.com/song.mp3"))
        assertTrue(adapter.canHandle("https://example.com/document.pdf"))

        assertFalse(adapter.canHandle("https://example.com/article"))
        assertFalse(adapter.canHandle("https://example.com/page.html"))
        assertFalse(adapter.canHandle("https://example.com/script.js"))
    }

    @Test
    fun inspect_returnsAvailableWithCandidate_forValidDirectMedia() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "video/mp4")
                .setHeader("Content-Length", "10485760")
                .setHeader("Content-Disposition", "inline; filename=\"sample_clip.mp4\"")
        )

        val targetUrl = server.url("/sample_clip.mp4").toString()
        val result = adapter.inspect(targetUrl)

        assertTrue(result is InspectionResult.Available)
        val available = result as InspectionResult.Available
        assertEquals("sample_clip.mp4", available.title)
        assertEquals(1, available.candidates.size)

        val candidate = available.candidates.first()
        assertEquals(MediaType.VIDEO, candidate.mediaType)
        assertEquals(10485760L, candidate.estimatedBytes)
        assertEquals("MP4", candidate.formatLabel)
    }

    @Test
    fun inspect_maps401and403_toNeedsAuth() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))

        val result = adapter.inspect(server.url("/secure_video.mp4").toString())
        assertTrue(result is InspectionResult.NeedsAuth)
    }

    @Test
    fun inspect_maps404_toUnsupported() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = adapter.inspect(server.url("/non_existent.pdf").toString())
        assertTrue(result is InspectionResult.Unsupported)
    }
}
