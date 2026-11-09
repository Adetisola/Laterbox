package com.laterbox.app.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlValidatorTest {

    @Test
    fun isValidUrl_validatesStandardHttpAndHttps() {
        assertTrue(UrlValidator.isValidUrl("https://example.com"))
        assertTrue(UrlValidator.isValidUrl("http://example.com/path?arg=1"))
        assertTrue(UrlValidator.isValidUrl("https://github.com/Adetisola/Laterbox"))
        assertTrue(UrlValidator.isValidUrl("nytimes.com/article"))
    }

    @Test
    fun isValidUrl_rejectsInvalidAndDangerousSchemes() {
        assertFalse(UrlValidator.isValidUrl(null))
        assertFalse(UrlValidator.isValidUrl(""))
        assertFalse(UrlValidator.isValidUrl("   "))
        assertFalse(UrlValidator.isValidUrl("just random text"))
        assertFalse(UrlValidator.isValidUrl("javascript:alert(1)"))
        assertFalse(UrlValidator.isValidUrl("file:///data/user/0/com.app"))
        assertFalse(UrlValidator.isValidUrl("content://media/external/images"))
    }

    @Test
    fun extractUrl_extractsUrlFromPastedText() {
        val commentary = "Hey, look at this article: https://news.ycombinator.com/item?id=123 from earlier today."
        val extracted = UrlValidator.extractUrl(commentary)
        assertNotNull(extracted)
        assertEquals("https://news.ycombinator.com/item?id=123", extracted)
    }

    @Test
    fun extractUrl_extractsBareDomain() {
        val extracted = UrlValidator.extractUrl("nytimes.com/article")
        assertNotNull(extracted)
        assertEquals("https://nytimes.com/article", extracted)
    }

    @Test
    fun extractUrl_returnsNullWhenNoUrlPresent() {
        assertNull(UrlValidator.extractUrl("Just a shopping list with groceries and milk"))
        assertNull(UrlValidator.extractUrl(""))
        assertNull(UrlValidator.extractUrl(null))
    }

    @Test
    fun normalize_addsHttpsSchemeWhenMissing() {
        assertEquals("https://example.com", UrlValidator.normalize("example.com"))
        assertEquals("http://example.com", UrlValidator.normalize("http://example.com"))
        assertEquals("https://example.com", UrlValidator.normalize("https://example.com"))
    }
}
