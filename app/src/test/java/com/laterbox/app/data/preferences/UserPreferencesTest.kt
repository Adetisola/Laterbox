package com.laterbox.app.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPreferencesTest {

    @Test
    fun userPreferences_defaultValuesMatchLocalFirstPrinciples() {
        val prefs = UserPreferences()
        assertEquals("", prefs.userName)
        assertTrue(prefs.allowSystemBackup)
        assertEquals(ThemeMode.SYSTEM, prefs.themeMode)
        assertTrue(prefs.smartRemindersEnabled)
        assertTrue(prefs.dailyDigestEnabled)
        assertEquals(20, prefs.dailyDigestHour)
        assertEquals(0, prefs.dailyDigestMinute)
        assertFalse(prefs.morningEveningEnabled)
        assertEquals(9, prefs.morningHour)
        assertEquals(20, prefs.eveningHour)
        assertFalse(prefs.weekendRoundupEnabled)
        assertEquals(10, prefs.weekendRoundupHour)
        assertFalse(prefs.customScheduleEnabled)
        assertEquals(SmartReminderPersistence.TWICE, prefs.smartReminderPersistence)
        assertEquals("", prefs.geminiApiKey)
        assertTrue(prefs.deliveredSlotKeys.isEmpty())
        assertFalse(prefs.accessibilityCaptureEnabled)
        assertTrue(prefs.clipboardSuggestionEnabled)
        assertTrue(prefs.recentClipboardHistory.isEmpty())
        assertTrue(prefs.excludedCapturePackages.isEmpty())
    }

    @Test
    fun userPreferences_toSmartReminderSettings_preservesAllSchedules() {
        val prefs = UserPreferences(
            userName = "Alex Developer",
            smartRemindersEnabled = true,
            morningEveningEnabled = true,
            morningHour = 8,
            morningMinute = 30,
            eveningHour = 21,
            eveningMinute = 15,
            weekendRoundupEnabled = true,
            weekendRoundupHour = 11,
            weekendRoundupMinute = 0,
            dailyDigestEnabled = false,
            customScheduleEnabled = true,
            customScheduleDays = setOf(1, 3, 5),
            customScheduleHour = 14,
            customScheduleMinute = 30,
            smartReminderPersistence = SmartReminderPersistence.THRICE
        )

        val settings = prefs.toSmartReminderSettings()
        assertTrue(settings.enabled)
        assertTrue(settings.morningEvening.enabled)
        assertEquals(8, settings.morningEvening.morningHour)
        assertEquals(21, settings.morningEvening.eveningHour)
        assertTrue(settings.weekendRoundup.enabled)
        assertEquals(11, settings.weekendRoundup.hour)
        assertFalse(settings.dailyDigest.enabled)
        assertTrue(settings.custom.enabled)
        assertEquals(setOf(1, 3, 5), settings.custom.daysOfWeek)
        assertEquals(14, settings.custom.hour)
        assertEquals(SmartReminderPersistence.THRICE, settings.persistence)
    }
}
