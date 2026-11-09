package com.laterbox.app.reminders

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.laterbox.app.LaterboxApplication
import com.laterbox.app.ai.ReminderOrigin
import com.laterbox.app.ai.SmartDigestEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val linkId = intent.getLongExtra(EXTRA_LINK_ID, -1L)
        if (action != ACTION_TRIGGER_SMART_DIGEST && linkId <= 0) return

        val appContainer = (context.applicationContext as? LaterboxApplication)?.container ?: return
        val linkRepository = appContainer.linkRepository
        val userPreferencesRepository = appContainer.userPreferencesRepository

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_TRIGGER_REMINDER -> {
                        val link = linkRepository.getLinkById(linkId).first()
                        if (link != null && !link.isArchived && !link.nudgeDismissed) {
                            val prefs = userPreferencesRepository.userPreferencesFlow.first()
                            val userName = prefs.userName.ifBlank { null }
                            val copy = SmartDigestEngine.generateDigestCopy(
                                link = link,
                                userName = userName,
                                origin = ReminderOrigin.MANUAL,
                                customApiKey = prefs.geminiApiKey.ifBlank { null }
                            )

                            NotificationHelper.showReminderNotification(
                                context = context,
                                link = link,
                                userName = userName,
                                origin = ReminderOrigin.MANUAL,
                                copy = copy
                            )
                            linkRepository.recordNudge(linkId)
                        }
                    }
                    ACTION_TRIGGER_SMART_DIGEST -> {
                        val prefs = userPreferencesRepository.userPreferencesFlow.first()
                        if (prefs.smartRemindersEnabled) {
                            val candidate = linkRepository.getSmartDigestCandidate(
                                maxNudgeCount = prefs.smartReminderPersistence.maxNudges
                            )
                            if (candidate != null) {
                                val userName = prefs.userName.ifBlank { null }
                                val copy = SmartDigestEngine.generateDigestCopy(
                                    link = candidate,
                                    userName = userName,
                                    origin = ReminderOrigin.SMART_DIGEST,
                                    customApiKey = prefs.geminiApiKey.ifBlank { null }
                                )

                                NotificationHelper.showReminderNotification(
                                    context = context,
                                    link = candidate,
                                    userName = userName,
                                    origin = ReminderOrigin.SMART_DIGEST,
                                    copy = copy
                                )
                                linkRepository.recordNudge(candidate.id)
                            }
                            // Reschedule next exact alarm for tomorrow
                            ReminderScheduler(context).scheduleSmartDigest(
                                targetHour = prefs.smartReminderHour,
                                targetMinute = prefs.smartReminderMinute,
                                schedule = prefs.smartReminderSchedule
                            )
                        }
                    }
                    ACTION_DISMISS_REMINDER -> {
                        linkRepository.dismissReminder(linkId)
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, linkId.toInt())
                        notificationManager?.cancel(notifId)
                        notificationManager?.cancel(linkId.toInt())
                        notificationManager?.cancel((linkId + 500_000L).toInt())
                        Log.d(TAG, "Dismissed reminder for link $linkId (notifId $notifId)")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling reminder broadcast for action $action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ReminderReceiver"
        const val ACTION_TRIGGER_REMINDER = "com.laterbox.app.action.TRIGGER_REMINDER"
        const val ACTION_TRIGGER_SMART_DIGEST = "com.laterbox.app.action.TRIGGER_SMART_DIGEST"
        const val ACTION_DISMISS_REMINDER = "com.laterbox.app.action.DISMISS_REMINDER"
        const val EXTRA_LINK_ID = "extra_link_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_URL = "extra_url"
    }
}
