package com.laterbox.app.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SharedTextParserTest {

    @Test
    fun parsesChromeShare() {
        val text = "https://developer.android.com/jetpack/compose"
        val subject = "Jetpack Compose | Android Developers"
        val result = SharedTextParser.parse(text, subject)

        assertNotNull(result)
        assertEquals("https://developer.android.com/jetpack/compose", result?.url)
        assertEquals("Jetpack Compose | Android Developers", result?.title)
    }

    @Test
    fun parsesYouTubeShare() {
        val text = "Check out this video: https://youtu.be/dQw4w9WgXcQ?si=abc"
        val subject = "Kotlin 2.0 Released!"
        val result = SharedTextParser.parse(text, subject)

        assertNotNull(result)
        assertEquals("https://youtu.be/dQw4w9WgXcQ?si=abc", result?.url)
        assertEquals("Kotlin 2.0 Released!", result?.title)
        assertEquals("Check out this video:", result?.note)
    }

    @Test
    fun parsesRedditOrMixedTextWithTrailingPunctuation() {
        val text = "Thoughtful architecture post: https://www.reddit.com/r/androiddev/comments/123/architecture/."
        val result = SharedTextParser.parse(text, null)

        assertNotNull(result)
        assertEquals("https://www.reddit.com/r/androiddev/comments/123/architecture/", result?.url)
        assertEquals("Thoughtful architecture post:", result?.title)
    }

    @Test
    fun returnsNullForInvalidOrEmptyText() {
        assertNull(SharedTextParser.parse(null, null))
        assertNull(SharedTextParser.parse("", null))
        assertNull(SharedTextParser.parse("Just some random text without any links", "Subject"))
    }
}
