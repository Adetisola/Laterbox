package com.laterbox.app.diagnostics

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.laterbox.app.data.local.LaterboxDatabase
import com.laterbox.app.data.preferences.UserPreferencesRepository
import com.laterbox.app.media.data.adapters.ytdlp.FfmpegInitState
import com.laterbox.app.media.data.adapters.ytdlp.YtDlpInitException
import com.laterbox.app.media.data.adapters.ytdlp.YtDlpInitFailureCategory
import com.laterbox.app.media.data.adapters.ytdlp.YtDlpInitState
import com.laterbox.app.media.data.adapters.ytdlp.YtDlpRuntimeManager
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppHealthCheckerTest {

    private lateinit var context: Context
    private lateinit var db: LaterboxDatabase
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var runtimeManager: YtDlpRuntimeManager
    private lateinit var checker: AppHealthChecker

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, LaterboxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefsRepo = UserPreferencesRepository(context)
        runtimeManager = YtDlpRuntimeManager(context)
        checker = AppHealthChecker(context, db, runtimeManager, prefsRepo)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun evaluateIssues_flagsCriticalAndWarningSubsystemsAccurately() {
        val issues = AppHealthChecker.evaluateIssues(
            ytDlpState = YtDlpInitState.Failed(
                YtDlpInitException(YtDlpInitFailureCategory.PYTHON_RUNTIME_INIT_FAILURE, "boom")
            ),
            ffmpegState = FfmpegInitState.Failed(
                YtDlpInitException(YtDlpInitFailureCategory.FFMPEG_INIT_FAILURE, "ffmpeg")
            ),
            quickJsAvailable = true,
            failedInspections = 3,
            failedDownloads = 2,
            availableBytes = 20L * 1024L * 1024L,
            netConnected = false,
            netValidated = false,
            notificationsEnabled = false,
            exactAlarmsAllowed = false
        )

        val ids = issues.map { it.id }.toSet()
        assertTrue(ids.contains("ytdlp_failed"))
        assertTrue(ids.contains("ffmpeg_failed"))
        assertTrue(ids.contains("storage_critical"))
        assertTrue(ids.contains("network_offline"))
        assertTrue(ids.contains("notifications_disabled"))
        assertTrue(ids.contains("exact_alarms_restricted"))
        assertTrue(ids.contains("failed_inspections"))
        assertTrue(ids.contains("failed_downloads"))
        assertTrue(issues.any { it.id == "ytdlp_failed" && it.severity == IssueSeverity.CRITICAL })
        assertTrue(issues.any { it.id == "storage_critical" && it.severity == IssueSeverity.CRITICAL })
    }

    @Test
    fun runCheck_withHealthyOverridesReturnsHealthyAndHumanSummary() = runTest {
        val report = checker.runCheck(
            overrideStorageBytes = 1024L * 1024L * 1024L,
            overrideNetworkConnected = true,
            overrideNetworkValidated = true,
            overrideNotificationsEnabled = true,
            overrideExactAlarmsAllowed = true,
            overrideQuickJsAvailable = true
        )

        assertEquals(AppHealthStatus.HEALTHY, report.overallStatus)
        assertTrue(report.issues.isEmpty())
        val summary = report.toHumanSummary()
        assertTrue(summary.contains("HEALTHY"))
        assertTrue(summary.contains("1024 MB"))
        assertFalse(summary.contains("Detected issues"))
    }
}
