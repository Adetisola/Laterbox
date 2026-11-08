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
    }

    @Test
    fun userPreferences_withCustomUserName() {
        val prefs = UserPreferences(userName = "Alex Developer")
        assertEquals("Alex Developer", prefs.userName)
    }
}
