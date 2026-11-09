package com.laterbox.app.reminders.smart

import android.content.Context
import java.time.ZonedDateTime

object SmartReminderCoordinator {

    suspend fun sync(
        context: Context,
        settings: SmartReminderSettings,
        now: ZonedDateTime = ZonedDateTime.now()
    ) {
        SmartReminderScheduler(context).sync(settings, now)
    }

    suspend fun scheduleNext(
        context: Context,
        type: SmartScheduleType
    ) {
        SmartReminderScheduler(context).scheduleNext(type)
    }
}
