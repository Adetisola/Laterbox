package com.laterbox.app.capture.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionTextExtractorTest {

    private val ownPackage = "com.laterbox.app"

    @Test
    fun extractFromRawSelection_validNodeSubstring_returnsCandidate() {
        val fullText = "Please see https://example.com/docs for details."
        val url = "https://example.com/docs"
        val start = fullText.indexOf(url)
        val end = start + url.length

        val outcome = SelectionTextExtractor.extractFromRawSelection(
            nodeText = fullText,
            hintText = null,
            selStart = start,
            selEnd = end,
            eventText = null,
            fromIndex = -1,
            toIndex = -1,
            isPassword = false,
            packageName = "org.mozilla.firefox",
            ownPackage = ownPackage,
            excludedPackages = emptySet(),
            bounds = null
        )

        assertTrue(outcome is SelectionEventOutcome.CandidateText)
        assertEquals("https://example.com/docs", (outcome as SelectionEventOutcome.CandidateText).text)
    }

    @Test
    fun extractFromRawSelection_selectionCleared_returnsSelectionCleared() {
        val outcome = SelectionTextExtractor.extractFromRawSelection(
            nodeText = "Some text here",
            hintText = null,
            selStart = 5,
            selEnd = 5, // cursor placed, nothing selected
            eventText = null,
            fromIndex = -1,
            toIndex = -1,
            isPassword = false,
            packageName = "org.mozilla.firefox",
            ownPackage = ownPackage,
            excludedPackages = emptySet(),
            bounds = null
        )

        assertTrue(outcome is SelectionEventOutcome.SelectionCleared)
    }

    @Test
    fun extractFromRawSelection_passwordField_isIgnored() {
        val outcome = SelectionTextExtractor.extractFromRawSelection(
            nodeText = "https://secret.com",
            hintText = null,
            selStart = 0,
            selEnd = 18,
            eventText = null,
            fromIndex = -1,
            toIndex = -1,
            isPassword = true,
            packageName = "com.example.app",
            ownPackage = ownPackage,
            excludedPackages = emptySet(),
            bounds = null
        )

        assertEquals(SelectionEventOutcome.Ignored, outcome)
    }

    @Test
    fun extractFromRawSelection_ownPackage_isIgnored() {
        val outcome = SelectionTextExtractor.extractFromRawSelection(
            nodeText = "https://example.com",
            hintText = null,
            selStart = 0,
            selEnd = 19,
            eventText = null,
            fromIndex = -1,
            toIndex = -1,
            isPassword = false,
            packageName = ownPackage,
            ownPackage = ownPackage,
            excludedPackages = emptySet(),
            bounds = null
        )

        assertEquals(SelectionEventOutcome.Ignored, outcome)
    }

    @Test
    fun extractFromRawSelection_excludedPackage_isIgnored() {
        val outcome = SelectionTextExtractor.extractFromRawSelection(
            nodeText = "https://banking.com",
            hintText = null,
            selStart = 0,
            selEnd = 19,
            eventText = null,
            fromIndex = -1,
            toIndex = -1,
            isPassword = false,
            packageName = "com.bank.app",
            ownPackage = ownPackage,
            excludedPackages = setOf("com.bank.app"),
            bounds = null
        )

        assertEquals(SelectionEventOutcome.Ignored, outcome)
    }

    @Test
    fun extractFromRawSelection_eventTextFallback_returnsCandidate() {
        val outcome = SelectionTextExtractor.extractFromRawSelection(
            nodeText = null,
            hintText = null,
            selStart = -1,
            selEnd = -1,
            eventText = "https://github.com/trending",
            fromIndex = 0,
            toIndex = 27,
            isPassword = false,
            packageName = "com.news.reader",
            ownPackage = ownPackage,
            excludedPackages = emptySet(),
            bounds = null
        )

        assertTrue(outcome is SelectionEventOutcome.CandidateText)
        assertEquals("https://github.com/trending", (outcome as SelectionEventOutcome.CandidateText).text)
    }
}
