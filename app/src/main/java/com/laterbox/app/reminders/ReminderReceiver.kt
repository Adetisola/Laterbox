package com.laterbox.app.reminders

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.laterbox.app.LaterboxApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val linkId = intent.getLongExtra(EXTRA_LINK_ID, -1L)
        if (linkId <= 0) return

        val appContainer = (context.applicationContext as? LaterboxApplication)?.container ?: return
        val linkRepository = appContainer.linkRepository
        val userPreferencesRepository = appContainer.userPreferencesRepository

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_TRIGGER_REMINDER -> {
                        val link = linkRepository.getLinkById(linkId).first()
                        if (link != null && !link.isArchived && !link.nudgeDismissed) {
                            val prefs = userPreferencesRepository.userPreferencesFlow.first()
                            val userName = prefs.userName.ifBlank { null }
                            val copy = if (prefs.smartRemindersEnabled) {
                                com.laterbox.app.ai.SmartDigestEngine.generateDigestCopy(link, userName)
                            } else null
                            NotificationHelper.showReminderNotification(context, link, userName, copy)
                            linkRepository.recordNudge(linkId)
                        }
                    }
                    ACTION_DISMISS_REMINDER -> {
                        linkRepository.dismissReminder(linkId)
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        notificationManager?.cancel(linkId.toInt())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling reminder broadcast for link $linkId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ReminderReceiver"
        const val ACTION_TRIGGER_REMINDER = "com.laterbox.app.action.TRIGGER_REMINDER"
        const val ACTION_DISMISS_REMINDER = "com.laterbox.app.action.DISMISS_REMINDER"
        const val EXTRA_LINK_ID = "extra_link_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_URL = "extra_url"
    }
}
