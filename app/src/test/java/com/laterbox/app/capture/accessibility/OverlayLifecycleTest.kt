package com.laterbox.app.capture.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayLifecycleTest {

    @Test
    fun `shouldDismissOnWindowStateChange preserves prompt for keyboard and system UI`() {
        val deduplicator = SelectionPromptDeduplicator()
        deduplicator.markPromptShown("https://example.com/article", "com.browser.app")

        // Should not dismiss when keyboards appear
        assertFalse(deduplicator.shouldDismissOnWindowStateChange("com.google.android.inputmethod.latin", "com.laterbox.app"))
        assertFalse(deduplicator.shouldDismissOnWindowStateChange("com.samsung.android.honeyboard", "com.laterbox.app"))
        assertFalse(deduplicator.shouldDismissOnWindowStateChange("com.android.systemui", "com.laterbox.app"))

        // Should not dismiss for same source app
        assertFalse(deduplicator.shouldDismissOnWindowStateChange("com.browser.app", "com.laterbox.app"))

        // Should not dismiss for Laterbox itself
        assertFalse(deduplicator.shouldDismissOnWindowStateChange("com.laterbox.app", "com.laterbox.app"))

        // Should dismiss when genuinely switching to another distinct third-party app
        assertTrue(deduplicator.shouldDismissOnWindowStateChange("com.twitter.android", "com.laterbox.app"))
    }
}
