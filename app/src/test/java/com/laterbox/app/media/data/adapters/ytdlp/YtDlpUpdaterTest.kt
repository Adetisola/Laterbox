package com.laterbox.app.media.data.adapters.ytdlp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.laterbox.app.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YtDlpUpdaterTest {

    private lateinit var context: Context
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var updater: YtDlpUpdater

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        userPreferencesRepository = UserPreferencesRepository(context)
        updater = YtDlpUpdater(context, userPreferencesRepository)
    }

    @Test
    fun getInstalledVersion_safeInTestEnvironment_doesNotThrow() = runBlocking {
        // In Robolectric without native libraries, getInstalledVersion catches Throwable and returns empty string
        val version = updater.getInstalledVersion()
        assertNotNull(version)
    }

    @Test
    fun forceUpdate_safeInTestEnvironment_returnsFailureWithoutCrashing() = runBlocking {
        // Without native binaries in JVM test environment, forceUpdate returns Result.failure rather than crashing
        val result = updater.forceUpdate()
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun updateIfStale_whenRecent_returnsFalseWithoutUpdating() = runBlocking {
        // Record an update that happened right now
        userPreferencesRepository.recordYtDlpUpdate("2026.11.01", System.currentTimeMillis())

        val didUpdate = updater.updateIfStale(maxAgeMs = 24 * 60 * 60 * 1000L)
        assertFalse(didUpdate)
    }
}
