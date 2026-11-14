package com.laterbox.app.media.data.inspection

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.laterbox.app.data.local.LaterboxDatabase
import com.laterbox.app.data.local.dao.SavedLinkDao
import com.laterbox.app.data.local.entity.SavedLinkEntity
import com.laterbox.app.media.data.db.dao.MediaInspectionDao
import com.laterbox.app.media.domain.adapter.AdapterRegistry
import com.laterbox.app.media.domain.adapter.MediaPlatformAdapter
import com.laterbox.app.media.domain.adapter.PlatformResolver
import com.laterbox.app.media.domain.model.FailureCode
import com.laterbox.app.media.domain.model.InspectionResult
import com.laterbox.app.media.domain.model.MediaCandidate
import com.laterbox.app.media.domain.model.MediaType
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaInspectionManagerTest {

    private lateinit var context: Context
    private lateinit var db: LaterboxDatabase
    private lateinit var linkDao: SavedLinkDao
    private lateinit var inspectionDao: MediaInspectionDao
    private val testScope = TestScope()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, LaterboxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        linkDao = db.savedLinkDao()
        inspectionDao = db.mediaInspectionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun startInspection_cachesResultInRoomAndReturnsFromCache() = testScope.runTest {
        // Arrange
        val link = SavedLinkEntity(
            originalUrl = "https://example.com/video",
            normalizedUrl = "https://example.com/video",
            title = "Test Video",
            domain = "example.com"
        )
        val linkId = linkDao.insertLink(link)

        var inspectCallCount = 0
        val fakeAdapter = object : MediaPlatformAdapter {
            override val id = "Fake"
            override fun canHandle(url: String) = true
            override suspend fun inspect(url: String): InspectionResult {
                inspectCallCount++
                return InspectionResult.Available(
                    platform = "Fake",
                    title = "Fake Video",
                    thumbnailUrl = null,
                    candidates = listOf(
                        MediaCandidate(
                            candidateId = "c1",
                            mediaType = MediaType.VIDEO,
                            formatLabel = "720p",
                            estimatedBytes = 1000000L,
                            durationMs = 60000L,
                            width = 1280,
                            height = 720,
                            source = com.laterbox.app.media.domain.model.AcquisitionSource.DirectHttp("https://example.com/stream.mp4")
                        )
                    )
                )
            }
        }

        val registry = AdapterRegistry(listOf(fakeAdapter))
        val platformResolver = PlatformResolver(registry)
        val manager = MediaInspectionManager(
            appContext = context,
            inspectionDao = inspectionDao,
            platformResolver = platformResolver,
            scope = this
        )

        // Act 1: First inspection
        val job1 = manager.startInspection(linkId, "https://example.com/video", forceRefresh = false)
        job1.join()

        val result1 = manager.getInspectionResultForLink(linkId).filterNotNull().first()
        assertTrue(result1 is InspectionResult.Available)
        assertEquals(1, inspectCallCount)

        // Verify saved in Room
        val inRoom = inspectionDao.getEntityForLink(linkId)
        assertNotNull(inRoom)
        assertEquals("COMPLETED", inRoom?.status)

        // Act 2: Second inspection without forceRefresh -> should use cache, call count stays 1
        val job2 = manager.startInspection(linkId, "https://example.com/video", forceRefresh = false)
        job2.join()

        assertEquals(1, inspectCallCount)

        // Act 3: Third inspection with forceRefresh = true -> should re-inspect
        val job3 = manager.startInspection(linkId, "https://example.com/video", forceRefresh = true)
        job3.join()

        assertEquals(2, inspectCallCount)
    }

    @Test
    fun startInspection_preservesFailureWhenInspectionFails() = testScope.runTest {
        val link = SavedLinkEntity(
            originalUrl = "https://linkedin.com/posts/xyz",
            normalizedUrl = "https://linkedin.com/posts/xyz",
            title = "LinkedIn Post",
            domain = "linkedin.com",
            previewImageUrl = "https://media.licdn.com/dms/image/preview.jpg"
        )
        val linkId = linkDao.insertLink(link)

        val failingAdapter = object : MediaPlatformAdapter {
            override val id = "LinkedIn"
            override fun canHandle(url: String) = true
            override suspend fun inspect(url: String): InspectionResult {
                return InspectionResult.Failed(
                    code = FailureCode.EXTRACTOR_BROKEN,
                    message = "Unable to extract video",
                    retryable = false
                )
            }
        }

        val registry = AdapterRegistry(listOf(failingAdapter))
        val platformResolver = PlatformResolver(registry)
        val manager = MediaInspectionManager(
            appContext = context,
            inspectionDao = inspectionDao,
            platformResolver = platformResolver,
            scope = this
        )

        val job = manager.startInspection(linkId, "https://linkedin.com/posts/xyz", previewImageUrl = link.previewImageUrl, forceRefresh = false)
        job.join()

        val result = manager.getInspectionResultForLink(linkId).filterNotNull().first()

        // Extraction failure must NOT be masked by OG preview image
        assertTrue(result is InspectionResult.Failed)
        val failed = result as InspectionResult.Failed
        assertEquals(FailureCode.EXTRACTOR_BROKEN, failed.code)
        assertEquals("Unable to extract video", failed.message)

        val entity = inspectionDao.getEntityForLink(linkId)
        assertNotNull(entity)
        assertEquals("FAILED", entity?.status)
        assertEquals(FailureCode.EXTRACTOR_BROKEN.name, entity?.errorCode)
    }

    @Test
    fun cancelInspection_marksInFlightAsCancelled() = testScope.runTest {
        val link = SavedLinkEntity(
            originalUrl = "https://example.com/running",
            normalizedUrl = "https://example.com/running",
            title = "Running Video",
            domain = "example.com"
        )
        val linkId = linkDao.insertLink(link)

        // Insert in-flight inspection entity
        val inFlightEntity = com.laterbox.app.media.data.db.entity.MediaInspectionEntity(
            id = "inspect_$linkId",
            savedLinkId = linkId,
            url = "https://example.com/running",
            status = "INSPECTING",
            resultJson = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        inspectionDao.insert(inFlightEntity)

        val registry = AdapterRegistry(emptyList())
        val platformResolver = PlatformResolver(registry)
        val manager = MediaInspectionManager(
            appContext = context,
            inspectionDao = inspectionDao,
            platformResolver = platformResolver,
            scope = this
        )

        // Act
        manager.cancelInspection(linkId)

        // Assert
        val updated = inspectionDao.getEntityForLink(linkId)
        assertNotNull(updated)
        assertEquals("CANCELLED", updated?.status)
    }

    @Test
    fun scheduleInspection_invalidatesWhenUrlChanges() = testScope.runTest {
        val link = SavedLinkEntity(
            originalUrl = "https://example.com/old_url",
            normalizedUrl = "https://example.com/old_url",
            title = "Old Video",
            domain = "example.com"
        )
        val linkId = linkDao.insertLink(link)

        val oldEntity = com.laterbox.app.media.data.db.entity.MediaInspectionEntity(
            id = "inspect_$linkId",
            savedLinkId = linkId,
            url = "https://example.com/old_url",
            status = "COMPLETED",
            resultJson = "{}",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        inspectionDao.insert(oldEntity)

        val registry = AdapterRegistry(emptyList())
        val platformResolver = PlatformResolver(registry)
        val manager = MediaInspectionManager(
            appContext = context,
            inspectionDao = inspectionDao,
            platformResolver = platformResolver,
            scope = this
        )

        // Act: schedule inspection with a different URL
        val job = manager.scheduleInspection(linkId, "https://example.com/new_url", forceRefresh = false)
        job.join()

        // Old entity should be invalidated (deleted from DB)
        val entity = inspectionDao.getEntityForLink(linkId)
        assertTrue(entity == null || entity.url == "https://example.com/new_url")
    }

    @Test
    fun markInspectionDeferredAndPending_doNotSpinIndefinitelyAndExposeRetryableState() = testScope.runTest {
        val link = SavedLinkEntity(
            originalUrl = "https://example.com/pending_test",
            normalizedUrl = "https://example.com/pending_test",
            title = "Pending Video",
            domain = "example.com"
        )
        val linkId = linkDao.insertLink(link)

        val registry = AdapterRegistry(emptyList())
        val platformResolver = PlatformResolver(registry)
        val manager = MediaInspectionManager(
            appContext = context,
            inspectionDao = inspectionDao,
            platformResolver = platformResolver,
            scope = this
        )

        manager.markInspectionPending(linkId, "https://example.com/pending_test")

        val entity = inspectionDao.getEntityForLink(linkId)
        assertNotNull(entity)
        assertEquals("PENDING", entity?.status)

        val isInspecting = manager.isInspectingFlow(linkId).first()
        org.junit.Assert.assertFalse("Deferred/pending states must not show an infinite spinner", isInspecting)
        manager.markInspectionDeferred(linkId, "https://example.com/pending_test", status = "WAITING_FOR_NETWORK", reason = "Offline")
        org.junit.Assert.assertFalse(manager.isInspectingFlow(linkId).first())
        val mapped = manager.getInspectionResultForLink(linkId).first()
        assertTrue(mapped is InspectionResult.Failed && mapped.retryable)
    }

    @Test
    fun executeInspection_forceRefreshBypassesCompletedCache() = testScope.runTest {
        val link = SavedLinkEntity(
            originalUrl = "https://example.com/refresh_test",
            normalizedUrl = "https://example.com/refresh_test",
            title = "Refresh Video",
            domain = "example.com"
        )
        val linkId = linkDao.insertLink(link)

        var inspectCalls = 0
        val fakeAdapter = object : MediaPlatformAdapter {
            override val id = "Fake"
            override fun canHandle(url: String) = true
            override suspend fun inspect(url: String): InspectionResult {
                inspectCalls++
                return InspectionResult.Available(
                    platform = "Fake",
                    title = "Title $inspectCalls",
                    thumbnailUrl = null,
                    candidates = emptyList()
                )
            }
        }

        val registry = AdapterRegistry(listOf(fakeAdapter))
        val platformResolver = PlatformResolver(registry)
        val manager = MediaInspectionManager(
            appContext = context,
            inspectionDao = inspectionDao,
            platformResolver = platformResolver,
            scope = this
        )

        // 1. Initial execution
        manager.executeInspection(linkId, "https://example.com/refresh_test", forceRefresh = false)
        assertEquals(1, inspectCalls)

        // 2. Normal execution hits cache (calls remain 1)
        manager.executeInspection(linkId, "https://example.com/refresh_test", forceRefresh = false)
        assertEquals(1, inspectCalls)

        // 3. Force refresh bypasses cache (calls increment to 2)
        val refreshResult = manager.executeInspection(linkId, "https://example.com/refresh_test", forceRefresh = true)
        assertEquals(2, inspectCalls)
        assertTrue(refreshResult.isSuccess)
    }

    @Test
    fun retry_cancelsPreviousActiveJob_andSetsInspectingImmediately() = testScope.runTest {
        val link = SavedLinkEntity(
            originalUrl = "https://example.com/slow_video",
            normalizedUrl = "https://example.com/slow_video",
            title = "Slow Video",
            domain = "example.com"
        )
        val linkId = linkDao.insertLink(link)

        val firstStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val firstShouldFinish = kotlinx.coroutines.CompletableDeferred<Unit>()

        val slowAdapter = object : MediaPlatformAdapter {
            override val id = "Slow"
            override fun canHandle(url: String) = true
            override suspend fun inspect(url: String): InspectionResult = inspect(url, null)
            override suspend fun inspect(url: String, processId: String?): InspectionResult {
                if (url.contains("slow")) {
                    firstStarted.complete(Unit)
                    firstShouldFinish.await()
                    return InspectionResult.Available("Slow", "Slow Video Title", null, emptyList())
                }
                return InspectionResult.Available("Fast", "Fast Video Title", null, emptyList())
            }
        }

        val registry = AdapterRegistry(listOf(slowAdapter))
        val platformResolver = PlatformResolver(registry)
        val manager = MediaInspectionManager(
            appContext = context,
            inspectionDao = inspectionDao,
            platformResolver = platformResolver,
            scope = this
        )

        // 1. Start first slow inspection
        val job1 = manager.startInspection(linkId, "https://example.com/slow_video", forceRefresh = false)
        firstStarted.await()

        // 2. While first is running, start second inspection with forceRefresh = true
        val job2 = manager.startInspection(linkId, "https://example.com/fast_video", forceRefresh = true)

        // Verify that job1 was cancelled immediately
        assertTrue("Job 1 should be cancelled by force refresh", job1.isCancelled)

        // Let job1 attempt to finish if uncancelled
        firstShouldFinish.complete(Unit)
        job2.join()

        // DB should have Fast Video result, not Slow Video result
        val entity = inspectionDao.getEntityForLink(linkId)
        assertNotNull(entity)
        assertEquals("COMPLETED", entity?.status)
        assertEquals("https://example.com/fast_video", entity?.url)
        val parsed = entity?.resultJson?.let { com.laterbox.app.media.data.adapters.InspectionResultJsonAdapter.fromJson(it) }
        assertTrue(parsed is InspectionResult.Available)
        assertEquals("Fast Video Title", (parsed as InspectionResult.Available).title)
    }

    @Test
    fun cancelInspection_cancelsActiveJob_marksCancelled_andJobDoesNotOverwriteCancelled() = testScope.runTest {
        val link = SavedLinkEntity(
            originalUrl = "https://example.com/hanging_video",
            normalizedUrl = "https://example.com/hanging_video",
            title = "Hanging Video",
            domain = "example.com"
        )
        val linkId = linkDao.insertLink(link)

        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val blockUntilCancelled = kotlinx.coroutines.CompletableDeferred<Unit>()

        val hangingAdapter = object : MediaPlatformAdapter {
            override val id = "Hanging"
            override fun canHandle(url: String) = true
            override suspend fun inspect(url: String): InspectionResult = inspect(url, null)
            override suspend fun inspect(url: String, processId: String?): InspectionResult {
                started.complete(Unit)
                blockUntilCancelled.await()
                return InspectionResult.Available("Hanging", "Should Not Persist", null, emptyList())
            }
        }

        val registry = AdapterRegistry(listOf(hangingAdapter))
        val platformResolver = PlatformResolver(registry)
        val manager = MediaInspectionManager(
            appContext = context,
            inspectionDao = inspectionDao,
            platformResolver = platformResolver,
            scope = this
        )

        val job = manager.startInspection(linkId, "https://example.com/hanging_video", forceRefresh = false)
        started.await()

        // Call cancelInspection
        manager.cancelInspection(linkId)
        assertTrue("Active inspection job should be cancelled", job.isCancelled)

        // Status should be CANCELLED in DB
        val entity = inspectionDao.getEntityForLink(linkId)
        assertEquals("CANCELLED", entity?.status)

        // Allow hanging inspection to complete its await
        blockUntilCancelled.complete(Unit)
        job.join()

        // Status must STILL be CANCELLED, not COMPLETED
        val finalEntity = inspectionDao.getEntityForLink(linkId)
        assertEquals("CANCELLED", finalEntity?.status)
    }

    @Test
    fun executeInspection_usesExplicitProcessId_andInvalidatesInfoJsonCacheOnForceRefresh() = testScope.runTest {
        val url = "https://example.com/worker_video"
        val link = SavedLinkEntity(
            originalUrl = url,
            normalizedUrl = url,
            title = "Worker Video",
            domain = "example.com"
        )
        val linkId = linkDao.insertLink(link)

        com.laterbox.app.media.data.adapters.ytdlp.InfoJsonCache.save(context, url, """{"id":"stale"}""", linkId)
        assertNotNull(com.laterbox.app.media.data.adapters.ytdlp.InfoJsonCache.getValidInfoJson(context, url, linkId))

        var observedProcessId: String? = null
        val recordingAdapter = object : MediaPlatformAdapter {
            override val id = "Recorder"
            override fun canHandle(u: String) = true
            override suspend fun inspect(u: String): InspectionResult = inspect(u, null)
            override suspend fun inspect(u: String, processId: String?): InspectionResult {
                observedProcessId = processId
                return InspectionResult.Available("Recorder", "Fresh", null, emptyList())
            }
        }

        val manager = MediaInspectionManager(
            appContext = context,
            inspectionDao = inspectionDao,
            platformResolver = PlatformResolver(AdapterRegistry(listOf(recordingAdapter))),
            scope = this
        )

        val expectedPid = MediaInspectionWorker.buildProcessId(linkId, 2)
        val result = manager.executeInspection(
            savedLinkId = linkId,
            url = url,
            forceRefresh = true,
            explicitProcessId = expectedPid
        )

        assertTrue(result.isSuccess)
        assertEquals(expectedPid, observedProcessId)
        assertEquals(
            null,
            com.laterbox.app.media.data.adapters.ytdlp.InfoJsonCache.getValidInfoJson(context, url, linkId)
        )
    }

    @Test
    fun requestInspection_deduplicatesConcurrentRunAndRecoverOrphanedUnsticksDeadInspectingRow() = testScope.runTest {
        val url = "https://example.com/race_test"
        val linkId = linkDao.insertLink(
            SavedLinkEntity(originalUrl = url, normalizedUrl = url, title = "Race", domain = "example.com")
        )
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        var calls = 0
        val adapter = object : MediaPlatformAdapter {
            override val id = "Slow"
            override fun canHandle(u: String) = true
            override suspend fun inspect(u: String): InspectionResult {
                calls++
                started.complete(Unit)
                release.await()
                return InspectionResult.Available("Slow", "Ok", null, emptyList())
            }
        }
        val manager = MediaInspectionManager(
            appContext = context,
            inspectionDao = inspectionDao,
            platformResolver = PlatformResolver(AdapterRegistry(listOf(adapter))),
            scope = this
        )

        val job1 = manager.requestInspection(linkId, url)
        started.await()
        // Second non-forced request reuses same job; concurrent non-forced worker execute does not clobber gen
        val job2 = manager.requestInspection(linkId, url)
        assertTrue(job1 === job2)
        val dupResult = manager.executeInspection(linkId, url, forceRefresh = false)
        assertTrue(dupResult.isFailure)

        release.complete(Unit)
        job1.join()
        assertEquals(1, calls)
        assertEquals("COMPLETED", inspectionDao.getEntityForLink(linkId)?.status)

        // Simulate orphaned INSPECTING row left by dead process
        inspectionDao.insert(
            com.laterbox.app.media.data.db.entity.MediaInspectionEntity(
                id = "orphan_$linkId", savedLinkId = linkId, url = url, status = "INSPECTING",
                createdAt = 1L, updatedAt = 1L
            )
        )
        assertTrue(manager.isInspectingFlow(linkId).first())
        manager.recoverIfOrphaned(linkId)
        org.junit.Assert.assertFalse(manager.isInspectingFlow(linkId).first())
        assertEquals("QUEUED", inspectionDao.getEntityForLink(linkId)?.status)
    }
}
