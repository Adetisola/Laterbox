package com.laterbox.app.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UrlNormalizerTest {

    @Test
    fun stripsTrackingParameters() {
        val url1 = "https://example.com/page?utm_source=twitter&id=42"
        val url2 = "https://example.com/page?id=42"
        assertEquals(UrlNormalizer.normalize(url1), UrlNormalizer.normalize(url2))
        assertEquals("https://example.com/page?id=42", UrlNormalizer.normalize(url1))
    }

    @Test
    fun sortsQueryParametersAlphabetically() {
        val url1 = "https://example.com/page?b=2&a=1"
        val url2 = "https://example.com/page?a=1&b=2"
        assertEquals(UrlNormalizer.normalize(url1), UrlNormalizer.normalize(url2))
        assertEquals("https://example.com/page?a=1&b=2", UrlNormalizer.normalize(url1))
    }

    @Test
    fun normalizesHttpToHttpsAndTrimsTrailingSlash() {
        val urlHttp = "http://example.com/article/"
        val urlHttps = "https://example.com/article"
        assertEquals(UrlNormalizer.normalize(urlHttp), UrlNormalizer.normalize(urlHttps))
        assertEquals("https://example.com/article", UrlNormalizer.normalize(urlHttp))
    }

    @Test
    fun normalizesWwwPrefix() {
        val urlWithWww = "https://www.example.com/article"
        val urlWithoutWww = "https://example.com/article"
        assertEquals(UrlNormalizer.normalize(urlWithWww), UrlNormalizer.normalize(urlWithoutWww))
    }

    @Test
    fun normalizesYouTubeShortlinkToCanonical() {
        val shortUrl = "https://youtu.be/dQw4w9WgXcQ"
        val longUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        assertEquals(UrlNormalizer.normalize(shortUrl), UrlNormalizer.normalize(longUrl))
        assertEquals("https://youtube.com/watch?v=dQw4w9WgXcQ", UrlNormalizer.normalize(shortUrl))
    }

    @Test
    fun preservesYouTubeTimestamp() {
        val urlWithTime = "https://youtu.be/dQw4w9WgXcQ?t=60"
        val urlDifferentTime = "https://youtu.be/dQw4w9WgXcQ?t=120"
        assertNotEquals(UrlNormalizer.normalize(urlWithTime), UrlNormalizer.normalize(urlDifferentTime))
    }

    @Test
    fun preservesSubdomainsExceptWhitelistedMobile() {
        // Distinct subdomains should NOT be merged
        val docsUrl = "https://docs.example.com"
        val mainUrl = "https://example.com"
        assertNotEquals(UrlNormalizer.normalize(docsUrl), UrlNormalizer.normalize(mainUrl))

        // Whitelisted mobile subdomains should be normalized
        val mobileWiki = "https://m.wikipedia.org/wiki/Kotlin"
        val desktopWiki = "https://wikipedia.org/wiki/Kotlin"
        assertEquals(UrlNormalizer.normalize(mobileWiki), UrlNormalizer.normalize(desktopWiki))
    }

    @Test
    fun preservesSpaHashRoutesWhileStrippingAnchorTags() {
        // SPA hash paths are preserved
        val spaRoute1 = "https://app.com/#/projects"
        val spaRoute2 = "https://app.com/#/settings"
        assertNotEquals(UrlNormalizer.normalize(spaRoute1), UrlNormalizer.normalize(spaRoute2))
        assertEquals("https://app.com/#/projects", UrlNormalizer.normalize(spaRoute1))

        // Standard document anchors are stripped
        val docAnchor1 = "https://blog.com/post#comments"
        val docAnchor2 = "https://blog.com/post#summary"
        assertEquals(UrlNormalizer.normalize(docAnchor1), UrlNormalizer.normalize(docAnchor2))
        assertEquals("https://blog.com/post", UrlNormalizer.normalize(docAnchor1))
    }

    @Test
    fun extractsDomainCleanly() {
        assertEquals("youtube.com", UrlNormalizer.extractDomain("https://www.youtube.com/watch?v=123"))
        assertEquals("github.com", UrlNormalizer.extractDomain("https://github.com/Adetisola/Laterbox"))
        assertEquals("theverge.com", UrlNormalizer.extractDomain("http://theverge.com/tech"))
    }
}
