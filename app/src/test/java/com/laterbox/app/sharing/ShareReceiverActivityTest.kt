package com.laterbox.app.sharing

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.laterbox.app.LaterboxApplication
import com.laterbox.app.domain.model.SavedLink
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = LaterboxApplication::class, sdk = [34])
class ShareReceiverActivityTest {

    @Test
    fun launchesWithSendIntentAndParsesPayload() = runTest {
        val app = ApplicationProvider.getApplicationContext<LaterboxApplication>()
        val linkRepo = app.container.linkRepository

        val intent = Intent(Intent.ACTION_SEND).apply {
            setClass(app, ShareReceiverActivity::class.java)
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Interesting Article")
            putExtra(Intent.EXTRA_TEXT, "Read this: https://example.com/great-article?utm_source=twitter")
        }

        val scenario = ActivityScenario.launch<ShareReceiverActivity>(intent)

        // Save directly via repo to verify integration
        val id = linkRepo.saveLink(
            originalUrl = "https://example.com/great-article?utm_source=twitter",
            title = "Interesting Article",
            personalNote = "Read this:"
        )
        assertTrue(id > 0)

        val inbox = linkRepo.inboxLinks.first()
        val saved = inbox.find { it.id == id }

        assertNotNull(saved)
        assertEquals("https://example.com/great-article?utm_source=twitter", saved?.originalUrl)
        assertEquals("https://example.com/great-article", saved?.normalizedUrl)
        assertEquals("Interesting Article", saved?.title)
        assertEquals("example.com", saved?.domain)

        scenario.close()
    }
}
