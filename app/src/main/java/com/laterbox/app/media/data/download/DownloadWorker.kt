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
import com.laterbox.app.media.data.storage.ThumbnailGenerator
import com.laterbox.app.media.domain.model.AssetFileStatus
import com.laterbox.app.media.domain.model.DownloadStage
import com.laterbox.app.media.domain.model.DownloadState
import com.laterbox.app.media.domain.model.FailureCode
import com.laterbox.app.media.domain.model.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID

private data class ProgressEvent(
    val bytes: Long,
    val totalBytes: Long?,
    val percent: Int,
    val stage: DownloadStage?
)

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
                    task.totalBytes != null && task.totalBytes > 20L * 1024 * 1024
                }
                MediaDownloadNetworkPolicy.ANY_NETWORK -> false
            }

            if (isBlocked) {
                taskDao.updateStatus(taskId, DownloadState.WAITING_FOR_WIFI.name)
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
        val initialNotification = buildNotification(task.title ?: "Downloading media", 0, null, taskId, DownloadStage.PREPARING.label)
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
        val progressChannel = Channel<ProgressEvent>(Channel.CONFLATED)
        val progressJob = CoroutineScope(coroutineContext).launch {
            var lastReportedStage: DownloadStage? = null
            var lastDbUpdateTime = 0L
            var lastDbPercent = -1

            for (event in progressChannel) {
                val now = System.currentTimeMillis()
                val stageChanged = event.stage != null && event.stage != lastReportedStage
                val percentAdvancement = event.percent - lastDbPercent >= 1 || event.percent == 100
                val timeElapsed = now - lastDbUpdateTime >= 500L

                if (stageChanged || (timeElapsed && percentAdvancement)) {
                    lastDbUpdateTime = now
                    lastDbPercent = event.percent
                    if (event.stage != null) lastReportedStage = event.stage

                    val current = taskDao.getEntityById(taskId)
                    if (current?.status == DownloadState.CANCELLED.name) break

                    taskDao.updateProgress(taskId, event.percent, event.bytes, event.totalBytes)
                    if (lastReportedStage != null) {
                        taskDao.updateStage(taskId, lastReportedStage?.name, null)
                    }

                    try {
                        val updatedNotification = buildNotification(
                            title = task.title ?: "Downloading media",
                            progress = event.percent,
                            totalBytes = event.totalBytes,
                            taskId = taskId,
                            stageLabel = lastReportedStage?.label
                        )
                        notificationManager.notify(notificationId, updatedNotification)
                    } catch (_: Exception) {
                    }
                }
            }
        }

        return try {
            downloadSemaphore.withPermit {
                taskDao.updateStatusAndStage(taskId, DownloadState.DOWNLOADING.name, DownloadStage.DOWNLOADING.name, null)
                val partFile = storageManager.getPartFile(taskId)

                val onProgressCallback: (Long, Long?, Int, DownloadStage?) -> Unit = { bytes, total, percent, stage ->
                    progressChannel.trySend(ProgressEvent(bytes, total, percent, stage))
                }

                val result = if (task.formatSelector != null) {
                    val ytDlpDownloader = YtDlpDownloader(appContext)
                    ytDlpDownloader.download(
                        url = task.sourceUrl,
                        formatSelector = task.formatSelector,
                        destinationFile = partFile,
                        taskId = taskId,
                        onProgress = onProgressCallback
                    )
                } else {
                    val httpDownloader = HttpDownloader()
                    httpDownloader.download(
                        url = task.sourceUrl,
                        destinationFile = partFile,
                        headers = task.toDomain().headers,
                        onProgress = { bytes, total, percent, stage ->
                            onProgressCallback(bytes, total, percent, stage)
                        }
                    )
                }

                progressChannel.close()
                progressJob.join()

                // Cancellation check before finalizing
                val checkCancel = taskDao.getEntityById(taskId)
                if (checkCancel?.status == DownloadState.CANCELLED.name) {
                    storageManager.deletePartFile(taskId)
                    return Result.success()
                }

                when (result) {
                    is DownloadResult.Success -> {
                        val mediaType = try { MediaType.valueOf(task.mediaType) } catch (_: Exception) { MediaType.OTHER }

                        // Validate downloaded file integrity and media tracks
                        val expectedHasAudio = mediaType == MediaType.VIDEO && (task.formatSelector?.contains("bestaudio") == true)
                        val validation = MediaValidator.validate(
                            file = partFile,
                            expectedType = mediaType,
                            expectedHasAudio = expectedHasAudio
                        )
                        if (validation is ValidationResult.Invalid) {
                            storageManager.deletePartFile(taskId)
                            taskDao.updateError(
                                id = taskId,
                                status = DownloadState.FAILED.name,
                                errorCode = validation.failureCode.name,
                                errorMessage = validation.reason,
                                retryCount = task.retryCount
                            )
                            notificationManager.cancel(notificationId)
                            return Result.failure()
                        }

                        // 5. Audio Metadata Tagging stage
                        if (mediaType == MediaType.AUDIO && (!task.artist.isNullOrBlank() || !task.title.isNullOrBlank())) {
                            taskDao.updateStatusAndStage(taskId, DownloadState.PROCESSING.name, DownloadStage.TAGGING_METADATA.name, null)
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

                        // 6. Finalizing & Storage Deduplication
                        taskDao.updateStatusAndStage(taskId, DownloadState.PROCESSING.name, DownloadStage.FINALIZING.name, null)

                        val checksum = com.laterbox.app.media.data.storage.ChecksumUtil.calculateSha256(partFile)
                        val existingAsset = assetDao.findByChecksum(checksum)
                        val duplicatePath = if (existingAsset != null && storageManager.resolveFile(existingAsset.localPath).exists()) {
                            existingAsset.localPath
                        } else null

                        val cleanSuggestedExt = when (mediaType) {
                            MediaType.AUDIO -> "m4a"
                            MediaType.VIDEO -> "mp4"
                            MediaType.IMAGE -> "jpg"
                            else -> null
                        }

                        val finalized = storageManager.finalizeDownload(
                            taskId = taskId,
                            mediaType = mediaType,
                            suggestedExt = cleanSuggestedExt,
                            existingDuplicatePath = duplicatePath
                        )

                        val finalMediaFile = storageManager.resolveFile(finalized.relativePath)
                        val assetId = UUID.randomUUID().toString()

                        // 7. Thumbnail Generation stage
                        taskDao.updateStage(taskId, DownloadStage.GENERATING_THUMBNAIL.name, null)
                        val thumbPath = ThumbnailGenerator.generateThumbnail(
                            context = appContext,
                            mediaFile = finalMediaFile,
                            mediaType = mediaType,
                            assetId = assetId,
                            artworkUrl = task.artworkUrl
                        )

                        // 8. Saving to Library
                        taskDao.updateStage(taskId, DownloadStage.SAVING_TO_LIBRARY.name, null)

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
                            versionTag = task.versionTag,
                            thumbnailPath = thumbPath
                        )

                        assetDao.insertAsset(assetEntity)
                        taskDao.updateStatusAndStage(taskId, DownloadState.COMPLETED.name, DownloadStage.COMPLETED.name, null)
                        taskDao.markCompleted(taskId, assetId)
                        notificationManager.cancel(notificationId)
                        Result.success()
                    }
                    is DownloadResult.Failure -> {
                        val current = taskDao.getEntityById(taskId)
                        if (current?.status == DownloadState.CANCELLED.name) {
                            storageManager.deletePartFile(taskId)
                            return Result.success()
                        }

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
            progressChannel.close()
            progressJob.cancel()
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
            progressChannel.close()
            progressJob.cancel()
            val currentTask = taskDao.getEntityById(taskId)
            if (currentTask?.status != DownloadState.CANCELLED.name) {
                val newRetry = task.retryCount + 1
                taskDao.updateError(
                    id = taskId,
                    status = if (newRetry <= 3) DownloadState.QUEUED.name else DownloadState.FAILED.name,
                    errorCode = FailureCode.UNKNOWN.name,
                    errorMessage = e.localizedMessage ?: "Unknown error",
                    retryCount = newRetry
                )
                if (newRetry <= 3) Result.retry() else Result.failure()
            } else {
                Result.success()
            }
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
        taskId: String,
        stageLabel: String? = null
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

        val isIndeterminate = totalBytes == null || totalBytes <= 0L || progress <= 0
        val subtext = if (!stageLabel.isNullOrBlank()) "$stageLabel ($progress%)" else title
        return NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle("Laterbox Download")
            .setContentText(subtext)
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
