package com.laterbox.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log

class ReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun scheduleReminder(linkId: Long, triggerAtMillis: Long, title: String? = null, url: String? = null) {
        if (alarmManager == null || triggerAtMillis <= System.currentTimeMillis()) {
            return
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_TRIGGER_REMINDER
            putExtra(ReminderReceiver.EXTRA_LINK_ID, linkId)
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_URL, url)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            linkId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // Android 14+ safe: setWindow with 5-minute battery-friendly window avoids SCHEDULE_EXACT_ALARM permission gating
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                5 * 60_000L,
                pendingIntent
            )
            Log.d(TAG, "Scheduled reminder for link $linkId at $triggerAtMillis")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule reminder for link $linkId", e)
        }
    }

    fun cancelReminder(linkId: Long) {
        if (alarmManager == null) return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_TRIGGER_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            linkId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled reminder for link $linkId")
        }
    }

    companion object {
        private const val TAG = "ReminderScheduler"
    }
}
