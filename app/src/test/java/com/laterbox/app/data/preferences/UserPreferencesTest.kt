package com.laterbox.app.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPreferencesTest {

    @Test
    fun userPreferences_defaultUserNameIsEmpty() {
        val prefs = UserPreferences()
        assertEquals("", prefs.userName)
        assertTrue(prefs.allowSystemBackup)
        assertEquals(ThemeMode.SYSTEM, prefs.themeMode)
        assertTrue(prefs.smartRemindersEnabled)
        assertEquals(AiEngine.CLOUD, prefs.aiEngine)
    }

    @Test
    fun userPreferences_withCustomUserNameAndAiEngine() {
        val prefs = UserPreferences(
            userName = "Alex Developer",
            smartRemindersEnabled = false,
            aiEngine = AiEngine.ON_DEVICE
        )
        assertEquals("Alex Developer", prefs.userName)
        assertEquals(false, prefs.smartRemindersEnabled)
        assertEquals(AiEngine.ON_DEVICE, prefs.aiEngine)
    }
}
