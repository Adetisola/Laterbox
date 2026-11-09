package com.laterbox.app.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlCaptureExtractorTest {

    @Test
    fun extract_singleStandardUrl_returnsSingleUrl() {
        val result = UrlCaptureExtractor.extract("https://example.com/blog/post-1")
        assertTrue(result is UrlExtractionResult.SingleUrl)
        val single = result as UrlExtractionResult.SingleUrl
        assertEquals("https://example.com/blog/post-1", single.normalizedUrl)
        assertEquals("example.com", single.domain)
    }

    @Test
    fun extract_urlWithWww_normalizesCorrectly() {
        val result = UrlCaptureExtractor.extract("www.nytimes.com/section/technology")
        assertTrue(result is UrlExtractionResult.SingleUrl)
        val single = result as UrlExtractionResult.SingleUrl
        assertEquals("https://nytimes.com/section/technology", single.normalizedUrl)
        assertEquals("nytimes.com", single.domain)
    }

    @Test
    fun extract_urlWithSurroundingProse_extractsCleanUrl() {
        val input = "Have you read this amazing article: https://news.ycombinator.com/item?id=42? Check it out!"
        val result = UrlCaptureExtractor.extract(input)
        assertTrue(result is UrlExtractionResult.SingleUrl)
        val single = result as UrlExtractionResult.SingleUrl
        assertEquals("https://news.ycombinator.com/item?id=42", single.normalizedUrl)
        assertEquals("news.ycombinator.com", single.domain)
    }

    @Test
    fun extract_wikipediaBalancedParentheses_preservesClosingParen() {
        val input = "https://en.wikipedia.org/wiki/Kotlin_(programming_language)"
        val result = UrlCaptureExtractor.extract(input)
        assertTrue(result is UrlExtractionResult.SingleUrl)
        val single = result as UrlExtractionResult.SingleUrl
        assertEquals("https://wikipedia.org/wiki/Kotlin_(programming_language)", single.normalizedUrl)
    }

    @Test
    fun extract_unbalancedParentheses_stripsTrailingParen() {
        val input = "(see https://example.com/reference)"
        val result = UrlCaptureExtractor.extract(input)
        assertTrue(result is UrlExtractionResult.SingleUrl)
        val single = result as UrlExtractionResult.SingleUrl
        assertEquals("https://example.com/reference", single.normalizedUrl)
    }

    @Test
    fun extract_trailingPunctuation_isStripped() {
        val cases = listOf(
            "https://example.com/test." to "https://example.com/test",
            "https://example.com/test," to "https://example.com/test",
            "https://example.com/test!" to "https://example.com/test",
            "\"https://example.com/test\"" to "https://example.com/test",
            "<https://example.com/test>" to "https://example.com/test"
        )
        for ((input, expected) in cases) {
            val result = UrlCaptureExtractor.extract(input)
            assertTrue("Failed for input: $input", result is UrlExtractionResult.SingleUrl)
            assertEquals(expected, (result as UrlExtractionResult.SingleUrl).normalizedUrl)
        }
    }

    @Test
    fun extract_emailAddress_isIgnored() {
        val result = UrlCaptureExtractor.extract("Please send questions to hello@laterbox.app today.")
        assertEquals(UrlExtractionResult.NoValidUrl, result)
    }

    @Test
    fun extract_plainTextAndSentences_isIgnored() {
        val result = UrlCaptureExtractor.extract("This is just an ordinary sentence. Nothing to save.")
        assertEquals(UrlExtractionResult.NoValidUrl, result)
    }

    @Test
    fun extract_phoneNumbers_isIgnored() {
        val result = UrlCaptureExtractor.extract("+1-800-555-0199 or (555) 123-4567")
        assertEquals(UrlExtractionResult.NoValidUrl, result)
    }

    @Test
    fun extract_filenamesWithoutScheme_areIgnored() {
        val cases = listOf(
            "annual_report.pdf",
            "screenshot.png",
            "Main.kt",
            "archive.zip",
            "data.json",
            "v1.0.12"
        )
        for (case in cases) {
            val result = UrlCaptureExtractor.extract(case)
            assertEquals("Should ignore $case", UrlExtractionResult.NoValidUrl, result)
        }
    }

    @Test
    fun extract_nonHttpSchemes_areIgnored() {
        val cases = listOf(
            "javascript:alert('pwn')",
            "file:///sdcard/Download/test.html",
            "content://media/external/images/1",
            "mailto:test@example.com"
        )
        for (case in cases) {
            val result = UrlCaptureExtractor.extract(case)
            assertEquals("Should ignore $case", UrlExtractionResult.NoValidUrl, result)
        }
    }

    @Test
    fun extract_multipleDistinctUrls_returnsMultipleUrls() {
        val input = "Compare https://kotlinlang.org with https://developer.android.com for more info."
        val result = UrlCaptureExtractor.extract(input)
        assertTrue(result is UrlExtractionResult.MultipleUrls)
        val multiple = result as UrlExtractionResult.MultipleUrls
        assertEquals(2, multiple.urls.size)
        assertEquals("https://kotlinlang.org", multiple.urls[0].normalizedUrl)
        assertEquals("https://developer.android.com", multiple.urls[1].normalizedUrl)
    }

    @Test
    fun extract_duplicateUrlsInSameText_deduplicatesToSingleUrl() {
        val input = "Check https://example.com/page and here again https://example.com/page"
        val result = UrlCaptureExtractor.extract(input)
        assertTrue(result is UrlExtractionResult.SingleUrl)
        val single = result as UrlExtractionResult.SingleUrl
        assertEquals("https://example.com/page", single.normalizedUrl)
    }
}
