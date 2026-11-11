package com.laterbox.app.media.data.adapters.ytdlp

import com.laterbox.app.media.domain.model.InspectionResult
import com.laterbox.app.media.domain.model.MediaType
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class Stage0OEmbedFetcherTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun toInspectionResult_createsIncompleteAvailableWithPreviewCandidate() {
        val fetcher = Stage0OEmbedFetcher()
        val stage0 = Stage0Result(
            title = "Test Video Title",
            author = "Test Author",
            thumbnailUrl = "https://example.com/thumb.jpg",
            platform = "YouTube"
        )

        val result = fetcher.toInspectionResult(stage0)
        assertFalse("Stage 0 result must be marked as not complete", result.isComplete)
        assertEquals("Test Video Title", result.title)
        assertEquals("https://example.com/thumb.jpg", result.thumbnailUrl)
        assertEquals(1, result.candidates.size)

        val candidate = result.candidates.first()
        assertEquals(MediaType.IMAGE, candidate.mediaType)
        assertEquals("Preview image only", candidate.formatLabel)
        assertTrue(candidate.isThumbnail)
        assertTrue(candidate.isOgFallback)
    }

    @Test
    fun toInspectionResult_emptyThumbnail_returnsZeroCandidates() {
        val fetcher = Stage0OEmbedFetcher()
        val stage0 = Stage0Result(
            title = "Text Only Post",
            author = "Author",
            thumbnailUrl = null,
            platform = "Twitter"
        )

        val result = fetcher.toInspectionResult(stage0)
        assertFalse(result.isComplete)
        assertTrue(result.candidates.isEmpty())
        assertEquals("Text Only Post", result.title)
    }

    @Test
    fun fetch_openGraphFallback_parsesHtmlMetaTags() = runBlocking {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta property="og:title" content="OpenGraph Article Title" />
                <meta property="og:image" content="https://example.com/og_banner.jpg" />
                <meta property="og:site_name" content="TechBlog" />
            </head>
            <body></body>
            </html>
        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody(html)
        )

        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()

        val fetcher = Stage0OEmbedFetcher(client)
        val url = server.url("/article/123").toString()

        val result = fetcher.fetch(url)
        assertNotNull(result)
        assertEquals("OpenGraph Article Title", result?.title)
        assertEquals("https://example.com/og_banner.jpg", result?.thumbnailUrl)
    }
}
