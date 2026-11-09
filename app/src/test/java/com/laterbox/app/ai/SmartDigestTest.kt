package com.laterbox.app.ai

import com.laterbox.app.domain.model.ContentType
import com.laterbox.app.domain.model.SavedLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmartDigestTest {

    @Test
    fun contextAwareCopyGenerator_withInstagramLinkAndUserName() {
        val threeDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3)
        val link = SavedLink(
            id = 1L,
            originalUrl = "https://www.instagram.com/reel/C12345678",
            normalizedUrl = "https://instagram.com/reel/C12345678",
            title = "Incredible street food in Kyoto",
            domain = "instagram.com",
            siteName = "Instagram",
            contentType = ContentType.VIDEO,
            createdAt = threeDaysAgo
        )

        val copy = ContextAwareCopyGenerator.generateCopy(
            link = link,
            userName = "Alex",
            now = System.currentTimeMillis()
        )

        assertEquals("Watch when you have a moment", copy.title)
        assertTrue(copy.body.contains("Hey Alex, seems like you haven't checked the Instagram post you saved 3 days ago"))
        assertFalse(copy.isAiGenerated)
    }

    @Test
    fun contextAwareCopyGenerator_withPersonalNotePrioritizesNote() {
        val link = SavedLink(
            id = 2L,
            originalUrl = "https://example.com/guide",
            normalizedUrl = "https://example.com/guide",
            title = "Modern Kotlin Coroutines in Depth",
            domain = "example.com",
            personalNote = "Study chapter 4 before Monday team review",
            contentType = ContentType.ARTICLE
        )

        val copy = ContextAwareCopyGenerator.generateCopy(
            link = link,
            userName = "Timi"
        )

        assertTrue(copy.body.contains("Study chapter 4 before Monday team review"))
        assertTrue(copy.body.contains("Modern Kotlin Coroutines in Depth"))
    }

    @Test
    fun smartDigestEngine_parseValidJson() {
        val raw = """
            {
              "title": "Revisit your saved reel",
              "body": "Hi Alex, seems like you haven't checked the Instagram video you saved a few days ago."
            }
        """.trimIndent()

        val parsed = SmartDigestEngine.parseAiResponse(raw)
        assertNotNull(parsed)
        assertEquals("Revisit your saved reel", parsed?.title)
        assertTrue(parsed?.body?.contains("Hi Alex") == true)
        assertTrue(parsed?.isAiGenerated == true)
    }

    @Test
    fun smartDigestEngine_parseMarkdownFencedJson() {
        val raw = """
            ```json
            {
              "title": "Catch up on this thread",
              "body": "There is a discussion on Reddit you wanted to look into."
            }
            ```
        """.trimIndent()

        val parsed = SmartDigestEngine.parseAiResponse(raw)
        assertNotNull(parsed)
        assertEquals("Catch up on this thread", parsed?.title)
        assertEquals("There is a discussion on Reddit you wanted to look into.", parsed?.body)
        assertTrue(parsed?.isAiGenerated == true)
    }

    @Test
    fun smartDigestEngine_parseMalformedJsonReturnsNull() {
        val raw = "Sorry, I am an AI and cannot generate that."
        val parsed = SmartDigestEngine.parseAiResponse(raw)
        assertNull(parsed)
    }

    @Test
    fun contextAwareCopyGenerator_withManualReminderOrigin() {
        val link = SavedLink(
            id = 3L,
            originalUrl = "https://youtube.com/watch?v=123",
            normalizedUrl = "https://youtube.com/watch?v=123",
            title = "Introduction to Jetpack Compose",
            domain = "youtube.com",
            siteName = "YouTube",
            contentType = ContentType.VIDEO
        )

        val copy = ContextAwareCopyGenerator.generateCopy(
            link = link,
            userName = "Timi",
            origin = ReminderOrigin.MANUAL
        )

        assertEquals("Ready for Introduction to Jetpack?", copy.title)
        assertTrue(copy.body.contains("Hey Timi, here is Introduction to Jetpack Compose from YouTube that you asked to revisit."))
        assertEquals(ReminderOrigin.MANUAL, copy.origin)
    }
}
