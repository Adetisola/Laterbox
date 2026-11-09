package com.laterbox.app.reminders.smart

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.laterbox.app.LaterboxApplication
import com.laterbox.app.ai.AiCopyResult
import com.laterbox.app.ai.AiFailure
import com.laterbox.app.ai.ReminderOrigin
import com.laterbox.app.ai.SmartDigestEngine
import com.laterbox.app.reminders.NotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class SmartReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? LaterboxApplication ?: return Result.failure()
        val scheduleTypeName = inputData.getString(KEY_SCHEDULE_TYPE) ?: return Result.failure()
        val slotKey = inputData.getString(KEY_SLOT_KEY) ?: return Result.failure()
        val targetMillis = inputData.getLong(KEY_TARGET_MILLIS, 0L)

        val type = try {
            SmartScheduleType.valueOf(scheduleTypeName)
        } catch (_: Exception) {
            return Result.failure()
        }

        try {
            val userPrefsRepo = app.container.userPreferencesRepository
            val prefs = userPrefsRepo.userPreferencesFlow.first()
            val settings = prefs.toSmartReminderSettings()

            if (!settings.enabled) {
                return Result.success()
            }

            // 1. Stale run check: if more than 3 hours late, skip delivery to prevent cascades
            val nowMillis = System.currentTimeMillis()
            if (targetMillis > 0 && (nowMillis - targetMillis) > STALE_THRESHOLD_MS) {
                Log.w(TAG, "Skipping stale reminder run for slot $slotKey (missed by ${(nowMillis - targetMillis) / 60_000} mins)")
                userPrefsRepo.recordDeliveredSlot(slotKey)
                SmartReminderCoordinator.scheduleNext(applicationContext, type)
                return Result.success()
            }

            // 2. Slot deduplication check: has this calendar slot already been delivered?
            if (prefs.deliveredSlotKeys.contains(slotKey)) {
                Log.d(TAG, "Slot $slotKey already delivered; skipping duplicate execution.")
                SmartReminderCoordinator.scheduleNext(applicationContext, type)
                return Result.success()
            }

            // 3. Notification permission check
            if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
                Log.w(TAG, "Notifications are disabled. Skipping delivery for slot $slotKey.")
                SmartReminderCoordinator.scheduleNext(applicationContext, type)
                return Result.success()
            }

            // 4. Find candidate link
            val linkRepo = app.container.linkRepository
            val candidate = linkRepo.getSmartDigestCandidate(
                maxNudgeCount = settings.persistence.maxNudges
            )
            if (candidate == null) {
                Log.d(TAG, "No candidate links to nudge in inbox.")
                userPrefsRepo.recordDeliveredSlot(slotKey)
                SmartReminderCoordinator.scheduleNext(applicationContext, type)
                return Result.success()
            }

            // 5. Generate AI copy with honest Google AI Studio provider
            val result = SmartDigestEngine.generateDigestCopy(
                link = candidate,
                userName = prefs.userName.ifBlank { null },
                origin = ReminderOrigin.SMART_DIGEST,
                customApiKey = prefs.geminiApiKey.ifBlank { null }
            )

            when (result) {
                is AiCopyResult.Success -> {
                    NotificationHelper.showSmartDigestNotification(
                        context = applicationContext,
                        link = candidate,
                        copy = result.copy
                    )
                    linkRepo.recordNudge(candidate.id)
                    userPrefsRepo.recordDeliveredSlot(slotKey)
                    SmartReminderCoordinator.scheduleNext(applicationContext, type)
                    return Result.success()
                }

                is AiCopyResult.Failure -> {
                    Log.w(TAG, "AI generation failed for slot $slotKey: ${result.reason.userMessage}")
                    val canRetry = when (result.reason) {
                        is AiFailure.Offline,
                        is AiFailure.Timeout,
                        is AiFailure.RateLimited,
                        is AiFailure.ServerError -> true
                        is AiFailure.NoApiKey,
                        is AiFailure.AuthError,
                        is AiFailure.EmptyResponse,
                        is AiFailure.MalformedResponse -> false
                    }

                    if (canRetry && runAttemptCount < 3) {
                        return Result.retry()
                    } else {
                        // Max retries exceeded or unrecoverable error:
                        // Be honest! Never present fake template text as AI-generated.
                        // Skip this slot quietly and schedule next occurrence.
                        userPrefsRepo.recordDeliveredSlot(slotKey)
                        SmartReminderCoordinator.scheduleNext(applicationContext, type)
                        return Result.success()
                    }
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in SmartReminderWorker", e)
            SmartReminderCoordinator.scheduleNext(applicationContext, type)
            return Result.failure()
        }
    }

    companion object {
        private const val TAG = "SmartReminderWorker"
        const val KEY_SCHEDULE_TYPE = "KEY_SCHEDULE_TYPE"
        const val KEY_SLOT_KEY = "KEY_SLOT_KEY"
        const val KEY_TARGET_MILLIS = "KEY_TARGET_MILLIS"
        private const val STALE_THRESHOLD_MS = 3 * 3600 * 1000L // 3 hours
    }
}
