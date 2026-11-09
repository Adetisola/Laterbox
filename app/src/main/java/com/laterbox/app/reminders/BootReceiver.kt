package com.laterbox.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.laterbox.app.LaterboxApplication
import com.laterbox.app.reminders.smart.SmartReminderCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIMEZONE_CHANGED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != "android.intent.action.TIME_SET" &&
            action != "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        ) {
            return
        }

        val appContainer = (context.applicationContext as? LaterboxApplication)?.container ?: return
        val linkRepository = appContainer.linkRepository
        val reminderScheduler = ReminderScheduler(context)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scheduledLinks = linkRepository.getScheduledReminders()
                val now = System.currentTimeMillis()
                for (link in scheduledLinks) {
                    link.reminderAt?.let { triggerAt ->
                        if (triggerAt > now) {
                            reminderScheduler.scheduleReminder(
                                linkId = link.id,
                                triggerAtMillis = triggerAt,
                                title = link.title,
                                url = link.originalUrl
                            )
                        }
                    }
                }
                val prefs = appContainer.userPreferencesRepository.userPreferencesFlow.first()
                SmartReminderCoordinator.sync(context, prefs.toSmartReminderSettings())
                Log.d(TAG, "Rescheduled reminders and synced smart reminders on $action")
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling reminders on action $action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
