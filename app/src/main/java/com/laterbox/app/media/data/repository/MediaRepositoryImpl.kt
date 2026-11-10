package com.laterbox.app.media.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.laterbox.app.media.data.db.dao.DownloadedAssetDao
import com.laterbox.app.media.data.db.dao.DownloadTaskDao
import com.laterbox.app.media.data.db.entity.DownloadTaskEntity
import com.laterbox.app.media.data.download.DownloadWorker
import com.laterbox.app.media.data.storage.MediaStorageManager
import com.laterbox.app.media.data.storage.MediaStoreExporter
import com.laterbox.app.media.domain.model.AcquisitionSource
import com.laterbox.app.media.domain.model.AssetFileStatus
import com.laterbox.app.media.domain.model.DownloadState
import com.laterbox.app.media.domain.model.DownloadTask
import com.laterbox.app.media.domain.model.MediaAsset
import com.laterbox.app.media.domain.model.MediaCandidate
import com.laterbox.app.media.domain.model.MediaType
import com.laterbox.app.media.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class MediaRepositoryImpl(
    private val context: Context,
    private val assetDao: DownloadedAssetDao,
    private val taskDao: DownloadTaskDao,
    private val storageManager: MediaStorageManager = MediaStorageManager(context)
) : MediaRepository {

    override fun getAssetsForLink(linkId: Long): Flow<List<MediaAsset>> {
        return assetDao.getAssetsForLink(linkId).map { list -> list.map { it.toDomain() } }
    }

    override fun getAllAssets(): Flow<List<MediaAsset>> {
        return assetDao.getAllAssets().map { list -> list.map { it.toDomain() } }
    }

    override fun filterAssets(mediaType: MediaType?, query: String): Flow<List<MediaAsset>> {
        return assetDao.filterAssets(mediaType?.name, query.trim()).map { list -> list.map { it.toDomain() } }
    }

    override fun getAssetById(id: String): Flow<MediaAsset?> {
        return assetDao.getAssetById(id).map { it?.toDomain() }
    }

    override fun getTasksForLink(linkId: Long): Flow<List<DownloadTask>> {
        return taskDao.getTasksForLink(linkId).map { list -> list.map { it.toDomain() } }
    }

    override fun getQueueTasks(): Flow<List<DownloadTask>> {
        return taskDao.getQueueTasks().map { list -> list.map { it.toDomain() } }
    }

    override fun getTaskById(taskId: String): Flow<DownloadTask?> {
        return taskDao.getTaskById(taskId).map { it?.toDomain() }
    }

    override fun getStorageBreakdown(): Flow<Map<MediaType, Long>> {
        return assetDao.getStorageBreakdown().map { list ->
            list.associate { tuple ->
                val type = try { MediaType.valueOf(tuple.mediaType) } catch (_: Exception) { MediaType.OTHER }
                type to tuple.totalBytes
            }
        }
    }

    override fun getAssetCount(): Flow<Int> {
        return assetDao.getAssetCount()
    }

    override suspend fun enqueueDownload(
        linkId: Long,
        candidate: MediaCandidate,
        sourcePlatform: String,
        title: String?
    ): String = withContext(Dispatchers.IO) {
        val taskId = UUID.randomUUID().toString()
        val sourceUrl = when (val s = candidate.source) {
            is AcquisitionSource.DirectHttp -> s.url
            is AcquisitionSource.YtDlp -> s.url
        }
        val formatSelector = when (val s = candidate.source) {
            is AcquisitionSource.DirectHttp -> null
            is AcquisitionSource.YtDlp -> s.formatSelector
        }

        val taskEntity = DownloadTaskEntity(
            id = taskId,
            savedLinkId = linkId,
            sourceUrl = sourceUrl,
            sourcePlatform = sourcePlatform,
            candidateId = candidate.candidateId,
            formatSelector = formatSelector,
            mediaType = candidate.mediaType.name,
            qualityLabel = candidate.formatLabel,
            title = title,
            assetId = null,
            status = DownloadState.QUEUED.name,
            progress = 0,
            downloadedBytes = 0L,
            totalBytes = candidate.estimatedBytes,
            allowCellularOverride = false,
            supportsRange = true,
            retryCount = 0,
            errorCode = null,
            errorMessage = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        taskDao.insertTask(taskEntity)
        enqueueWorkManagerTask(taskId, allowCellular = false)
        taskId
    }

    override suspend fun overrideCellularRequirement(taskId: String): Unit = withContext(Dispatchers.IO) {
        taskDao.updateCellularOverride(taskId, allow = true)
        taskDao.updateStatus(taskId, DownloadState.QUEUED.name)
        enqueueWorkManagerTask(taskId, allowCellular = true)
    }

    override suspend fun pauseDownload(taskId: String): Unit = withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).cancelUniqueWork(DownloadWorker.uniqueWorkName(taskId))
        taskDao.updateStatus(taskId, DownloadState.PAUSED.name)
    }

    override suspend fun resumeDownload(taskId: String): Unit = withContext(Dispatchers.IO) {
        val task = taskDao.getEntityById(taskId) ?: return@withContext
        taskDao.updateStatus(taskId, DownloadState.QUEUED.name)
        enqueueWorkManagerTask(taskId, allowCellular = task.allowCellularOverride)
    }

    override suspend fun cancelDownload(taskId: String): Unit = withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).cancelUniqueWork(DownloadWorker.uniqueWorkName(taskId))
        taskDao.updateStatus(taskId, DownloadState.CANCELLED.name)
        storageManager.deletePartFile(taskId)
    }

    override suspend fun retryDownload(taskId: String): Unit = withContext(Dispatchers.IO) {
        val task = taskDao.getEntityById(taskId) ?: return@withContext
        taskDao.updateError(taskId, DownloadState.QUEUED.name, null, null, 0)
        enqueueWorkManagerTask(taskId, allowCellular = task.allowCellularOverride)
    }

    override suspend fun deleteAsset(assetId: String): Unit = withContext(Dispatchers.IO) {
        val asset = assetDao.getEntityById(assetId) ?: return@withContext
        val refCount = assetDao.countByLocalPath(asset.localPath)
        assetDao.deleteAssetById(assetId)
        storageManager.deletePhysicalFileIfUnreferenced(asset.localPath, refCount)
    }

    override suspend fun deleteAssetsForLink(linkId: Long, deleteFiles: Boolean): Unit = withContext(Dispatchers.IO) {
        val assets = assetDao.getAssetsListForLink(linkId)
        if (deleteFiles) {
            for (asset in assets) {
                val refCount = assetDao.countByLocalPath(asset.localPath)
                assetDao.deleteAssetById(asset.id)
                storageManager.deletePhysicalFileIfUnreferenced(asset.localPath, refCount)
            }
        }
        taskDao.deleteTasksForLink(linkId)
    }

    override suspend fun runOrphanSweep(): Unit = withContext(Dispatchers.IO) {
        val activeIds = taskDao.getActiveAndPausedTaskIds().toSet()
        storageManager.sweepOrphanPartFiles(activeIds)

        val allAssets = assetDao.getAllAssets().first()
        for (asset in allAssets) {
            val file = storageManager.resolveFile(asset.localPath)
            if (!file.exists()) {
                assetDao.updateFileStatus(asset.id, AssetFileStatus.MISSING.name)
            }
        }
    }

    override suspend fun recoverInterruptedTasks(): Unit = withContext(Dispatchers.IO) {
        taskDao.resetInterruptedTasksToQueued()
        val queued = taskDao.getQueuedTasks()
        for (task in queued) {
            enqueueWorkManagerTask(task.id, allowCellular = task.allowCellularOverride)
        }
    }

    private val mediaStoreExporter = MediaStoreExporter(context, storageManager)

    override suspend fun exportAsset(assetId: String): Result<String> = withContext(Dispatchers.IO) {
        val assetEntity = assetDao.getEntityById(assetId)
            ?: return@withContext Result.failure(IllegalArgumentException("Asset $assetId not found"))
        val asset = assetEntity.toDomain()
        val result = mediaStoreExporter.exportAsset(asset)
        if (result.isSuccess) {
            val uri = result.getOrThrow()
            assetDao.updateExportedUri(assetId, uri.toString())
            Result.success(uri.toString())
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Export failed"))
        }
    }

    private fun enqueueWorkManagerTask(taskId: String, allowCellular: Boolean) {
        val networkType = if (allowCellular) NetworkType.CONNECTED else NetworkType.NOT_ROAMING
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(DownloadWorker.KEY_TASK_ID to taskId))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            DownloadWorker.uniqueWorkName(taskId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
