package com.laterbox.app.media.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.laterbox.app.data.local.LaterboxDatabase
import com.laterbox.app.data.local.entity.SavedLinkEntity
import com.laterbox.app.media.data.db.dao.MediaInspectionDao
import com.laterbox.app.media.data.db.entity.MediaInspectionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaInspectionDaoTest {

    private lateinit var db: LaterboxDatabase
    private lateinit var inspectionDao: MediaInspectionDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LaterboxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        inspectionDao = db.mediaInspectionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun retryOrRefresh_enforcesSingleRowPerLink_andFlowEmitsNewestResult() = runTest {
        // Precondition: create a saved link
        db.savedLinkDao().insertLink(
            SavedLinkEntity(
                id = 1L,
                originalUrl = "https://youtube.com/watch?v=123",
                normalizedUrl = "https://youtube.com/watch?v=123",
                title = "Video Title",
                domain = "youtube.com"
            )
        )

        // Attempt 1: First inspection completes
        val firstAttempt = MediaInspectionEntity(
            id = "inspect_1_g1000",
            savedLinkId = 1L,
            url = "https://youtube.com/watch?v=123",
            status = "FAILED",
            resultJson = """{"title":"Old Failed Result"}""",
            errorCode = "EXTRACTION_TIMEOUT",
            errorMessage = "Timed out",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        inspectionDao.insert(firstAttempt)

        // Attempt 2: User clicks Retry/Refresh -> second inspection completes with new result
        val secondAttempt = MediaInspectionEntity(
            id = "inspect_1_g2000",
            savedLinkId = 1L,
            url = "https://youtube.com/watch?v=123",
            status = "COMPLETED",
            resultJson = """{"title":"New Fresh Result"}""",
            errorCode = null,
            errorMessage = null,
            createdAt = 1000L,
            updatedAt = 2000L
        )
        inspectionDao.insert(secondAttempt)

        // Count rows in database for savedLinkId = 1
        val cursor = db.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM media_inspection WHERE savedLinkId = 1"
        )
        val rowCount = cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }

        // Must enforce exactly one row per link
        assertEquals("Expected exactly one inspection row per link", 1, rowCount)

        // Flow must emit the newest result, not the stale first result
        val emitted = inspectionDao.getInspectionForLink(1L).first()
        assertEquals("COMPLETED", emitted?.status)
        assertEquals("""{"title":"New Fresh Result"}""", emitted?.resultJson)
    }
}
