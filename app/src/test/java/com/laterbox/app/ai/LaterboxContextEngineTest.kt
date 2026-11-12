package com.laterbox.app.ai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.laterbox.app.data.local.LaterboxDatabase
import com.laterbox.app.data.local.entity.SavedLinkEntity
import com.laterbox.app.data.preferences.UserPreferencesRepository
import com.laterbox.app.data.repository.ActivityEventRepository
import com.laterbox.app.data.repository.ActivityEventType
import com.laterbox.app.domain.model.ContentType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaterboxContextEngineTest {

    private lateinit var db: LaterboxDatabase
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var activityRepo: ActivityEventRepository
    private lateinit var engine: LaterboxContextEngine

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LaterboxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefsRepo = UserPreferencesRepository(context)
        activityRepo = ActivityEventRepository(db.activityEventDao())
        engine = LaterboxContextEngine(
            database = db,
            activityEventRepository = activityRepo,
            userPreferencesRepository = prefsRepo
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun buildContextForPrompt_ranksRelevantLinksWithinBoundsAndRedactsSecrets() = runTest {
        prefsRepo.setGeminiApiKey("AIzaSySuperSecretKey1234567890123456")
        val linkDao = db.savedLinkDao()
        for (i in 1..15) {
            linkDao.insertLink(
                SavedLinkEntity(
                    originalUrl = "https://example.com/item/$i",
                    normalizedUrl = "https://example.com/item/$i",
                    title = if (i == 14) "Kotlin Coroutines Deep Dive" else "General Article $i",
                    domain = "example.com",
                    contentType = ContentType.ARTICLE
                )
            )
        }

        activityRepo.recordEvent(
            type = ActivityEventType.INSPECTION_FAILED,
            title = "Failed in /data/user/0/com.laterbox.app/files/secret",
            detail = "x-goog-api-key=AIzaSyAnotherSecretKey9999999999999"
        )

        val built = engine.buildContextForPrompt("Find my saved kotlin coroutines link")
        assertTrue(built.contextBlock.contains("Kotlin Coroutines Deep Dive"))
        val linkMatches = Regex("\\[linkId=\\d+\\]").findAll(built.contextBlock).count()
        assertTrue("Expected at most 10 links in context, got $linkMatches", linkMatches <= 10)
        assertFalse(built.contextBlock.contains("AIzaSy"))
        assertFalse(built.contextBlock.contains("/data/user/0/"))
    }

    @Test
    fun buildContextForPrompt_injectsHonestyReminderForTranscriptOrFullArticleQueries() = runTest {
        val built = engine.buildContextForPrompt("Give me the full transcript of what they said in that video")
        assertTrue(built.contextBlock.contains("HONESTY REMINDER"))
        assertTrue(built.systemInstruction.contains("Allowed action objects ONLY"))
    }
}
