package com.laterbox.app.ai

import com.laterbox.app.domain.model.ContentType
import com.laterbox.app.domain.model.SavedLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartDigestTest {

    @Test
    fun plainReminderCopy_forManualLink_prioritizesPersonalNote() {
        val link = SavedLink(
            id = 2L,
            originalUrl = "https://example.com/guide",
            normalizedUrl = "https://example.com/guide",
            title = "Modern Kotlin Coroutines in Depth",
            domain = "example.com",
            personalNote = "Study chapter 4 before Monday team review",
            contentType = ContentType.ARTICLE
        )

        val copy = PlainReminderCopy.forManual(link)

        assertEquals("Modern Kotlin Coroutines in Depth", copy.title)
        assertEquals("Study chapter 4 before Monday team review", copy.body)
        assertFalse(copy.isAiGenerated)
        assertEquals(ReminderOrigin.MANUAL, copy.origin)
    }

    @Test
    fun plainReminderCopy_withBlankTitle_usesDomain() {
        val link = SavedLink(
            id = 3L,
            originalUrl = "https://kotlinlang.org",
            normalizedUrl = "https://kotlinlang.org",
            title = "",
            domain = "kotlinlang.org",
            contentType = ContentType.ARTICLE
        )

        val copy = PlainReminderCopy.forManual(link)

        assertEquals("kotlinlang.org", copy.title)
        assertFalse(copy.isAiGenerated)
        assertEquals(ReminderOrigin.MANUAL, copy.origin)
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
    fun aiFailure_userMessagesAreHonestAndInformative() {
        assertEquals("No API key configured", AiFailure.NoApiKey.userMessage)
        assertEquals("Network connection unavailable", AiFailure.Offline.userMessage)
        assertEquals("Request timed out", AiFailure.Timeout.userMessage)
        assertEquals("Rate limited by AI provider (429)", AiFailure.RateLimited(429).userMessage)
        assertEquals("Invalid API key or unauthorized (401)", AiFailure.AuthError(401).userMessage)
        assertEquals("AI server error (500)", AiFailure.ServerError(500).userMessage)
        assertEquals("AI returned an empty response", AiFailure.EmptyResponse.userMessage)
        assertEquals("AI returned unparseable content", AiFailure.MalformedResponse.userMessage)
    }
}
