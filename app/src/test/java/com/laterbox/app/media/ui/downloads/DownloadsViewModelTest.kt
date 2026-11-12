package com.laterbox.app.media.ui.downloads

import app.cash.turbine.test
import com.laterbox.app.media.data.adapters.InspectionResultJsonAdapter
import com.laterbox.app.media.data.db.dao.MediaInspectionDao
import com.laterbox.app.media.data.db.entity.MediaInspectionEntity
import com.laterbox.app.media.domain.model.AcquisitionSource
import com.laterbox.app.media.domain.model.CandidateStatus
import com.laterbox.app.media.domain.model.DownloadTask
import com.laterbox.app.media.domain.model.InspectionResult
import com.laterbox.app.media.domain.model.MediaAsset
import com.laterbox.app.media.domain.model.MediaCandidate
import com.laterbox.app.media.domain.model.MediaType
import com.laterbox.app.media.domain.repository.MediaRepository
import com.laterbox.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeMediaInspectionDao(
        val completedInspectionsFlow: MutableStateFlow<List<MediaInspectionEntity>> = MutableStateFlow(emptyList())
    ) : MediaInspectionDao {
        override suspend fun insert(entity: MediaInspectionEntity) {}
        override suspend fun update(entity: MediaInspectionEntity) {}
        override fun getInspectionForLink(savedLinkId: Long): Flow<MediaInspectionEntity?> = flowOf(null)
        override suspend fun getEntityForLink(savedLinkId: Long): MediaInspectionEntity? = null
        override suspend fun getEntityById(id: String): MediaInspectionEntity? = null
        override suspend fun getStaleInspections(staleThreshold: Long): List<MediaInspectionEntity> = emptyList()
        override fun getCompletedInspectionLinkIds(): Flow<List<Long>> = flowOf(emptyList())
        override fun getCompletedInspections(): Flow<List<MediaInspectionEntity>> = completedInspectionsFlow
        override suspend fun deleteForLink(savedLinkId: Long) {}
        override suspend fun deleteById(id: String) {}
    }

    private class FakeMediaRepository(
        val assetsFlow: MutableStateFlow<List<MediaAsset>> = MutableStateFlow(emptyList())
    ) : MediaRepository {
        override fun getAssetsForLink(linkId: Long): Flow<List<MediaAsset>> = flowOf(emptyList())
        override fun getAllAssets(): Flow<List<MediaAsset>> = assetsFlow
        override fun filterAssets(mediaType: MediaType?, query: String): Flow<List<MediaAsset>> = assetsFlow
        override fun getAssetById(id: String): Flow<MediaAsset?> = flowOf(null)
        override fun getTasksForLink(linkId: Long): Flow<List<DownloadTask>> = flowOf(emptyList())
        override fun getQueueTasks(): Flow<List<DownloadTask>> = flowOf(emptyList())
        override fun getTaskById(taskId: String): Flow<DownloadTask?> = flowOf(null)
        override fun getStorageBreakdown(): Flow<Map<MediaType, Long>> = flowOf(emptyMap())
        override fun getAssetCount(): Flow<Int> = flowOf(0)
        override suspend fun enqueueDownload(linkId: Long, candidate: MediaCandidate, sourcePlatform: String, title: String?, artist: String?, album: String?, artworkUrl: String?, spotifyTrackId: String?, versionTag: String?): String = "task_1"
        override suspend fun overrideCellularRequirement(taskId: String) {}
        override suspend fun pauseDownload(taskId: String) {}
        override suspend fun resumeDownload(taskId: String) {}
        override suspend fun cancelDownload(taskId: String) {}
        override suspend fun dismissTask(taskId: String) {}
        override suspend fun clearTerminalTasks() {}
        override suspend fun retryDownload(taskId: String) {}
        override suspend fun deleteAsset(assetId: String) {}
        override suspend fun exportAsset(assetId: String): Result<String> = Result.success("path")
        override suspend fun deleteAssetsForLink(linkId: Long, deleteFiles: Boolean) {}
        override suspend fun runOrphanSweep() {}
        override suspend fun recoverInterruptedTasks() {}
        override fun getInspectionForLink(linkId: Long): Flow<InspectionResult?> = flowOf(null)
        override fun isInspectingLink(linkId: Long): Flow<Boolean> = flowOf(false)
        override fun startInspection(linkId: Long, url: String, previewImageUrl: String?, forceRefresh: Boolean) {}
        override suspend fun cancelInspection(linkId: Long) {}
    }

    @Test
    fun uiState_populatesReadyToDownloadItems_andFiltersAlreadyDownloaded() = runTest {
        val inspectionDao = FakeMediaInspectionDao()
        val mediaRepository = FakeMediaRepository()

        val candidate = MediaCandidate(
            candidateId = "c1",
            mediaType = MediaType.VIDEO,
            formatLabel = "1080p MP4",
            estimatedBytes = 50000000L,
            durationMs = 120000L,
            width = 1920,
            height = 1080,
            source = AcquisitionSource.DirectHttp("https://video.mp4"),
            status = CandidateStatus.AVAILABLE
        )
        val availableResult = InspectionResult.Available(
            platform = "YouTube",
            title = "A Great Architecture Video",
            thumbnailUrl = "https://example.com/thumb.jpg",
            candidates = listOf(candidate)
        )
        val json = InspectionResultJsonAdapter.toJson(availableResult)

        val completedInspection = MediaInspectionEntity(
            id = "insp_1",
            savedLinkId = 42L,
            url = "https://youtube.com/watch?v=123",
            status = "COMPLETED",
            resultJson = json,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val viewModel = DownloadsViewModel(mediaRepository, inspectionDao)

        viewModel.uiState.test {
            val initial = awaitItem()
            assertTrue(initial.readyToDownloadItems.isEmpty())

            // Emit completed inspection
            inspectionDao.completedInspectionsFlow.value = listOf(completedInspection)

            val updated = awaitItem()
            assertEquals(1, updated.readyToDownloadItems.size)
            val item = updated.readyToDownloadItems.first()
            assertEquals(42L, item.savedLinkId)
            assertEquals("A Great Architecture Video", item.title)
            assertEquals(1, item.candidateCount)

            // Simulate asset already downloaded for savedLinkId 42
            val downloadedAsset = MediaAsset(
                id = "asset_42",
                savedLinkId = 42L,
                sourcePlatform = "YouTube",
                originalSourceUrl = "https://youtube.com/watch?v=123",
                title = "A Great Architecture Video",
                mimeType = "video/mp4",
                mediaType = MediaType.VIDEO,
                localPath = "video_42.mp4",
                exportedUri = null,
                fileSize = 50000000L,
                durationMs = 120000L,
                width = 1920,
                height = 1080,
                qualityLabel = "1080p",
                checksum = "abc",
                createdAt = System.currentTimeMillis()
            )
            mediaRepository.assetsFlow.value = listOf(downloadedAsset)

            val filtered = awaitItem()
            assertTrue("Already downloaded asset should be filtered out from readyToDownloadItems", filtered.readyToDownloadItems.isEmpty())
        }
    }

    @Test
    fun assetMultiSelection_toggle_selectAll_and_clear() = runTest {
        val inspectionDao = FakeMediaInspectionDao()
        val mediaRepository = FakeMediaRepository()
        val viewModel = DownloadsViewModel(mediaRepository, inspectionDao)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val asset1 = MediaAsset(
            id = "asset_1",
            savedLinkId = 1L,
            sourcePlatform = "YouTube",
            originalSourceUrl = "https://youtube.com/watch?v=1",
            title = "Video 1",
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO,
            localPath = "video_1.mp4",
            exportedUri = null,
            fileSize = 1000L,
            durationMs = 10000L,
            width = 1280,
            height = 720,
            qualityLabel = "720p",
            checksum = "a1",
            createdAt = System.currentTimeMillis()
        )
        val asset2 = asset1.copy(id = "asset_2", savedLinkId = 2L, title = "Video 2")
        val assets = listOf(asset1, asset2)

        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertEquals(0, viewModel.uiState.value.selectedAssetIds.size)

        viewModel.onToggleAssetSelection("asset_1")
        assertTrue(viewModel.uiState.value.isSelectionMode)
        assertEquals(setOf("asset_1"), viewModel.uiState.value.selectedAssetIds)

        viewModel.onSelectAllAssets(assets)
        assertEquals(setOf("asset_1", "asset_2"), viewModel.uiState.value.selectedAssetIds)

        viewModel.onClearSelection()
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertTrue(viewModel.uiState.value.selectedAssetIds.isEmpty())
    }

    @Test
    fun queueTaskMultiSelection_toggle_selectAll_and_actions() = runTest {
        val inspectionDao = FakeMediaInspectionDao()
        val mediaRepository = FakeMediaRepository()
        val viewModel = DownloadsViewModel(mediaRepository, inspectionDao)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val task1 = DownloadTask(
            id = "task_1",
            savedLinkId = 1L,
            sourceUrl = "https://youtube.com/watch?v=1",
            sourcePlatform = "YouTube",
            candidateId = "c1",
            formatSelector = "best",
            mediaType = MediaType.VIDEO,
            qualityLabel = "1080p",
            title = "Task 1",
            assetId = null,
            status = com.laterbox.app.media.domain.model.DownloadState.FAILED,
            progress = 0,
            downloadedBytes = 0L,
            totalBytes = 1000L,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val task2 = task1.copy(id = "task_2", title = "Task 2")
        val tasks = listOf(task1, task2)

        assertFalse(viewModel.uiState.value.isQueueSelectionMode)
        assertEquals(0, viewModel.uiState.value.selectedTaskIds.size)

        viewModel.onToggleTaskSelection("task_1")
        assertTrue(viewModel.uiState.value.isQueueSelectionMode)
        assertEquals(setOf("task_1"), viewModel.uiState.value.selectedTaskIds)

        viewModel.onSelectAllTasks(tasks)
        assertEquals(setOf("task_1", "task_2"), viewModel.uiState.value.selectedTaskIds)

        viewModel.onRetrySelectedTasks()
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isQueueSelectionMode)
        assertTrue(viewModel.uiState.value.selectedTaskIds.isEmpty())
    }
}
