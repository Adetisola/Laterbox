package com.laterbox.app.reminders.smart

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.laterbox.app.LaterboxApplication
import com.laterbox.app.reminders.ReminderScheduler
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object SmartReminderCoordinator {

    private const val TAG = "SmartReminderCoordinator"

    suspend fun sync(
        context: Context,
        settings: SmartReminderSettings,
        now: ZonedDateTime = ZonedDateTime.now()
    ) {
        val workManager = try {
            WorkManager.getInstance(context)
        } catch (e: Exception) {
            Log.w(TAG, "WorkManager not available for sync: ${e.message}")
            return
        }

        // 1. Cancel legacy periodic work and legacy exact alarm if present
        try {
            workManager.cancelUniqueWork("laterbox_daily_digest_periodic")
            ReminderScheduler(context).cancelLegacySmartDigest()
        } catch (_: Exception) {}

        // 2. If master switch is OFF, cancel all smart reminder unique works
        if (!settings.enabled) {
            for (type in SmartScheduleType.values()) {
                workManager.cancelUniqueWork("smart_${type.name}")
            }
            Log.d(TAG, "Smart reminders disabled: all schedules cancelled")
            return
        }

        // 3. For each schedule type, schedule next occurrence or cancel if disabled
        for (type in SmartScheduleType.values()) {
            val nextTime = ScheduleCalculator.nextTrigger(type, settings, now)
            val uniqueWorkName = "smart_${type.name}"

            if (nextTime == null) {
                workManager.cancelUniqueWork(uniqueWorkName)
            } else {
                val delayMillis = Duration.between(now, nextTime).toMillis().coerceAtLeast(0L)
                val slotKey = ScheduleCalculator.slotKey(type, nextTime)

                val inputData = Data.Builder()
                    .putString(SmartReminderWorker.KEY_SCHEDULE_TYPE, type.name)
                    .putString(SmartReminderWorker.KEY_SLOT_KEY, slotKey)
                    .putLong(SmartReminderWorker.KEY_TARGET_MILLIS, nextTime.toInstant().toEpochMilli())
                    .build()

                val workRequest = OneTimeWorkRequestBuilder<SmartReminderWorker>()
                    .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                    .setInputData(inputData)
                    .addTag("smart_reminder")
                    .addTag("smart_${type.name}")
                    .build()

                workManager.enqueueUniqueWork(
                    uniqueWorkName,
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
                Log.d(TAG, "Enqueued $uniqueWorkName for $nextTime (slot=$slotKey, delay=${delayMillis / 1000}s)")
            }
        }
    }

    suspend fun scheduleNext(
        context: Context,
        type: SmartScheduleType
    ) {
        val app = context.applicationContext as? LaterboxApplication ?: return
        val prefs = app.container.userPreferencesRepository.userPreferencesFlow.first()
        val settings = prefs.toSmartReminderSettings()
        if (!settings.enabled) return

        val now = ZonedDateTime.now()
        // Compute next occurrence from 1 minute in future to avoid immediately picking the current slot
        val nextTime = ScheduleCalculator.nextTrigger(type, settings, now.plusMinutes(1)) ?: return
        val uniqueWorkName = "smart_${type.name}"
        val delayMillis = Duration.between(now, nextTime).toMillis().coerceAtLeast(0L)
        val slotKey = ScheduleCalculator.slotKey(type, nextTime)

        val inputData = Data.Builder()
            .putString(SmartReminderWorker.KEY_SCHEDULE_TYPE, type.name)
            .putString(SmartReminderWorker.KEY_SLOT_KEY, slotKey)
            .putLong(SmartReminderWorker.KEY_TARGET_MILLIS, nextTime.toInstant().toEpochMilli())
            .build()

        val workRequest = OneTimeWorkRequestBuilder<SmartReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .addTag("smart_reminder")
            .addTag("smart_${type.name}")
            .build()

        try {
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            Log.d(TAG, "Re-enqueued $uniqueWorkName for $nextTime (slot=$slotKey)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to re-enqueue $uniqueWorkName: ${e.message}")
        }
    }
}
