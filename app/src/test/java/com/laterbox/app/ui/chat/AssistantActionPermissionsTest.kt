package com.laterbox.app.ui.chat

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.laterbox.app.ai.ChatAction
import com.laterbox.app.ai.LaterboxContextEngine
import com.laterbox.app.data.local.LaterboxDatabase
import com.laterbox.app.data.local.entity.SavedLinkEntity
import com.laterbox.app.data.preferences.UserPreferencesRepository
import com.laterbox.app.data.repository.ActivityEventRepository
import com.laterbox.app.data.repository.ChatRepository
import com.laterbox.app.data.repository.LinkRepository
import com.laterbox.app.diagnostics.AppHealthChecker
import com.laterbox.app.media.domain.model.DownloadState
import com.laterbox.app.media.domain.model.DownloadTask
import com.laterbox.app.media.domain.model.InspectionResult
import com.laterbox.app.media.domain.model.MediaAsset
import com.laterbox.app.media.domain.model.MediaCandidate
import com.laterbox.app.media.domain.model.MediaType
import com.laterbox.app.media.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AssistantActionPermissionsTest {

    private val mainDispatcher = StandardTestDispatcher()
    private lateinit var db: LaterboxDatabase
    private lateinit var chatRepo: ChatRepository
    private lateinit var linkRepo: LinkRepository
    private lateinit var mediaRepo: RecordingFakeMediaRepository
    private lateinit var vm: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, LaterboxDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        chatRepo = ChatRepository(db.chatDao())
        linkRepo = LinkRepository(db.savedLinkDao(), ctx)
        mediaRepo = RecordingFakeMediaRepository()
        val prefs = UserPreferencesRepository(ctx)
        val activityRepo = ActivityEventRepository(db.activityEventDao())
        vm = ChatViewModel(
            chatRepository = chatRepo,
            userPreferencesRepository = prefs,
            laterboxContextEngine = LaterboxContextEngine(db, activityRepo, prefs),
            appHealthChecker = AppHealthChecker(ctx, db, com.laterbox.app.media.data.adapters.ytdlp.YtDlpRuntimeManager(ctx), prefs),
            linkRepository = linkRepo,
            mediaRepository = mediaRepo
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun allowOnce_executesActionWithReceipt_andDoesNotPersistGrant() = runTest(mainDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val linkId = db.savedLinkDao().insertLink(
            SavedLinkEntity(originalUrl = "https://example.com/v1", normalizedUrl = "https://example.com/v1", title = "Talk", domain = "example.com")
        )
        val convId = chatRepo.createConversation("Check")
        vm.selectConversation(convId)
        advanceUntilIdle()

        val action = ChatAction.RetryInspection(linkId, "Talk")
        vm.requestMutatingActionConfirmation(action)
        advanceUntilIdle()
        assertEquals(action, vm.uiState.value.pendingMutatingAction)

        vm.confirmMutatingAction()
        advanceUntilIdle()

        assertNull(vm.uiState.value.pendingMutatingAction)
        assertEquals(listOf(linkId), mediaRepo.inspectedLinkIds)
        assertTrue(chatRepo.getGrantedActionTypes(convId).isEmpty())
        val messages = chatRepo.getMessagesSnapshot(convId)
        assertTrue(messages.last().content.contains("Started a fresh media inspection"))

        // Second request in same conversation must ask again
        vm.requestMutatingActionConfirmation(action)
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.pendingMutatingAction)
    }

    @Test
    fun alwaysAllow_isScopedToConversationAndActionType_andCascadeDeletes() = runTest(mainDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val linkId = db.savedLinkDao().insertLink(
            SavedLinkEntity(originalUrl = "https://example.com/v2", normalizedUrl = "https://example.com/v2", title = "Demo", domain = "example.com")
        )
        mediaRepo.taskFlow.value = DownloadTask(
            id = "task-1", savedLinkId = linkId, sourceUrl = "https://example.com/v2",
            sourcePlatform = "Direct", candidateId = "c1", formatSelector = null,
            mediaType = MediaType.VIDEO, qualityLabel = "720p", title = "Demo Video",
            assetId = null, status = DownloadState.FAILED, progress = 0,
            downloadedBytes = 0, totalBytes = null, createdAt = 1L, updatedAt = 1L
        )
        val convA = chatRepo.createConversation("Conv A")
        val convB = chatRepo.createConversation("Conv B")

        vm.selectConversation(convA)
        advanceUntilIdle()
        vm.requestMutatingActionConfirmation(ChatAction.RetryInspection(linkId, "Demo"))
        advanceUntilIdle()
        vm.alwaysAllowMutatingActionInConversation()
        advanceUntilIdle()

        assertEquals(1, mediaRepo.inspectedLinkIds.size)
        assertEquals(listOf("RETRY_INSPECTION"), chatRepo.getGrantedActionTypes(convA))

        // Repeat RetryInspection in Conv A runs automatically without prompt
        vm.requestMutatingActionConfirmation(ChatAction.RetryInspection(linkId, "Demo"))
        advanceUntilIdle()
        assertNull(vm.uiState.value.pendingMutatingAction)
        assertEquals(2, mediaRepo.inspectedLinkIds.size)

        // Different action type in Conv A still prompts
        vm.requestMutatingActionConfirmation(ChatAction.RetryDownload("task-1", "Demo Video"))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.pendingMutatingAction is ChatAction.RetryDownload)
        vm.dismissMutatingAction()
        advanceUntilIdle()
        assertTrue(chatRepo.getMessagesSnapshot(convA).last().content.contains("Nothing was changed"))

        // Same action in Conv B still prompts
        vm.selectConversation(convB)
        advanceUntilIdle()
        vm.requestMutatingActionConfirmation(ChatAction.RetryInspection(linkId, "Demo"))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.pendingMutatingAction is ChatAction.RetryInspection)

        // Deleting Conv A cascades permission rows
        chatRepo.deleteConversation(convA)
        advanceUntilIdle()
        assertTrue(db.chatDao().getGrantedActionTypes(convA).isEmpty())
    }

    @Test
    fun invalidTarget_andDuplicateExecution_areSafelyRejected() = runTest(mainDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val convId = chatRepo.createConversation("Errors")
        vm.selectConversation(convId)
        advanceUntilIdle()

        // Missing link ID must fail validation without prompting or executing
        vm.requestMutatingActionConfirmation(ChatAction.RetryInspection(9999L, "Ghost"))
        advanceUntilIdle()
        assertNull(vm.uiState.value.pendingMutatingAction)
        assertTrue(mediaRepo.inspectedLinkIds.isEmpty())
        assertTrue(chatRepo.getMessagesSnapshot(convId).last().content.contains("no longer exists"))

        // Concurrent duplicate retry download prevention
        val linkId = db.savedLinkDao().insertLink(
            SavedLinkEntity(originalUrl = "https://example.com/v3", normalizedUrl = "https://example.com/v3", title = "V3", domain = "example.com")
        )
        mediaRepo.taskFlow.value = DownloadTask(
            id = "task-dup", savedLinkId = linkId, sourceUrl = "https://example.com/v3",
            sourcePlatform = "Direct", candidateId = "c1", formatSelector = null,
            mediaType = MediaType.VIDEO, qualityLabel = "720p", title = "V3",
            assetId = null, status = DownloadState.FAILED, progress = 0,
            downloadedBytes = 0, totalBytes = null, createdAt = 1L, updatedAt = 1L
        )
        mediaRepo.retryDelayMs = 200L
        vm.requestMutatingActionConfirmation(ChatAction.RetryDownload("task-dup", "V3"))
        advanceUntilIdle()
        vm.alwaysAllowMutatingActionInConversation()
        // Trigger a second duplicate while first is still suspending
        vm.requestMutatingActionConfirmation(ChatAction.RetryDownload("task-dup", "V3"))
        advanceUntilIdle()
        assertEquals(1, mediaRepo.retriedTaskIds.size)
    }

    private class RecordingFakeMediaRepository : MediaRepository {
        val inspectedLinkIds = mutableListOf<Long>()
        val retriedTaskIds = mutableListOf<String>()
        val taskFlow = MutableStateFlow<DownloadTask?>(null)
        var retryDelayMs: Long = 0L

        override fun getAssetsForLink(linkId: Long): Flow<List<MediaAsset>> = flowOf(emptyList())
        override fun getAllAssets(): Flow<List<MediaAsset>> = flowOf(emptyList())
        override fun filterAssets(mediaType: MediaType?, query: String): Flow<List<MediaAsset>> = flowOf(emptyList())
        override fun getAssetById(id: String): Flow<MediaAsset?> = flowOf(null)
        override fun getTasksForLink(linkId: Long): Flow<List<DownloadTask>> = flowOf(emptyList())
        override fun getQueueTasks(): Flow<List<DownloadTask>> = flowOf(emptyList())
        override fun getTaskById(taskId: String): Flow<DownloadTask?> = taskFlow
        override fun getStorageBreakdown(): Flow<Map<MediaType, Long>> = flowOf(emptyMap())
        override fun getAssetCount(): Flow<Int> = flowOf(0)
        override suspend fun enqueueDownload(
            linkId: Long, candidate: MediaCandidate, sourcePlatform: String, title: String?,
            artist: String?, album: String?, artworkUrl: String?, spotifyTrackId: String?, versionTag: String?
        ) = "id"
        override suspend fun overrideCellularRequirement(taskId: String) {}
        override suspend fun pauseDownload(taskId: String) {}
        override suspend fun resumeDownload(taskId: String) {}
        override suspend fun cancelDownload(taskId: String) {}
        override suspend fun dismissTask(taskId: String) {}
        override suspend fun clearTerminalTasks() {}
        override suspend fun retryDownload(taskId: String) {
            if (retryDelayMs > 0) delay(retryDelayMs)
            retriedTaskIds += taskId
            taskFlow.value = taskFlow.value?.copy(status = DownloadState.QUEUED)
        }
        override suspend fun deleteAsset(assetId: String) {}
        override suspend fun exportAsset(assetId: String) = Result.success("")
        override suspend fun deleteAssetsForLink(linkId: Long, deleteFiles: Boolean) {}
        override suspend fun runOrphanSweep() {}
        override suspend fun recoverInterruptedTasks() {}
        override fun getInspectionForLink(linkId: Long): Flow<InspectionResult?> = flowOf(null)
        override fun isInspectingLink(linkId: Long): Flow<Boolean> = flowOf(false)
        override fun startInspection(linkId: Long, url: String, previewImageUrl: String?, forceRefresh: Boolean) {
            inspectedLinkIds += linkId
        }
        override suspend fun cancelInspection(linkId: Long) {}
    }
}
