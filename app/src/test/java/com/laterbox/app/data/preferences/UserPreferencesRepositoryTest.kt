package com.laterbox.app.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserPreferencesRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: UserPreferencesRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = UserPreferencesRepository(context)
    }

    @Test
    fun userName_defaultsToEmpty() = runTest {
        val prefs = repository.userPreferencesFlow.first()
        assertEquals("", prefs.userName)
    }

    @Test
    fun setUserName_savesAndTrims() = runTest {
        repository.setUserName("  Alex Developer  ")
        val prefs = repository.userPreferencesFlow.first()
        assertEquals("Alex Developer", prefs.userName)

        // Empty or blank resets to empty string
        repository.setUserName("   ")
        val resetPrefs = repository.userPreferencesFlow.first()
        assertEquals("", resetPrefs.userName)
    }
}
