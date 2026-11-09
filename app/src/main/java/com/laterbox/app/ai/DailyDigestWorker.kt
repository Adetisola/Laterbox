package com.laterbox.app.ai

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.laterbox.app.LaterboxApplication
import com.laterbox.app.reminders.NotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class DailyDigestWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? LaterboxApplication ?: return Result.failure()
        return try {
            val prefs = app.container.userPreferencesRepository.userPreferencesFlow.first()
            if (!prefs.smartRemindersEnabled) {
                return Result.success()
            }

            val candidates = app.container.linkRepository.getSmartDigestCandidates(
                maxNudgeCount = prefs.smartReminderPersistence.maxNudges
            )
            val candidate = candidates.firstOrNull() ?: return Result.success()

            val copy = SmartDigestEngine.generateDigestCopy(
                link = candidate,
                userName = prefs.userName.ifBlank { null },
                engine = prefs.aiEngine
            )

            NotificationHelper.showSmartDigestNotification(
                context = applicationContext,
                link = candidate,
                copy = copy
            )

            app.container.linkRepository.recordNudge(candidate.id)
            Result.success()
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME_PERIODIC = "laterbox_daily_digest_periodic"

        fun enqueuePeriodic(
            context: Context,
            schedule: com.laterbox.app.data.preferences.SmartReminderSchedule = com.laterbox.app.data.preferences.SmartReminderSchedule.DAILY_DIGEST,
            targetHour: Int = 20,
            targetMinute: Int = 0
        ) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val (intervalHours, initialDelayMinutes) = calculateScheduleTiming(schedule, targetHour, targetMinute)

                val workRequest = PeriodicWorkRequestBuilder<DailyDigestWorker>(
                    repeatInterval = intervalHours,
                    repeatIntervalTimeUnit = TimeUnit.HOURS
                )
                    .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
                )
            } catch (_: Exception) {
                // Safeguard against uninitialized WorkManager in test environments
            }
        }

        private fun calculateScheduleTiming(
            schedule: com.laterbox.app.data.preferences.SmartReminderSchedule,
            targetHour: Int,
            targetMinute: Int
        ): Pair<Long, Long> {
            val now = java.util.Calendar.getInstance()
            val target = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }

            return when (schedule) {
                com.laterbox.app.data.preferences.SmartReminderSchedule.TWICE_DAILY -> {
                    target.set(java.util.Calendar.MINUTE, 0)
                    if (now.get(java.util.Calendar.HOUR_OF_DAY) < 9) {
                        target.set(java.util.Calendar.HOUR_OF_DAY, 9)
                    } else if (now.get(java.util.Calendar.HOUR_OF_DAY) < 20) {
                        target.set(java.util.Calendar.HOUR_OF_DAY, 20)
                    } else {
                        target.add(java.util.Calendar.DAY_OF_YEAR, 1)
                        target.set(java.util.Calendar.HOUR_OF_DAY, 9)
                    }
                    val delay = ((target.timeInMillis - now.timeInMillis) / 60_000L).coerceAtLeast(0L)
                    Pair(12L, delay)
                }
                com.laterbox.app.data.preferences.SmartReminderSchedule.WEEKEND_ROUNDUP -> {
                    target.set(java.util.Calendar.HOUR_OF_DAY, 10)
                    target.set(java.util.Calendar.MINUTE, 0)
                    val dayOfWeek = now.get(java.util.Calendar.DAY_OF_WEEK)
                    val daysUntilSaturday = (java.util.Calendar.SATURDAY - dayOfWeek + 7) % 7
                    val addDays = if (daysUntilSaturday == 0 && now.get(java.util.Calendar.HOUR_OF_DAY) >= 10) 7 else daysUntilSaturday
                    target.add(java.util.Calendar.DAY_OF_YEAR, addDays)
                    val delay = ((target.timeInMillis - now.timeInMillis) / 60_000L).coerceAtLeast(0L)
                    Pair(24L, delay)
                }
                com.laterbox.app.data.preferences.SmartReminderSchedule.CONTINUOUS_MONITOR -> {
                    Pair(6L, 15L) // Check every 6 hours, starting in 15 minutes
                }
                com.laterbox.app.data.preferences.SmartReminderSchedule.DAILY_DIGEST -> {
                    target.set(java.util.Calendar.HOUR_OF_DAY, targetHour)
                    target.set(java.util.Calendar.MINUTE, targetMinute)
                    if (target.timeInMillis <= now.timeInMillis) {
                        target.add(java.util.Calendar.DAY_OF_YEAR, 1)
                    }
                    val delay = ((target.timeInMillis - now.timeInMillis) / 60_000L).coerceAtLeast(0L)
                    Pair(24L, delay)
                }
            }
        }

        fun cancelPeriodic(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
            } catch (_: Exception) {
                // Safeguard against test environments
            }
        }
    }
}
