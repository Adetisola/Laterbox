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

        fun buildProcessId(savedLinkId: Long, runAttemptCount: Int): String =
            "inspect_${savedLinkId}_worker_a${runAttemptCount}"

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

        if (!forceRefresh && inspectionManager.isInProcessActive(savedLinkId)) {
            android.util.Log.i("MediaInspectionWorker", "In-process inspection already running for link $savedLinkId; skipping duplicate worker run")
            return Result.success()
        }

        // Pre-check 1: Low memory condition
        val actManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        if (memInfo.lowMemory) {
            android.util.Log.i("MediaInspectionWorker", "Deferring background inspection for link $savedLinkId: device in low-memory state (availMem=${memInfo.availMem}, threshold=${memInfo.threshold}) -> returning Result.retry()")
            inspectionManager.markInspectionDeferred(savedLinkId, url, status = "DEFERRED", reason = "Paused while device memory is low. Tap Retry to run now.")
            return Result.retry()
        }

        // Pre-check 2: Network capability validated
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val activeNet = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNet)
        val isValidated = caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        if (!isValidated) {
            android.util.Log.i("MediaInspectionWorker", "Deferring background inspection for link $savedLinkId: active network is not validated (attempt $runAttemptCount of 5) -> returning Result.retry()")
            inspectionManager.markInspectionDeferred(savedLinkId, url, status = "WAITING_FOR_NETWORK", reason = "Waiting for a working internet connection. Tap Retry to check now.")
            return Result.retry()
        }

        val inspectionProcessId = buildProcessId(savedLinkId, runAttemptCount)

        return try {
            inspectionSemaphore.withPermit {
                if (isStopped) {
                    cleanupProcess(inspectionProcessId)
                    return Result.failure()
                }

                val res = inspectionManager.executeInspection(
                    savedLinkId = savedLinkId,
                    url = url,
                    previewImageUrl = previewImageUrl,
                    forceRefresh = forceRefresh,
                    explicitProcessId = inspectionProcessId,
                    notifyOnTerminalFailure = false
                )
                if (res.isSuccess) {
                    val result = res.getOrNull()
                    if (result is InspectionResult.Failed && result.retryable && runAttemptCount < 5 && !isStopped) {
                        android.util.Log.i("MediaInspectionWorker", "Retrying inspection for link $savedLinkId after retryable failure (${result.code}: ${result.message}): attempt $runAttemptCount of 5 -> returning Result.retry()")
                        inspectionManager.markInspectionDeferred(savedLinkId, url, status = "QUEUED", reason = "Media check queued to retry automatically in the background.")
                        Result.retry()
                    } else {
                        if (result is InspectionResult.Failed && !isStopped) {
                            notifyTerminalWorkerFailure(container, savedLinkId, url, result.message ?: "Inspection failed", inspectionProcessId)
                        }
                        Result.success()
                    }
                } else {
                    val ex = res.exceptionOrNull()
                    if (ex is kotlinx.coroutines.CancellationException || isStopped) {
                        return Result.failure()
                    }
                    if (runAttemptCount < 5 && !isStopped) {
                        android.util.Log.w("MediaInspectionWorker", "Retrying inspection for link $savedLinkId after unhandled inspection error (${ex?.message}): attempt $runAttemptCount of 5 -> returning Result.retry()")
                        inspectionManager.markInspectionDeferred(savedLinkId, url, status = "QUEUED", reason = "Media check queued to retry automatically in the background.")
                        Result.retry()
                    } else {
                        notifyTerminalWorkerFailure(container, savedLinkId, url, ex?.message ?: "Inspection failed", inspectionProcessId)
                        Result.failure()
                    }
                }
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            cleanupProcess(inspectionProcessId)
            Result.failure()
        } catch (e: Throwable) {
            cleanupProcess(inspectionProcessId)
            if (isStopped || e is kotlinx.coroutines.CancellationException) {
                Result.failure()
            } else if (runAttemptCount < 5) {
                android.util.Log.w("MediaInspectionWorker", "Retrying inspection for link $savedLinkId after exception (${e.message}): attempt $runAttemptCount of 5 -> returning Result.retry()", e)
                inspectionManager.markInspectionDeferred(savedLinkId, url, status = "QUEUED", reason = "Media check queued to retry automatically in the background.")
                Result.retry()
            } else {
                notifyTerminalWorkerFailure(container, savedLinkId, url, e.message ?: "Inspection failed", inspectionProcessId)
                Result.failure()
            }
        }
    }

    private suspend fun notifyTerminalWorkerFailure(
        container: com.laterbox.app.di.AppContainer?,
        savedLinkId: Long,
        url: String,
        errorMessage: String,
        dedupKey: String
    ) {
        if (container == null) return
        try {
            val link = container.database.savedLinkDao().getEntityById(savedLinkId)
            val title = link?.title ?: url
            container.activityEventRepository.recordEvent(
                type = com.laterbox.app.data.repository.ActivityEventType.INSPECTION_FAILED,
                title = "Media inspection failed: $title",
                detail = errorMessage,
                linkId = savedLinkId
            )
            container.contextualNotificationEngine.notifyExtractionFailure(
                savedLinkId = savedLinkId,
                url = url,
                linkTitle = title,
                errorMessage = errorMessage,
                dedupKey = dedupKey
            )
        } catch (_: Throwable) {}
    }

    private fun cleanupProcess(processId: String) {
        try {
            YoutubeDL.getInstance().destroyProcessById(processId)
        } catch (_: Throwable) {}
        try {
            val app = appContext as? LaterboxApplication
            app?.container?.warmYtDlpServer?.cancel(processId)
        } catch (_: Throwable) {}
    }
}
