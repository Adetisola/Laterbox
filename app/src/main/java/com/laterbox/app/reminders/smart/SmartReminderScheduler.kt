package com.laterbox.app.reminders.smart

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.WorkManager
import com.laterbox.app.LaterboxApplication
import com.laterbox.app.reminders.ReminderReceiver
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime

class SmartReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun canScheduleExact(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() == true
        } else {
            true
        }
    }

    /**
     * Cancels any legacy or delayed WorkManager smart reminder tasks
     * so AlarmManager has sole authoritative ownership of schedule timing.
     */
    fun cancelLegacyWorkManagerJobs() {
        try {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelAllWorkByTag("smart_reminder")
            workManager.cancelUniqueWork("laterbox_daily_digest_periodic")
            for (type in SmartScheduleType.values()) {
                workManager.cancelUniqueWork("smart_${type.name}")
            }
            Log.d(TAG, "Cancelled all legacy WorkManager smart reminder jobs")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel legacy WorkManager tasks: ${e.message}")
        }
    }

    /**
     * Synchronizes all smart reminder schedules according to user preferences.
     * Cancels alarms for disabled schedules; calculates and registers exact AlarmManager alarms for enabled ones.
     */
    suspend fun sync(
        settings: SmartReminderSettings,
        now: ZonedDateTime = ZonedDateTime.now()
    ) {
        cancelLegacyWorkManagerJobs()

        if (!settings.enabled) {
            cancelAll()
            Log.d(TAG, "Smart reminders disabled: all alarms cancelled")
            return
        }

        for (type in SmartScheduleType.values()) {
            val nextTime = ScheduleCalculator.nextTrigger(type, settings, now)
            if (nextTime == null) {
                cancelSchedule(type)
            } else {
                scheduleExact(type, nextTime)
            }
        }
    }

    /**
     * Schedules the next occurrence of a specific schedule type.
     */
    suspend fun scheduleNext(type: SmartScheduleType) {
        val app = context.applicationContext as? LaterboxApplication ?: return
        val prefs = app.container.userPreferencesRepository.userPreferencesFlow.first()
        val settings = prefs.toSmartReminderSettings()
        if (!settings.enabled) {
            cancelSchedule(type)
            return
        }

        val now = ZonedDateTime.now()
        // Compute from 1 minute in future to avoid immediately picking the current slot
        val nextTime = ScheduleCalculator.nextTrigger(type, settings, now.plusMinutes(1))
        if (nextTime != null) {
            scheduleExact(type, nextTime)
        } else {
            cancelSchedule(type)
        }
    }

    /**
     * Registers an exact one-time alarm for a specific schedule type and trigger time.
     */
    fun scheduleExact(type: SmartScheduleType, triggerTime: ZonedDateTime) {
        if (alarmManager == null) return
        val triggerAtMillis = triggerTime.toInstant().toEpochMilli()
        if (triggerAtMillis <= System.currentTimeMillis()) return

        val slotKey = ScheduleCalculator.slotKey(type, triggerTime)
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_TRIGGER_SMART_SCHEDULE
            putExtra(ReminderReceiver.EXTRA_SCHEDULE_TYPE, type.name)
            putExtra(ReminderReceiver.EXTRA_SLOT_KEY, slotKey)
            putExtra(ReminderReceiver.EXTRA_TARGET_MILLIS, triggerAtMillis)
        }

        val requestCode = requestCodeFor(type)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (canScheduleExact()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
                Log.d(TAG, "Scheduled exact alarm for ${type.name} at $triggerTime (slot=$slotKey, req=$requestCode)")
            } else {
                // Approximate fallback when permission is denied or revoked
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    alarmManager.setWindow(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        15 * 60 * 1000L, // 15-minute window
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
                Log.w(TAG, "Scheduled approximate alarm for ${type.name} at $triggerTime (exact permission not granted)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm for ${type.name}", e)
        }
    }

    fun cancelSchedule(type: SmartScheduleType) {
        if (alarmManager == null) return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_TRIGGER_SMART_SCHEDULE
        }
        val requestCode = requestCodeFor(type)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled alarm for ${type.name} (req=$requestCode)")
        }
    }

    fun cancelAll() {
        for (type in SmartScheduleType.values()) {
            cancelSchedule(type)
        }
    }

    companion object {
        private const val TAG = "SmartReminderScheduler"
        const val SMART_ALARM_BASE_REQUEST_CODE = 900_000

        fun requestCodeFor(type: SmartScheduleType): Int {
            return SMART_ALARM_BASE_REQUEST_CODE + type.ordinal + 1
        }
    }
}
