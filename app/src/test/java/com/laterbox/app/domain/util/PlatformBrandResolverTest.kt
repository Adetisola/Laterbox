package com.laterbox.app.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PlatformBrandResolverTest {

    @Test
    fun resolveYouTubeDomainsAndUrls() {
        val cases = listOf(
            "youtube.com",
            "www.youtube.com",
            "m.youtube.com",
            "music.youtube.com",
            "youtu.be",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ?t=10"
        )
        for (case in cases) {
            val brand = PlatformBrandResolver.resolve(case)
            assertNotNull("Expected YouTube match for $case", brand)
            assertEquals("youtube", brand?.id)
            assertEquals("YouTube", brand?.displayName)
        }
    }

    @Test
    fun resolveRedditDomainsAndUrls() {
        val cases = listOf(
            "reddit.com",
            "www.reddit.com",
            "old.reddit.com",
            "redd.it",
            "https://reddit.com/r/androiddev/comments/123",
            "https://redd.it/xyz123"
        )
        for (case in cases) {
            val brand = PlatformBrandResolver.resolve(case)
            assertNotNull("Expected Reddit match for $case", brand)
            assertEquals("reddit", brand?.id)
            assertEquals("Reddit", brand?.displayName)
        }
    }

    @Test
    fun resolveGitHubDomainsAndUrls() {
        val cases = listOf(
            "github.com",
            "www.github.com",
            "gist.github.com",
            "https://github.com/Adetisola/Laterbox"
        )
        for (case in cases) {
            val brand = PlatformBrandResolver.resolve(case)
            assertNotNull("Expected GitHub match for $case", brand)
            assertEquals("github", brand?.id)
            assertEquals("GitHub", brand?.displayName)
        }
    }

    @Test
    fun resolveXTwitterDomainsAndUrls() {
        val cases = listOf(
            "twitter.com",
            "mobile.twitter.com",
            "x.com",
            "t.co",
            "https://twitter.com/android/status/12345",
            "https://x.com/compose/tweet",
            "https://t.co/xyz987"
        )
        for (case in cases) {
            val brand = PlatformBrandResolver.resolve(case)
            assertNotNull("Expected X/Twitter match for $case", brand)
            assertEquals("x", brand?.id)
            assertEquals("X", brand?.displayName)
        }
    }

    @Test
    fun rejectsUnrelatedOrSubtlyModifiedDomains() {
        val negativeCases = listOf(
            "notyoutube.com",
            "fake-youtube.com",
            "youtube.com.attacker.com",
            "notreddit.com",
            "reddit.com.co",
            "mygithub.com",
            "github.company",
            "fox.com",
            "box.com",
            "example.com",
            "nytimes.com",
            "",
            "   ",
            null
        )
        for (case in negativeCases) {
            val brand = PlatformBrandResolver.resolve(case)
            assertNull("Expected null match for negative case: '$case'", brand)
        }
    }
}
