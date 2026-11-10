package com.laterbox.app.media.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.laterbox.app.LaterboxApplication
import com.laterbox.app.R
import com.laterbox.app.data.preferences.MediaDownloadNetworkPolicy
import com.laterbox.app.media.data.db.entity.DownloadedAssetEntity
import com.laterbox.app.media.data.storage.MediaStorageManager
import com.laterbox.app.media.domain.model.AssetFileStatus
import com.laterbox.app.media.domain.model.DownloadState
import com.laterbox.app.media.domain.model.FailureCode
import com.laterbox.app.media.domain.model.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID

class DownloadWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val app = appContext as? LaterboxApplication
        val container = app?.container
        val database = container?.database ?: com.laterbox.app.data.local.LaterboxDatabase.getInstance(appContext)
        val userPrefs = container?.userPreferencesRepository ?: com.laterbox.app.data.preferences.UserPreferencesRepository(appContext)
        val taskDao = database.downloadTaskDao()
        val assetDao = database.downloadedAssetDao()
        val storageManager = MediaStorageManager(appContext)

        val task = taskDao.getEntityById(taskId) ?: return Result.failure()
        if (task.status == DownloadState.COMPLETED.name || task.status == DownloadState.CANCELLED.name) {
            return Result.success()
        }

        // 1. Network policy evaluation
        val prefs = userPrefs.userPreferencesFlow.first()
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val isMetered = connectivityManager?.isActiveNetworkMetered ?: false

        if (isMetered && !task.allowCellularOverride) {
            val isBlocked = when (prefs.mediaDownloadNetworkPolicy) {
                MediaDownloadNetworkPolicy.WIFI_ONLY -> true
                MediaDownloadNetworkPolicy.WIFI_OVER_20MB -> {
                    // Only hold if size is explicitly known to exceed 20 MB; do not block streams with unknown size
                    task.totalBytes != null && task.totalBytes > 20L * 1024 * 1024
                }
                MediaDownloadNetworkPolicy.ANY_NETWORK -> false
            }

            if (isBlocked) {
                taskDao.updateStatus(taskId, DownloadState.WAITING_FOR_WIFI.name)
                // Schedule WorkManager constraint for unmetered network
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build()
                val retryRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setConstraints(constraints)
                    .setInputData(inputData)
                    .build()
                WorkManager.getInstance(appContext).enqueueUniqueWork(
                    uniqueWorkName(taskId),
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    retryRequest
                )
                return Result.success()
            }
        }

        // 2. Storage space verification
        if (!storageManager.hasSufficientSpace(task.totalBytes)) {
            taskDao.updateError(
                id = taskId,
                status = DownloadState.FAILED.name,
                errorCode = FailureCode.INSUFFICIENT_STORAGE.name,
                errorMessage = FailureCode.INSUFFICIENT_STORAGE.userMessage,
                retryCount = task.retryCount
            )
            return Result.failure()
        }

        // 3. Setup low-importance silent notification & attempt setForeground
        ensureNotificationChannel()
        val notificationId = taskId.hashCode()
        val initialNotification = buildNotification(task.title ?: "Downloading media", 0, null, taskId)
        try {
            val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(notificationId, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                ForegroundInfo(notificationId, initialNotification)
            }
            setForeground(foregroundInfo)
        } catch (_: Exception) {
            // Graceful fallback: downloads continue even if notification permission is denied
        }

        // 4. Download transfer under concurrency gate
        return try {
            downloadSemaphore.withPermit {
                taskDao.updateStatus(taskId, DownloadState.DOWNLOADING.name)
                val partFile = storageManager.getPartFile(taskId)

                val result = if (task.formatSelector != null) {
                    val ytDlpDownloader = YtDlpDownloader(appContext)
                    ytDlpDownloader.download(
                        url = task.sourceUrl,
                        formatSelector = task.formatSelector,
                        destinationFile = partFile,
                        taskId = taskId,
                        onProgress = { bytesDownloaded, totalBytes, percent ->
                            taskDao.updateProgress(taskId, percent, bytesDownloaded, totalBytes)
                            try {
                                val updatedNotification = buildNotification(
                                    title = task.title ?: "Downloading media",
                                    progress = percent,
                                    totalBytes = totalBytes,
                                    taskId = taskId
                                )
                                notificationManager.notify(notificationId, updatedNotification)
                            } catch (_: Exception) {
                            }
                        }
                    )
                } else {
                    val httpDownloader = HttpDownloader()
                    httpDownloader.download(
                        url = task.sourceUrl,
                        destinationFile = partFile,
                        onProgress = { bytesDownloaded, totalBytes, percent ->
                            taskDao.updateProgress(taskId, percent, bytesDownloaded, totalBytes)
                            try {
                                val updatedNotification = buildNotification(
                                    title = task.title ?: "Downloading media",
                                    progress = percent,
                                    totalBytes = totalBytes,
                                    taskId = taskId
                                )
                                notificationManager.notify(notificationId, updatedNotification)
                            } catch (_: Exception) {
                            }
                        }
                    )
                }

                when (result) {
                    is DownloadResult.Success -> {
                        // 5. Finalize & Deduplicate
                        taskDao.updateStatus(taskId, DownloadState.PROCESSING.name)
                        val mediaType = try { MediaType.valueOf(task.mediaType) } catch (_: Exception) { MediaType.OTHER }

                        if (mediaType == MediaType.AUDIO && (!task.artist.isNullOrBlank() || !task.title.isNullOrBlank())) {
                            AudioTaggingProcessor.tagAudioFile(
                                appContext = appContext,
                                audioFile = partFile,
                                title = task.title,
                                artist = task.artist,
                                album = task.album,
                                artworkUrl = task.artworkUrl,
                                spotifyTrackId = task.spotifyTrackId
                            )
                        }

                        val checksum = com.laterbox.app.media.data.storage.ChecksumUtil.calculateSha256(partFile)
                        val existingAsset = assetDao.findByChecksum(checksum)
                        val duplicatePath = if (existingAsset != null && storageManager.resolveFile(existingAsset.localPath).exists()) {
                            existingAsset.localPath
                        } else null

                        val finalized = storageManager.finalizeDownload(
                            taskId = taskId,
                            mediaType = mediaType,
                            suggestedExt = task.formatSelector,
                            existingDuplicatePath = duplicatePath
                        )

                        val assetId = UUID.randomUUID().toString()
                        val assetEntity = DownloadedAssetEntity(
                            id = assetId,
                            savedLinkId = task.savedLinkId,
                            sourcePlatform = task.sourcePlatform,
                            originalSourceUrl = task.sourceUrl,
                            title = task.title,
                            mimeType = finalized.mimeType,
                            mediaType = mediaType.name,
                            localPath = finalized.relativePath,
                            exportedUri = null,
                            fileStatus = AssetFileStatus.AVAILABLE.name,
                            fileSize = finalized.fileSize,
                            durationMs = null,
                            width = null,
                            height = null,
                            qualityLabel = task.qualityLabel,
                            checksum = finalized.checksum,
                            createdAt = System.currentTimeMillis(),
                            artist = task.artist,
                            album = task.album,
                            artworkUrl = task.artworkUrl,
                            spotifyTrackId = task.spotifyTrackId,
                            versionTag = task.versionTag
                        )

                        assetDao.insertAsset(assetEntity)
                        taskDao.markCompleted(taskId, assetId)
                        notificationManager.cancel(notificationId)
                        Result.success()
                    }
                    is DownloadResult.Failure -> {
                        val isRetryable = result.code.isRetryable
                        val newRetry = task.retryCount + 1
                        if (isRetryable && newRetry <= 3) {
                            taskDao.updateError(
                                id = taskId,
                                status = DownloadState.QUEUED.name,
                                errorCode = result.code.name,
                                errorMessage = result.message,
                                retryCount = newRetry
                            )
                            Result.retry()
                        } else {
                            taskDao.updateError(
                                id = taskId,
                                status = DownloadState.FAILED.name,
                                errorCode = result.code.name,
                                errorMessage = result.message,
                                retryCount = newRetry
                            )
                            Result.failure()
                        }
                    }
                }
            }
        } catch (ce: CancellationException) {
            if (task.formatSelector != null) {
                YtDlpDownloader(appContext).cancel(taskId)
            }
            val currentTask = taskDao.getEntityById(taskId)
            if (currentTask?.status != DownloadState.CANCELLED.name) {
                if (isStopped) {
                    // Timeout or worker cancelled by OS: pause so it resumes cleanly
                    taskDao.updateStatus(taskId, DownloadState.PAUSED.name)
                }
            }
            throw ce
        } catch (e: Exception) {
            val newRetry = task.retryCount + 1
            taskDao.updateError(
                id = taskId,
                status = if (newRetry <= 3) DownloadState.QUEUED.name else DownloadState.FAILED.name,
                errorCode = FailureCode.UNKNOWN.name,
                errorMessage = e.localizedMessage ?: "Unknown error",
                retryCount = newRetry
            )
            if (newRetry <= 3) Result.retry() else Result.failure()
        } finally {
            notificationManager.cancel(notificationId)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Silent progress notifications for Laterbox media downloads"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        title: String,
        progress: Int,
        totalBytes: Long?,
        taskId: String
    ): android.app.Notification {
        val cancelIntent = Intent(appContext, DownloadCancelReceiver::class.java).apply {
            putExtra(DownloadCancelReceiver.EXTRA_TASK_ID, taskId)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            appContext,
            taskId.hashCode(),
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isIndeterminate = totalBytes == null || totalBytes <= 0L
        return NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle("Laterbox Download")
            .setContentText(title)
            .setSmallIcon(R.drawable.ic_stat_laterbox)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, progress, isIndeterminate)
            .addAction(R.drawable.ic_huge_close, "Cancel", cancelPendingIntent)
            .build()
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val CHANNEL_ID = "laterbox_media_downloads"
        val downloadSemaphore = Semaphore(permits = 2)

        fun uniqueWorkName(taskId: String): String = "media_download_$taskId"
    }
}
