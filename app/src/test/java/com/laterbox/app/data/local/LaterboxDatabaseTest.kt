package com.laterbox.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.laterbox.app.data.local.dao.CollectionDao
import com.laterbox.app.data.local.dao.SavedLinkDao
import com.laterbox.app.data.local.entity.CollectionEntity
import com.laterbox.app.data.local.entity.SavedLinkEntity
import com.laterbox.app.domain.model.ContentType
import com.laterbox.app.domain.model.MetadataStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaterboxDatabaseTest {

    private lateinit var db: LaterboxDatabase
    private lateinit var linkDao: SavedLinkDao
    private lateinit var collectionDao: CollectionDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LaterboxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        linkDao = db.savedLinkDao()
        collectionDao = db.collectionDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndRetrieveCollection() = runTest {
        val collection = CollectionEntity(name = "Reading List")
        val id = collectionDao.insertCollection(collection)
        assertTrue(id > 0)

        val retrieved = collectionDao.getCollections().first()
        assertEquals(1, retrieved.size)
        assertEquals("Reading List", retrieved[0].name)
    }

    @Test
    fun insertAndRetrieveSavedLinkWithCollection() = runTest {
        val colId = collectionDao.insertCollection(CollectionEntity(name = "Inspiration"))

        val link = SavedLinkEntity(
            originalUrl = "https://example.com/design",
            normalizedUrl = "https://example.com/design",
            title = "Design Systems",
            domain = "example.com",
            collectionId = colId
        )
        val linkId = linkDao.insertLink(link)
        assertTrue(linkId > 0)

        val inbox = linkDao.getInboxLinks().first()
        assertEquals(1, inbox.size)
        assertEquals("Design Systems", inbox[0].link.title)
        assertEquals("Inspiration", inbox[0].collection?.name)
    }

    @Test
    fun collectionDeletionPreservesLinksWithSetNull() = runTest {
        val colId = collectionDao.insertCollection(CollectionEntity(name = "Watch Later"))

        val link = SavedLinkEntity(
            originalUrl = "https://youtube.com/watch?v=123",
            normalizedUrl = "https://youtube.com/watch?v=123",
            title = "Kotlin Conf Talk",
            domain = "youtube.com",
            collectionId = colId
        )
        val linkId = linkDao.insertLink(link)

        // Verify collection count is 1
        val collectionsWithCounts = collectionDao.getCollectionsWithCounts().first()
        assertEquals(1, collectionsWithCounts[0].itemCount)

        // Delete collection
        collectionDao.deleteCollection(colId)

        // Link MUST still exist in Inbox with collectionId == null
        val inbox = linkDao.getInboxLinks().first()
        assertEquals(1, inbox.size)
        assertNull(inbox[0].link.collectionId)
        assertNull(inbox[0].collection)
    }

    @Test
    fun findLinkByNormalizedUrlDetectsDuplicate() = runTest {
        val link = SavedLinkEntity(
            originalUrl = "https://example.com/guide?utm_source=twitter",
            normalizedUrl = "https://example.com/guide",
            title = "Architecture Guide",
            domain = "example.com"
        )
        linkDao.insertLink(link)

        val found = linkDao.findLinkByNormalizedUrl("https://example.com/guide")
        assertNotNull(found)
        assertEquals("Architecture Guide", found?.link?.title)

        val notFound = linkDao.findLinkByNormalizedUrl("https://example.com/other")
        assertNull(notFound)
    }

    @Test
    fun searchAcrossTitleDomainAndNote() = runTest {
        val link1 = SavedLinkEntity(
            originalUrl = "https://github.com/google/ksp",
            normalizedUrl = "https://github.com/google/ksp",
            title = "Kotlin Symbol Processing",
            domain = "github.com",
            personalNote = "Great compiler plugin"
        )
        val link2 = SavedLinkEntity(
            originalUrl = "https://reddit.com/r/androiddev",
            normalizedUrl = "https://reddit.com/r/androiddev",
            title = "Android Dev Community",
            domain = "reddit.com"
        )
        linkDao.insertLink(link1)
        linkDao.insertLink(link2)

        // Search by title
        val titleMatch = linkDao.searchLinks("Symbol").first()
        assertEquals(1, titleMatch.size)

        // Search by domain
        val domainMatch = linkDao.searchLinks("github").first()
        assertEquals(1, domainMatch.size)

        // Search by personal note
        val noteMatch = linkDao.searchLinks("compiler").first()
        assertEquals(1, noteMatch.size)

        // No match
        val noMatch = linkDao.searchLinks("flutter").first()
        assertEquals(0, noMatch.size)
    }

    @Test
    fun archiveAndRestoreWorkflow() = runTest {
        val link = SavedLinkEntity(
            originalUrl = "https://example.com",
            normalizedUrl = "https://example.com",
            title = "Example",
            domain = "example.com",
            isArchived = false
        )
        val id = linkDao.insertLink(link)

        // Starts in Inbox
        assertEquals(1, linkDao.getInboxLinks().first().size)
        assertEquals(0, linkDao.getArchivedLinks().first().size)

        // Archive link
        linkDao.setArchived(id, true)
        assertEquals(0, linkDao.getInboxLinks().first().size)
        assertEquals(1, linkDao.getArchivedLinks().first().size)

        // Restore link
        linkDao.setArchived(id, false)
        assertEquals(1, linkDao.getInboxLinks().first().size)
        assertEquals(0, linkDao.getArchivedLinks().first().size)
    }

    @Test
    fun readUnreadToggle() = runTest {
        val link = SavedLinkEntity(
            originalUrl = "https://example.com",
            normalizedUrl = "https://example.com",
            title = "Example",
            domain = "example.com",
            isRead = false
        )
        val id = linkDao.insertLink(link)

        var item = linkDao.getLinkById(id).first()
        assertFalse(item?.link?.isRead ?: true)

        linkDao.setRead(id, true)
        item = linkDao.getLinkById(id).first()
        assertTrue(item?.link?.isRead ?: false)
    }
}
