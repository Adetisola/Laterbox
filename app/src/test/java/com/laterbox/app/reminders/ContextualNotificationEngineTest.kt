package com.laterbox.app.reminders

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.laterbox.app.data.local.LaterboxDatabase
import com.laterbox.app.data.preferences.UserPreferencesRepository
import com.laterbox.app.data.repository.ActivityEventRepository
import com.laterbox.app.diagnostics.AppHealthChecker
import com.laterbox.app.media.data.adapters.ytdlp.YtDlpRuntimeManager
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContextualNotificationEngineTest {

    private lateinit var context: Context
    private lateinit var db: LaterboxDatabase
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var activityRepo: ActivityEventRepository
    private lateinit var healthChecker: AppHealthChecker
    private lateinit var engine: ContextualNotificationEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, LaterboxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefsRepo = UserPreferencesRepository(context)
        activityRepo = ActivityEventRepository(db.activityEventDao())
        healthChecker = AppHealthChecker(context, db, YtDlpRuntimeManager(context), prefsRepo)
        engine = ContextualNotificationEngine(context, db, prefsRepo, activityRepo, healthChecker)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun isWithinQuietHours_handlesOvernightDaytimeAndDisabledWindows() {
        // Overnight 22:00 -> 08:00
        assertTrue(NotificationHelper.isWithinQuietHours(23, 22, 8))
        assertTrue(NotificationHelper.isWithinQuietHours(2, 22, 8))
        assertFalse(NotificationHelper.isWithinQuietHours(8, 22, 8))
        assertFalse(NotificationHelper.isWithinQuietHours(15, 22, 8))

        // Daytime 09:00 -> 17:00
        assertTrue(NotificationHelper.isWithinQuietHours(9, 9, 17))
        assertTrue(NotificationHelper.isWithinQuietHours(16, 9, 17))
        assertFalse(NotificationHelper.isWithinQuietHours(17, 9, 17))
        assertFalse(NotificationHelper.isWithinQuietHours(20, 9, 17))

        // Disabled when start == end
        assertFalse(NotificationHelper.isWithinQuietHours(12, 10, 10))
    }

    @Test
    fun claimDedupKey_returnsTrueOnceAndFalseOnDuplicate() {
        val unique = "test_dedup_${System.nanoTime()}"
        assertTrue(NotificationHelper.claimDedupKey(context, unique))
        assertFalse(NotificationHelper.claimDedupKey(context, unique))
    }

    @Test
    fun notifyFailures_respectsQuietHoursAndPreferenceToggles() = runTest {
        prefsRepo.setSmartNotificationsExtractionFailure(true)
        prefsRepo.setSmartNotificationsDownloadFailure(true)
        prefsRepo.setQuietHoursEnabled(true)
        prefsRepo.setQuietHoursWindow(22, 8)

        // Suppressed during quiet hour (23:00)
        assertFalse(
            engine.notifyExtractionFailure(
                savedLinkId = 1L,
                url = "https://example.com/v",
                linkTitle = "Video",
                errorMessage = "Timed out",
                dedupKey = "ext_quiet_${System.nanoTime()}",
                currentHour = 23
            )
        )
        assertFalse(
            engine.notifyDownloadFailure(
                taskId = "task-1",
                savedLinkId = 1L,
                title = "Video",
                errorMessage = "HTTP 403",
                dedupKey = "dl_quiet_${System.nanoTime()}",
                currentHour = 2
            )
        )

        // Suppressed when toggles are off even outside quiet hours
        prefsRepo.setSmartNotificationsExtractionFailure(false)
        prefsRepo.setSmartNotificationsDownloadFailure(false)
        assertFalse(
            engine.notifyExtractionFailure(
                savedLinkId = 1L,
                url = "https://example.com/v",
                linkTitle = "Video",
                errorMessage = "Timed out",
                dedupKey = "ext_off_${System.nanoTime()}",
                currentHour = 14
            )
        )
        assertFalse(
            engine.notifyDownloadFailure(
                taskId = "task-1",
                savedLinkId = 1L,
                title = "Video",
                errorMessage = "HTTP 403",
                dedupKey = "dl_off_${System.nanoTime()}",
                currentHour = 14
            )
        )
    }

    @Test
    fun selectNextUnshownTip_isNonRepeatingAcrossCatalogAndStopsWhenExhausted() = runTest {
        prefsRepo.resetShownTips()
        val seen = mutableSetOf<String>()

        repeat(ContextualNotificationEngine.TIP_CATALOG.size) {
            val next = ContextualNotificationEngine.selectNextUnshownTip(seen)
            assertNotNull(next)
            assertTrue("Tip ${next!!.id} should not repeat", seen.add(next.id))
            prefsRepo.recordShownTip(next.id, timestamp = 1000L + it)
        }

        assertNull(ContextualNotificationEngine.selectNextUnshownTip(seen))
        assertEquals(ContextualNotificationEngine.TIP_CATALOG.size, seen.size)
    }
}
