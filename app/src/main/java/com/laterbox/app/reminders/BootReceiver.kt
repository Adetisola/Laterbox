package com.laterbox.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.laterbox.app.LaterboxApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val appContainer = (context.applicationContext as? LaterboxApplication)?.container ?: return
        val linkRepository = appContainer.linkRepository
        val reminderScheduler = ReminderScheduler(context)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scheduledLinks = linkRepository.getScheduledReminders()
                for (link in scheduledLinks) {
                    link.reminderAt?.let { triggerAt ->
                        reminderScheduler.scheduleReminder(
                            linkId = link.id,
                            triggerAtMillis = triggerAt,
                            title = link.title,
                            url = link.originalUrl
                        )
                    }
                }
                val prefs = appContainer.userPreferencesRepository.userPreferencesFlow.first()
                if (prefs.smartRemindersEnabled) {
                    reminderScheduler.scheduleSmartDigest(
                        targetHour = prefs.smartReminderHour,
                        targetMinute = prefs.smartReminderMinute,
                        schedule = prefs.smartReminderSchedule
                    )
                }
                Log.d(TAG, "Rescheduled ${scheduledLinks.size} reminders and smart digest after boot")
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling reminders on boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
