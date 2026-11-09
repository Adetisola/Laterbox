package com.laterbox.app.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ClipboardSuggestionManagerTest {

    private val now = 1700000000000L // arbitrary fixed timestamp

    @Test
    fun evaluateCandidate_validFreshLink_returnsSuggestion() {
        val result = ClipboardSuggestionManager.evaluateCandidate(
            rawText = "https://example.com/fresh-article",
            isSensitive = false,
            clipTimestampMs = now - 60_000L, // 1 min ago
            nowMs = now,
            clipboardEnabled = true,
            recentHistoryEntries = emptySet(),
            isAlreadySaved = { false }
        )

        assertNotNull(result)
        assertEquals("https://example.com/fresh-article", result?.url)
        assertEquals("example.com", result?.domain)
        assertNotNull(result?.hash)
    }

    @Test
    fun evaluateCandidate_whenDisabled_returnsNull() {
        val result = ClipboardSuggestionManager.evaluateCandidate(
            rawText = "https://example.com/link",
            isSensitive = false,
            clipTimestampMs = now,
            nowMs = now,
            clipboardEnabled = false,
            recentHistoryEntries = emptySet(),
            isAlreadySaved = { false }
        )

        assertNull(result)
    }

    @Test
    fun evaluateCandidate_whenSensitive_returnsNull() {
        val result = ClipboardSuggestionManager.evaluateCandidate(
            rawText = "https://example.com/secret",
            isSensitive = true,
            clipTimestampMs = now,
            nowMs = now,
            clipboardEnabled = true,
            recentHistoryEntries = emptySet(),
            isAlreadySaved = { false }
        )

        assertNull(result)
    }

    @Test
    fun evaluateCandidate_whenStaleClipOlderThan12Hours_returnsNull() {
        val thirteenHoursAgo = now - (13 * 3600 * 1000L)
        val result = ClipboardSuggestionManager.evaluateCandidate(
            rawText = "https://example.com/old",
            isSensitive = false,
            clipTimestampMs = thirteenHoursAgo,
            nowMs = now,
            clipboardEnabled = true,
            recentHistoryEntries = emptySet(),
            isAlreadySaved = { false }
        )

        assertNull(result)
    }

    @Test
    fun evaluateCandidate_whenAlreadySavedInDatabase_returnsNull() {
        val result = ClipboardSuggestionManager.evaluateCandidate(
            rawText = "https://example.com/existing",
            isSensitive = false,
            clipTimestampMs = now - 1000L,
            nowMs = now,
            clipboardEnabled = true,
            recentHistoryEntries = emptySet(),
            isAlreadySaved = { url -> url == "https://example.com/existing" }
        )

        assertNull(result)
    }

    @Test
    fun evaluateCandidate_whenInRecentHistoryWithinRetention_returnsNull() {
        val hash = ClipboardSuggestionManager.computeHash("https://example.com/handled")
        val threeDaysAgo = now - (3 * 24 * 3600 * 1000L)
        val history = setOf("$hash:$threeDaysAgo")

        val result = ClipboardSuggestionManager.evaluateCandidate(
            rawText = "https://example.com/handled",
            isSensitive = false,
            clipTimestampMs = now - 1000L,
            nowMs = now,
            clipboardEnabled = true,
            recentHistoryEntries = history,
            isAlreadySaved = { false }
        )

        assertNull(result)
    }

    @Test
    fun evaluateCandidate_whenHistoryEntryExpiredPast7Days_allowsSuggestion() {
        val hash = ClipboardSuggestionManager.computeHash("https://example.com/expired-history")
        val eightDaysAgo = now - (8 * 24 * 3600 * 1000L)
        val history = setOf("$hash:$eightDaysAgo")

        val result = ClipboardSuggestionManager.evaluateCandidate(
            rawText = "https://example.com/expired-history",
            isSensitive = false,
            clipTimestampMs = now - 1000L,
            nowMs = now,
            clipboardEnabled = true,
            recentHistoryEntries = history,
            isAlreadySaved = { false }
        )

        assertNotNull(result)
        assertEquals("https://example.com/expired-history", result?.url)
    }
}
