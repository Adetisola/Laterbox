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
    fun startInspection_attachesOgFallbackWhenInspectionFails() = testScope.runTest {
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

        // Should have attached the preview image fallback candidate
        assertTrue(result is InspectionResult.Available)
        val available = result as InspectionResult.Available
        assertEquals(1, available.candidates.size)
        assertTrue(available.candidates[0].isOgFallback)
        assertEquals(MediaType.IMAGE, available.candidates[0].mediaType)
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
}

