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
import com.laterbox.app.data.local.entity.SavedLinkEntity
import com.laterbox.app.domain.model.MetadataStatus
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class MetadataSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? LaterboxApplication ?: return Result.failure()
        val dao = app.container.database.savedLinkDao()

        val allInbox = dao.getInboxLinks().first()
        val allArchived = dao.getArchivedLinks().first()
        val candidates = (allInbox + allArchived).filter {
            it.link.metadataStatus == MetadataStatus.PENDING ||
                (it.link.metadataStatus == MetadataStatus.FETCHING && System.currentTimeMillis() - it.link.updatedAt > 10_000)
        }

        if (candidates.isEmpty()) {
            return Result.success()
        }

        var anyFailed = false

        for (item in candidates) {
            val link = item.link
            dao.updateMetadataStatus(link.id, MetadataStatus.FETCHING)

            val extractionResult = JsoupMetadataExtractor.extract(link.originalUrl)
            if (extractionResult.isSuccess) {
                val metadata = extractionResult.getOrThrow()
                val resolvedTitle = if (link.title == link.domain && !metadata.title.isNullOrBlank()) {
                    metadata.title
                } else {
                    link.title
                }

                val finalStatus = if (metadata.previewImageUrl != null || metadata.description != null) {
                    MetadataStatus.SUCCESS
                } else {
                    MetadataStatus.PARTIAL
                }

                val updatedEntity = link.copy(
                    title = resolvedTitle,
                    description = metadata.description ?: link.description,
                    siteName = metadata.siteName ?: link.siteName,
                    faviconUrl = metadata.faviconUrl ?: link.faviconUrl,
                    previewImageUrl = metadata.previewImageUrl ?: link.previewImageUrl,
                    contentType = metadata.contentType,
                    metadataStatus = finalStatus,
                    lastFetchedAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                dao.updateLink(updatedEntity)
            } else {
                anyFailed = true
                val newRetryCount = link.retryCount + 1
                val newStatus = if (newRetryCount >= 3) {
                    MetadataStatus.FAILED
                } else {
                    MetadataStatus.PENDING
                }
                val failedEntity = link.copy(
                    retryCount = newRetryCount,
                    metadataStatus = newStatus,
                    updatedAt = System.currentTimeMillis()
                )
                dao.updateLink(failedEntity)
            }
        }

        return if (anyFailed) Result.retry() else Result.success()
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
                    ExistingWorkPolicy.KEEP,
                    workRequest
                )
            } catch (e: Exception) {
                // Safeguard against uninitialized WorkManager in Robolectric testing environments
            }
        }
    }
}
