package com.laterbox.app.capture.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionPromptDeduplicatorTest {

    private val now = 1700000000000L

    @Test
    fun shouldShowPrompt_initialUrl_returnsTrue() {
        val deduplicator = SelectionPromptDeduplicator()
        assertTrue(deduplicator.shouldShowPrompt("https://example.com/test", "com.browser.app", now))
    }

    @Test
    fun shouldShowPrompt_alreadyCurrentlyShown_returnsFalse() {
        val deduplicator = SelectionPromptDeduplicator()
        deduplicator.markPromptShown("https://example.com/test", "com.browser.app")
        assertFalse(deduplicator.shouldShowPrompt("https://example.com/test", "com.browser.app", now))
    }

    @Test
    fun shouldShowPrompt_recentlyDismissedWithin60s_returnsFalse() {
        val deduplicator = SelectionPromptDeduplicator()
        deduplicator.markPromptDismissed("https://example.com/test", now)
        assertFalse(deduplicator.shouldShowPrompt("https://example.com/test", "com.browser.app", now + 30_000L))
    }

    @Test
    fun shouldShowPrompt_dismissedAfter60s_returnsTrue() {
        val deduplicator = SelectionPromptDeduplicator()
        deduplicator.markPromptDismissed("https://example.com/test", now)
        assertTrue(deduplicator.shouldShowPrompt("https://example.com/test", "com.browser.app", now + 61_000L))
    }

    @Test
    fun shouldShowPrompt_recentlySavedWithin10m_returnsFalse() {
        val deduplicator = SelectionPromptDeduplicator()
        deduplicator.markPromptSaved("https://example.com/test", now)
        assertFalse(deduplicator.shouldShowPrompt("https://example.com/test", "com.browser.app", now + 300_000L))
    }

    @Test
    fun shouldDismissOnWindowStateChange_systemUIOrSameApp_doesNotDismiss() {
        val deduplicator = SelectionPromptDeduplicator()
        deduplicator.markPromptShown("https://example.com/test", "com.browser.app")

        // Temporary action mode toolbar / system UI popup should NOT dismiss the prompt
        assertFalse(deduplicator.shouldDismissOnWindowStateChange("com.android.systemui"))
        assertFalse(deduplicator.shouldDismissOnWindowStateChange("com.google.android.inputmethod.latin"))
        assertFalse(deduplicator.shouldDismissOnWindowStateChange("com.browser.app"))
    }

    @Test
    fun shouldDismissOnWindowStateChange_differentApp_dismisses() {
        val deduplicator = SelectionPromptDeduplicator()
        deduplicator.markPromptShown("https://example.com/test", "com.browser.app")

        // User switched to another app
        assertTrue(deduplicator.shouldDismissOnWindowStateChange("com.other.application"))
    }
}
