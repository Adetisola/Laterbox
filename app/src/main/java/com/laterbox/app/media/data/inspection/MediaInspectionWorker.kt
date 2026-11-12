package com.laterbox.app.media.data.inspection

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.laterbox.app.LaterboxApplication
import com.laterbox.app.media.domain.model.InspectionResult
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.TimeUnit

class MediaInspectionWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_SAVED_LINK_ID = "saved_link_id"
        const val KEY_URL = "url"
        const val KEY_PREVIEW_IMAGE_URL = "preview_image_url"
        const val KEY_FORCE_REFRESH = "force_refresh"

        // Concurrency limit for background inspection lane (concurrency 1, low priority)
        private val inspectionSemaphore = Semaphore(1)

        fun schedule(
            context: Context,
            savedLinkId: Long,
            url: String,
            previewImageUrl: String? = null,
            forceRefresh: Boolean = false
        ) {
            val inputData = Data.Builder()
                .putLong(KEY_SAVED_LINK_ID, savedLinkId)
                .putString(KEY_URL, url)
                .putString(KEY_PREVIEW_IMAGE_URL, previewImageUrl)
                .putBoolean(KEY_FORCE_REFRESH, forceRefresh)
                .build()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<MediaInspectionWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag("media_inspection")
                .addTag("link_$savedLinkId")
                .build()

            try {
                val policy = if (forceRefresh) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
                WorkManager.getInstance(context)
                    .enqueueUniqueWork("media_inspect_$savedLinkId", policy, workRequest)
            } catch (e: Throwable) {
                android.util.Log.w("MediaInspectionWorker", "WorkManager schedule failed: ${e.message}")
            }
        }
    }

    override suspend fun doWork(): Result {
        val savedLinkId = inputData.getLong(KEY_SAVED_LINK_ID, -1L).takeIf { it > 0 } ?: return Result.failure()
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        var previewImageUrl = inputData.getString(KEY_PREVIEW_IMAGE_URL)
        val forceRefresh = inputData.getBoolean(KEY_FORCE_REFRESH, false)

        val app = appContext as? LaterboxApplication
        val container = app?.container
        if (previewImageUrl.isNullOrBlank() && container != null) {
            try {
                val savedLink = container.linkRepository.getLinkById(savedLinkId).first()
                previewImageUrl = savedLink?.previewImageUrl
            } catch (_: Throwable) {}
        }

        val inspectionManager = container?.mediaInspectionManager
            ?: MediaInspectionManager(
                appContext = appContext,
                inspectionDao = com.laterbox.app.data.local.LaterboxDatabase.getInstance(appContext).mediaInspectionDao(),
                platformResolver = container?.platformResolver ?: return Result.failure()
            )

        // Pre-check 1: Low memory condition
        val actManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        if (memInfo.lowMemory) {
            android.util.Log.i("MediaInspectionWorker", "Deferring background inspection for link $savedLinkId: device in low-memory state (availMem=${memInfo.availMem}, threshold=${memInfo.threshold}) -> returning Result.retry()")
            inspectionManager.markInspectionPending(savedLinkId, url)
            return Result.retry()
        }

        // Pre-check 2: Network capability validated
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val activeNet = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNet)
        val isValidated = caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        if (!isValidated) {
            android.util.Log.i("MediaInspectionWorker", "Deferring background inspection for link $savedLinkId: active network is not validated (attempt $runAttemptCount of 5) -> returning Result.retry()")
            inspectionManager.markInspectionPending(savedLinkId, url)
            return Result.retry()
        }

        val inspectionProcessId = "inspect_${savedLinkId}_worker_a${runAttemptCount}"

        return try {
            inspectionSemaphore.withPermit {
                if (isStopped) {
                    cleanupProcess(inspectionProcessId)
                    return Result.failure()
                }

                val res = inspectionManager.executeInspection(savedLinkId, url, previewImageUrl, forceRefresh)
                if (res.isSuccess) {
                    val result = res.getOrNull()
                    if (result is InspectionResult.Failed && result.retryable && runAttemptCount < 5) {
                        android.util.Log.i("MediaInspectionWorker", "Retrying inspection for link $savedLinkId after retryable failure (${result.code}: ${result.message}): attempt $runAttemptCount of 5 -> returning Result.retry()")
                        inspectionManager.markInspectionPending(savedLinkId, url)
                        Result.retry()
                    } else {
                        Result.success()
                    }
                } else {
                    if (runAttemptCount < 5) {
                        android.util.Log.w("MediaInspectionWorker", "Retrying inspection for link $savedLinkId after unhandled inspection error (${res.exceptionOrNull()?.message}): attempt $runAttemptCount of 5 -> returning Result.retry()")
                        inspectionManager.markInspectionPending(savedLinkId, url)
                        Result.retry()
                    } else {
                        Result.failure()
                    }
                }
            }
        } catch (e: Throwable) {
            cleanupProcess(inspectionProcessId)
            if (runAttemptCount < 5) {
                android.util.Log.w("MediaInspectionWorker", "Retrying inspection for link $savedLinkId after exception (${e.message}): attempt $runAttemptCount of 5 -> returning Result.retry()", e)
                inspectionManager.markInspectionPending(savedLinkId, url)
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private fun cleanupProcess(processId: String) {
        try {
            YoutubeDL.getInstance().destroyProcessById(processId)
        } catch (_: Throwable) {
        }
    }
}
