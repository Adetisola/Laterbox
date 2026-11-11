package com.laterbox.app.media.data.adapters.tiktok

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class TikTokUrlResolverTest {

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
    fun isTikTokShortLink_detectsKnownShortLinkDomains() {
        assertTrue(TikTokUrlResolver.isTikTokShortLink("https://vm.tiktok.com/ZM8vN7abc/"))
        assertTrue(TikTokUrlResolver.isTikTokShortLink("http://vt.tiktok.com/ZS9x123/"))
        assertTrue(TikTokUrlResolver.isTikTokShortLink("https://www.tiktok.com/t/ZT8foo123/"))
        assertTrue(TikTokUrlResolver.isTikTokShortLink("https://tiktok.com/t/ZT8foo123/"))

        assertFalse(TikTokUrlResolver.isTikTokShortLink("https://www.tiktok.com/@creator/video/71234567890"))
        assertFalse(TikTokUrlResolver.isTikTokShortLink("https://youtube.com/watch?v=abcdef"))
        assertFalse(TikTokUrlResolver.isTikTokShortLink("https://instagram.com/reel/12345"))
    }

    @Test
    fun isTikTokUrl_detectsTikTokUrls() {
        assertTrue(TikTokUrlResolver.isTikTokUrl("https://www.tiktok.com/@user/video/123"))
        assertTrue(TikTokUrlResolver.isTikTokUrl("https://vm.tiktok.com/abc"))
        assertTrue(TikTokUrlResolver.isTikTokUrl("https://m.tiktok.com/v/123.html"))

        assertFalse(TikTokUrlResolver.isTikTokUrl("https://youtube.com/watch?v=123"))
        assertFalse(TikTokUrlResolver.isTikTokUrl("https://x.com/status/123"))
    }

    @Test
    fun resolveCanonicalUrl_nonShortLink_returnsOriginalImmediately() = runBlocking {
        val resolver = TikTokUrlResolver()
        val standardUrl = "https://www.tiktok.com/@creator/video/71234567890"
        val result = resolver.resolveCanonicalUrl(standardUrl)
        assertEquals(standardUrl, result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun resolveCanonicalUrl_resolvesRedirectViaHead() = runBlocking {
        val targetPath = "/@creator/video/71234567890"
        val targetUrl = server.url(targetPath).toString()

        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", targetUrl)
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("ok")
        )

        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        val resolver = TikTokUrlResolver(client)
        // Simulate short link path on mock server
        val shortLinkUrl = server.url("/t/ZT8foo123/").toString()

        val resolved = resolver.resolveCanonicalUrl(shortLinkUrl)
        assertEquals(targetUrl, resolved)
    }

    @Test
    fun resolveCanonicalUrl_networkFailure_returnsOriginalUrl() = runBlocking {
        val client = OkHttpClient.Builder()
            .connectTimeout(500, TimeUnit.MILLISECONDS)
            .readTimeout(500, TimeUnit.MILLISECONDS)
            .build()

        val deadServer = MockWebServer()
        deadServer.start()
        val deadUrl = deadServer.url("/t/network_failure/").toString()
        deadServer.shutdown()

        val resolver = TikTokUrlResolver(client)

        // Should not throw, gracefully fall back to input URL on network failure
        val resolved = resolver.resolveCanonicalUrl(deadUrl)
        assertEquals(deadUrl, resolved)
    }
}
