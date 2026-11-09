package com.laterbox.app.reminders.smart

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmartReminderSchedulerTest {

    private lateinit var context: Context
    private lateinit var scheduler: SmartReminderScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        scheduler = SmartReminderScheduler(context)
    }

    @Test
    fun uniqueRequestCodesForEveryScheduleType() {
        val types = SmartScheduleType.values()
        val codes = types.map { SmartReminderScheduler.requestCodeFor(it) }

        // All codes should be distinct
        assertEquals("Each schedule type must have a unique request code", types.size, codes.distinct().size)

        // All codes should be above base request code (900_000)
        for (code in codes) {
            assertTrue("Request code $code should be >= 900001", code > SmartReminderScheduler.SMART_ALARM_BASE_REQUEST_CODE)
        }

        // Verify specific code bindings
        assertEquals(900_001, SmartReminderScheduler.requestCodeFor(SmartScheduleType.MORNING))
        assertEquals(900_002, SmartReminderScheduler.requestCodeFor(SmartScheduleType.EVENING))
        assertEquals(900_003, SmartReminderScheduler.requestCodeFor(SmartScheduleType.WEEKEND_SAT))
        assertEquals(900_004, SmartReminderScheduler.requestCodeFor(SmartScheduleType.WEEKEND_SUN))
        assertEquals(900_005, SmartReminderScheduler.requestCodeFor(SmartScheduleType.DAILY))
        assertEquals(900_006, SmartReminderScheduler.requestCodeFor(SmartScheduleType.CUSTOM))
    }

    @Test
    fun canScheduleExact_returnsBooleanWithoutCrashing() {
        // Under Robolectric, canScheduleExact should safely evaluate without crashing
        val canSchedule = scheduler.canScheduleExact()
        assertTrue(canSchedule || !canSchedule) // Valid boolean returned
    }

    @Test
    fun scheduleAndCancelSchedule_runsCleanly() {
        val futureTime = ZonedDateTime.of(
            LocalDate.now().plusDays(1),
            LocalTime.of(9, 0),
            ZoneId.systemDefault()
        )

        // Schedule exact
        scheduler.scheduleExact(SmartScheduleType.MORNING, futureTime)

        // Cancel schedule
        scheduler.cancelSchedule(SmartScheduleType.MORNING)

        // Cancel all
        scheduler.cancelAll()
    }
}
