package com.laterbox.app.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ReminderPresetsTest {

    @Test
    fun getTonightMillis_returns8PM() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 0)
        }.timeInMillis

        val tonight = ReminderPresets.getTonightMillis(now)
        val cal = Calendar.getInstance().apply { timeInMillis = tonight }

        assertEquals(20, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertTrue(tonight > now)
    }

    @Test
    fun getTonightMillis_whenAfter8PM_returnsTomorrow8PM() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 21)
            set(Calendar.MINUTE, 30)
        }.timeInMillis

        val tonight = ReminderPresets.getTonightMillis(now)
        val cal = Calendar.getInstance().apply { timeInMillis = tonight }

        assertEquals(20, cal.get(Calendar.HOUR_OF_DAY))
        assertTrue(tonight > now)
    }

    @Test
    fun getTomorrowMorningMillis_returns9AM() {
        val now = System.currentTimeMillis()
        val tomorrow = ReminderPresets.getTomorrowMorningMillis(now)
        val cal = Calendar.getInstance().apply { timeInMillis = tomorrow }

        assertEquals(9, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertTrue(tomorrow > now)
    }

    @Test
    fun getWeekendMillis_returnsSaturday10AM() {
        val now = System.currentTimeMillis()
        val weekend = ReminderPresets.getWeekendMillis(now)
        val cal = Calendar.getInstance().apply { timeInMillis = weekend }

        assertEquals(Calendar.SATURDAY, cal.get(Calendar.DAY_OF_WEEK))
        assertEquals(10, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertTrue(weekend >= now)
    }

    @Test
    fun formatReminderTime_returnsReadableString() {
        val now = System.currentTimeMillis()
        val formatted = ReminderPresets.formatReminderTime(now)
        assertTrue(formatted.isNotBlank())
    }
}
