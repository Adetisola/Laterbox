package com.laterbox.app.reminders

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.laterbox.app.LaterboxApplication
import com.laterbox.app.ai.AiCopyResult
import com.laterbox.app.ai.PlainReminderCopy
import com.laterbox.app.ai.ReminderOrigin
import com.laterbox.app.ai.SmartDigestEngine
import com.laterbox.app.reminders.smart.SmartReminderCoordinator
import com.laterbox.app.reminders.smart.SmartReminderScheduler
import com.laterbox.app.reminders.smart.SmartScheduleType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        val appContainer = (context.applicationContext as? LaterboxApplication)?.container ?: return
        val linkRepository = appContainer.linkRepository
        val userPreferencesRepository = appContainer.userPreferencesRepository

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_TRIGGER_REMINDER -> {
                        val linkId = intent.getLongExtra(EXTRA_LINK_ID, -1L)
                        if (linkId <= 0) return@launch

                        var link = linkRepository.getLinkById(linkId).first()
                        if (link != null && !link.isArchived && !link.nudgeDismissed) {
                            // Non-blocking metadata enrichment if preview image is missing
                            if (link.previewImageUrl.isNullOrBlank()) {
                                withTimeoutOrNull(2500L) {
                                    linkRepository.fetchMetadataNow(linkId)
                                }
                                link = linkRepository.getLinkById(linkId).first() ?: link
                            }

                            val prefs = userPreferencesRepository.userPreferencesFlow.first()
                            val userName = prefs.userName.ifBlank { null }
                            val aiResult = SmartDigestEngine.generateDigestCopy(
                                link = link,
                                userName = userName,
                                origin = ReminderOrigin.MANUAL,
                                customApiKey = prefs.geminiApiKey.ifBlank { null }
                            )

                            val copy = when (aiResult) {
                                is AiCopyResult.Success -> aiResult.copy
                                is AiCopyResult.Failure -> {
                                    Log.d(TAG, "AI reminder generation failed (${aiResult.reason.userMessage}); using honest plain copy")
                                    PlainReminderCopy.forManual(link)
                                }
                            }

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

                    ACTION_TRIGGER_SMART_SCHEDULE -> {
                        val scheduleTypeName = intent.getStringExtra(EXTRA_SCHEDULE_TYPE) ?: return@launch
                        val slotKey = intent.getStringExtra(EXTRA_SLOT_KEY) ?: return@launch
                        val targetMillis = intent.getLongExtra(EXTRA_TARGET_MILLIS, 0L)
                        val type = try {
                            SmartScheduleType.valueOf(scheduleTypeName)
                        } catch (_: Exception) {
                            return@launch
                        }

                        val scheduler = SmartReminderScheduler(context)
                        val prefs = userPreferencesRepository.userPreferencesFlow.first()
                        val settings = prefs.toSmartReminderSettings()

                        if (!settings.enabled) {
                            Log.d(TAG, "Smart reminders master switch disabled; skipping $slotKey")
                            return@launch
                        }

                        // 1. Stale schedule check (> 3 hours late)
                        val nowMillis = System.currentTimeMillis()
                        if (targetMillis > 0 && (nowMillis - targetMillis) > STALE_THRESHOLD_MS) {
                            Log.w(TAG, "Missed schedule slot $slotKey by ${(nowMillis - targetMillis) / 60_000} mins; skipping delivery")
                            scheduler.scheduleNext(type)
                            return@launch
                        }

                        // 2. Slot deduplication check
                        if (prefs.deliveredSlotKeys.contains(slotKey)) {
                            Log.d(TAG, "Slot $slotKey already delivered; skipping duplicate execution")
                            scheduler.scheduleNext(type)
                            return@launch
                        }

                        // 3. Notification permission check
                        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                            Log.w(TAG, "Notifications disabled; skipping slot $slotKey")
                            scheduler.scheduleNext(type)
                            return@launch
                        }

                        // 4. Candidate link selection
                        val candidate = linkRepository.getSmartDigestCandidate(
                            maxNudgeCount = settings.persistence.maxNudges
                        )
                        if (candidate == null) {
                            Log.d(TAG, "No candidate links to nudge in inbox")
                            scheduler.scheduleNext(type)
                            return@launch
                        }

                        // 5. Enrichment: re-read latest from DB and best-effort fetch if missing
                        var currentLink = linkRepository.getLinkById(candidate.id).first() ?: candidate
                        if (currentLink.previewImageUrl.isNullOrBlank()) {
                            withTimeoutOrNull(2500L) {
                                linkRepository.fetchMetadataNow(candidate.id)
                            }
                            currentLink = linkRepository.getLinkById(candidate.id).first() ?: candidate
                        }

                        // 6. Generate AI copy with Google AI Studio
                        val result = SmartDigestEngine.generateDigestCopy(
                            link = currentLink,
                            userName = prefs.userName.ifBlank { null },
                            origin = ReminderOrigin.SMART_DIGEST,
                            customApiKey = prefs.geminiApiKey.ifBlank { null }
                        )

                        when (result) {
                            is AiCopyResult.Success -> {
                                val posted = NotificationHelper.showSmartDigestNotification(
                                    context = context,
                                    link = currentLink,
                                    copy = result.copy
                                )
                                if (posted) {
                                    linkRepository.recordNudge(currentLink.id)
                                    userPreferencesRepository.recordDeliveredSlot(slotKey)
                                    Log.d(TAG, "Successfully delivered smart reminder for slot $slotKey")
                                }
                                scheduler.scheduleNext(type)
                            }

                            is AiCopyResult.Failure -> {
                                Log.w(TAG, "AI reminder generation failed for slot $slotKey: ${result.reason.userMessage}")
                                // Never record failed delivery as successfully delivered!
                                // Advance to next occurrence
                                scheduler.scheduleNext(type)
                            }
                        }
                    }

                    ACTION_TRIGGER_SMART_DIGEST -> {
                        // Legacy alarm action migration: cancel old alarm and sync via scheduler
                        ReminderScheduler(context).cancelLegacySmartDigest()
                        val prefs = userPreferencesRepository.userPreferencesFlow.first()
                        SmartReminderCoordinator.sync(context, prefs.toSmartReminderSettings())
                    }

                    ACTION_DISMISS_REMINDER -> {
                        val linkId = intent.getLongExtra(EXTRA_LINK_ID, -1L)
                        if (linkId > 0) {
                            linkRepository.dismissReminder(linkId)
                            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                            val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, linkId.toInt())
                            notificationManager?.cancel(notifId)
                            notificationManager?.cancel(linkId.toInt())
                            notificationManager?.cancel((linkId + 500_000L).toInt())
                            Log.d(TAG, "Dismissed reminder for link $linkId (notifId $notifId)")
                        }
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
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
        const val ACTION_TRIGGER_SMART_SCHEDULE = "com.laterbox.app.action.TRIGGER_SMART_SCHEDULE"
        const val ACTION_TRIGGER_SMART_DIGEST = "com.laterbox.app.action.TRIGGER_SMART_DIGEST"
        const val ACTION_DISMISS_REMINDER = "com.laterbox.app.action.DISMISS_REMINDER"
        const val EXTRA_LINK_ID = "extra_link_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_SCHEDULE_TYPE = "extra_schedule_type"
        const val EXTRA_SLOT_KEY = "extra_slot_key"
        const val EXTRA_TARGET_MILLIS = "extra_target_millis"
        const val STALE_THRESHOLD_MS = 3 * 60 * 60 * 1000L
    }
}
