package com.laterbox.app.media.domain.repository

import com.laterbox.app.media.domain.model.DownloadTask
import com.laterbox.app.media.domain.model.MediaAsset
import com.laterbox.app.media.domain.model.MediaCandidate
import com.laterbox.app.media.domain.model.MediaType
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun getAssetsForLink(linkId: Long): Flow<List<MediaAsset>>
    fun getAllAssets(): Flow<List<MediaAsset>>
    fun filterAssets(mediaType: MediaType?, query: String): Flow<List<MediaAsset>>
    fun getAssetById(id: String): Flow<MediaAsset?>

    fun getTasksForLink(linkId: Long): Flow<List<DownloadTask>>
    fun getQueueTasks(): Flow<List<DownloadTask>>
    fun getTaskById(taskId: String): Flow<DownloadTask?>

    fun getStorageBreakdown(): Flow<Map<MediaType, Long>>
    fun getAssetCount(): Flow<Int>

    suspend fun enqueueDownload(
        linkId: Long,
        candidate: MediaCandidate,
        sourcePlatform: String,
        title: String?
    ): String

    suspend fun overrideCellularRequirement(taskId: String)
    suspend fun pauseDownload(taskId: String)
    suspend fun resumeDownload(taskId: String)
    suspend fun cancelDownload(taskId: String)
    suspend fun retryDownload(taskId: String)
    suspend fun deleteAsset(assetId: String)
    suspend fun exportAsset(assetId: String): Result<String>
    suspend fun deleteAssetsForLink(linkId: Long, deleteFiles: Boolean)
    suspend fun runOrphanSweep()
    suspend fun recoverInterruptedTasks()
}
