package com.laterbox.app.reminders.smart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleCalculatorTest {

    private val testZone: ZoneId = ZoneId.of("UTC")

    @Test
    fun masterDisabled_returnsNullForEveryScheduleType() {
        val settings = SmartReminderSettings(
            enabled = false,
            morningEvening = MorningEveningConfig(enabled = true),
            weekendRoundup = WeekendConfig(enabled = true),
            dailyDigest = DailyDigestConfig(enabled = true),
            custom = CustomScheduleConfig(enabled = true)
        )
        val now = ZonedDateTime.of(LocalDate.of(2026, 11, 9), LocalTime.of(8, 0), testZone)

        SmartScheduleType.values().forEach { type ->
            assertNull("Expected null when master is disabled", ScheduleCalculator.nextTrigger(type, settings, now))
        }
    }

    @Test
    fun morningAndEvening_targetTwoDistinctTimesWithoutDrift() {
        val settings = SmartReminderSettings(
            enabled = true,
            morningEvening = MorningEveningConfig(
                enabled = true,
                morningHour = 9,
                morningMinute = 0,
                eveningHour = 20,
                eveningMinute = 0
            )
        )

        // Case A: Before morning (8:00 AM on Monday Nov 9)
        val nowMorning = ZonedDateTime.of(LocalDate.of(2026, 11, 9), LocalTime.of(8, 0), testZone)
        val morningTrigger = ScheduleCalculator.nextTrigger(SmartScheduleType.MORNING, settings, nowMorning)
        assertNotNull(morningTrigger)
        assertEquals(LocalDate.of(2026, 11, 9), morningTrigger?.toLocalDate())
        assertEquals(LocalTime.of(9, 0), morningTrigger?.toLocalTime())

        val eveningTrigger = ScheduleCalculator.nextTrigger(SmartScheduleType.EVENING, settings, nowMorning)
        assertNotNull(eveningTrigger)
        assertEquals(LocalDate.of(2026, 11, 9), eveningTrigger?.toLocalDate())
        assertEquals(LocalTime.of(20, 0), eveningTrigger?.toLocalTime())

        // Case B: Between morning and evening (14:00 on Monday Nov 9)
        val nowAfternoon = ZonedDateTime.of(LocalDate.of(2026, 11, 9), LocalTime.of(14, 0), testZone)
        val morningNextDay = ScheduleCalculator.nextTrigger(SmartScheduleType.MORNING, settings, nowAfternoon)
        assertEquals(LocalDate.of(2026, 11, 10), morningNextDay?.toLocalDate())
        assertEquals(LocalTime.of(9, 0), morningNextDay?.toLocalTime())

        val eveningToday = ScheduleCalculator.nextTrigger(SmartScheduleType.EVENING, settings, nowAfternoon)
        assertEquals(LocalDate.of(2026, 11, 9), eveningToday?.toLocalDate())
        assertEquals(LocalTime.of(20, 0), eveningToday?.toLocalTime())

        // Case C: After evening (21:30 on Monday Nov 9)
        val nowNight = ZonedDateTime.of(LocalDate.of(2026, 11, 9), LocalTime.of(21, 30), testZone)
        val morningTomorrow = ScheduleCalculator.nextTrigger(SmartScheduleType.MORNING, settings, nowNight)
        val eveningTomorrow = ScheduleCalculator.nextTrigger(SmartScheduleType.EVENING, settings, nowNight)
        assertEquals(LocalDate.of(2026, 11, 10), morningTomorrow?.toLocalDate())
        assertEquals(LocalTime.of(9, 0), morningTomorrow?.toLocalTime())
        assertEquals(LocalDate.of(2026, 11, 10), eveningTomorrow?.toLocalDate())
        assertEquals(LocalTime.of(20, 0), eveningTomorrow?.toLocalTime())
    }

    @Test
    fun weekendRoundup_onlyTriggersOnSaturdayAndSundayAcrossEntireWeek() {
        val settings = SmartReminderSettings(
            enabled = true,
            weekendRoundup = WeekendConfig(enabled = true, hour = 10, minute = 0)
        )

        // Starting from Monday Nov 9 to Sunday Nov 15
        val monday = LocalDate.of(2026, 11, 9) // Monday
        assertEquals(DayOfWeek.MONDAY, monday.dayOfWeek)

        for (dayOffset in 0..6) {
            val date = monday.plusDays(dayOffset.toLong())
            val nowNoon = ZonedDateTime.of(date, LocalTime.of(12, 0), testZone)

            val satTrigger = ScheduleCalculator.nextTrigger(SmartScheduleType.WEEKEND_SAT, settings, nowNoon)
            val sunTrigger = ScheduleCalculator.nextTrigger(SmartScheduleType.WEEKEND_SUN, settings, nowNoon)

            assertNotNull(satTrigger)
            assertNotNull(sunTrigger)
            assertEquals(DayOfWeek.SATURDAY, satTrigger?.dayOfWeek)
            assertEquals(DayOfWeek.SUNDAY, sunTrigger?.dayOfWeek)
            assertEquals(LocalTime.of(10, 0), satTrigger?.toLocalTime())
            assertEquals(LocalTime.of(10, 0), sunTrigger?.toLocalTime())
            assertTrue("Trigger must be strictly in the future", satTrigger!!.isAfter(nowNoon))
            assertTrue("Trigger must be strictly in the future", sunTrigger!!.isAfter(nowNoon))
        }
    }

    @Test
    fun dailyDigest_weekdaysOnlySkipsWeekend() {
        val settings = SmartReminderSettings(
            enabled = true,
            dailyDigest = DailyDigestConfig(
                enabled = true,
                hour = 19,
                minute = 0,
                weekdaysOnly = true
            )
        )

        // Friday night after 19:00 (e.g. 21:00 on Friday Nov 13)
        val fridayNight = ZonedDateTime.of(LocalDate.of(2026, 11, 13), LocalTime.of(21, 0), testZone)
        assertEquals(DayOfWeek.FRIDAY, fridayNight.dayOfWeek)

        val nextTrigger = ScheduleCalculator.nextTrigger(SmartScheduleType.DAILY, settings, fridayNight)
        assertNotNull(nextTrigger)
        assertEquals(DayOfWeek.MONDAY, nextTrigger?.dayOfWeek)
        assertEquals(LocalDate.of(2026, 11, 16), nextTrigger?.toLocalDate())
        assertEquals(LocalTime.of(19, 0), nextTrigger?.toLocalTime())
    }

    @Test
    fun customSchedule_strictlyRespectsConfiguredDays() {
        val settings = SmartReminderSettings(
            enabled = true,
            custom = CustomScheduleConfig(
                enabled = true,
                daysOfWeek = setOf(1, 3, 5), // Monday, Wednesday, Friday
                hour = 14,
                minute = 30
            )
        )

        // On Tuesday Nov 10 at 10:00 AM -> next should be Wednesday Nov 11 at 14:30
        val tuesday = ZonedDateTime.of(LocalDate.of(2026, 11, 10), LocalTime.of(10, 0), testZone)
        val nextTrigger = ScheduleCalculator.nextTrigger(SmartScheduleType.CUSTOM, settings, tuesday)

        assertNotNull(nextTrigger)
        assertEquals(LocalDate.of(2026, 11, 11), nextTrigger?.toLocalDate())
        assertEquals(DayOfWeek.WEDNESDAY, nextTrigger?.dayOfWeek)
        assertEquals(LocalTime.of(14, 30), nextTrigger?.toLocalTime())
    }

    @Test
    fun slotKey_formatsCorrectDeterministicString() {
        val triggerTime = ZonedDateTime.of(LocalDate.of(2026, 11, 14), LocalTime.of(10, 0), testZone)
        val key = ScheduleCalculator.slotKey(SmartScheduleType.WEEKEND_SAT, triggerTime)
        assertEquals("WEEKEND_SAT@2026-11-14", key)
    }
}
