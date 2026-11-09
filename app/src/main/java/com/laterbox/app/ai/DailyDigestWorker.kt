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

            val candidates = app.container.linkRepository.getSmartDigestCandidates()
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

        fun enqueuePeriodic(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val workRequest = PeriodicWorkRequestBuilder<DailyDigestWorker>(
                    repeatInterval = 24,
                    repeatIntervalTimeUnit = TimeUnit.HOURS
                )
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
            } catch (_: Exception) {
                // Safeguard against uninitialized WorkManager in test environments
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
