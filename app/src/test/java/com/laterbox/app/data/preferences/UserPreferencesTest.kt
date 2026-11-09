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
        assertEquals(SmartReminderSchedule.DAILY_DIGEST, prefs.smartReminderSchedule)
        assertEquals(20, prefs.smartReminderHour)
        assertEquals(0, prefs.smartReminderMinute)
        assertEquals(SmartReminderPersistence.TWICE, prefs.smartReminderPersistence)
        assertEquals(AiEngine.CLOUD, prefs.aiEngine)
    }

    @Test
    fun userPreferences_withCustomUserNameAndAiEngine() {
        val prefs = UserPreferences(
            userName = "Alex Developer",
            smartRemindersEnabled = false,
            smartReminderSchedule = SmartReminderSchedule.CONTINUOUS_MONITOR,
            smartReminderHour = 9,
            smartReminderMinute = 30,
            smartReminderPersistence = SmartReminderPersistence.UNTIL_READ,
            aiEngine = AiEngine.CLOUD
        )
        assertEquals("Alex Developer", prefs.userName)
        assertEquals(false, prefs.smartRemindersEnabled)
        assertEquals(SmartReminderSchedule.CONTINUOUS_MONITOR, prefs.smartReminderSchedule)
        assertEquals(9, prefs.smartReminderHour)
        assertEquals(30, prefs.smartReminderMinute)
        assertEquals(SmartReminderPersistence.UNTIL_READ, prefs.smartReminderPersistence)
        assertEquals(AiEngine.CLOUD, prefs.aiEngine)
    }
}
