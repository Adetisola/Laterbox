package com.laterbox.app.data.metadata

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.laterbox.app.LaterboxApplication
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class MetadataSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? LaterboxApplication ?: return Result.failure()
        return try {
            val allClean = app.container.linkRepository.syncPendingLinks()
            if (allClean) Result.success() else Result.retry()
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "laterbox_metadata_sync"

        fun enqueue(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val workRequest = OneTimeWorkRequestBuilder<MetadataSyncWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    workRequest
                )
            } catch (_: Exception) {
                // Safeguard against uninitialized WorkManager in Robolectric testing environments
            }
        }
    }
}
