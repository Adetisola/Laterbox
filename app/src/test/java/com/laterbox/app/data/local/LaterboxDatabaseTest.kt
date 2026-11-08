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

    @Test
    fun getLinksNeedingEnrichmentIncludesPendingFailedAndPartialButSkipsSuccess() = runTest {
        linkDao.insertLink(
            SavedLinkEntity(
                originalUrl = "https://example.com/1",
                normalizedUrl = "https://example.com/1",
                title = "Complete Link",
                domain = "example.com",
                previewImageUrl = "https://example.com/img.jpg",
                metadataStatus = MetadataStatus.SUCCESS
            )
        )
        linkDao.insertLink(
            SavedLinkEntity(
                originalUrl = "https://example.com/2",
                normalizedUrl = "https://example.com/2",
                title = "Partial Link Without Image",
                domain = "example.com",
                previewImageUrl = null,
                metadataStatus = MetadataStatus.PARTIAL
            )
        )
        linkDao.insertLink(
            SavedLinkEntity(
                originalUrl = "https://example.com/3",
                normalizedUrl = "https://example.com/3",
                title = "example.com",
                domain = "example.com",
                metadataStatus = MetadataStatus.FAILED
            )
        )
        linkDao.insertLink(
            SavedLinkEntity(
                originalUrl = "https://example.com/4",
                normalizedUrl = "https://example.com/4",
                title = "example.com",
                domain = "example.com",
                metadataStatus = MetadataStatus.PENDING
            )
        )

        val needingEnrichment = linkDao.getLinksNeedingEnrichment()
        assertEquals(3, needingEnrichment.size)
        val statuses = needingEnrichment.map { it.metadataStatus }.toSet()
        assertTrue(statuses.contains(MetadataStatus.PARTIAL))
        assertTrue(statuses.contains(MetadataStatus.FAILED))
        assertTrue(statuses.contains(MetadataStatus.PENDING))
        assertFalse(statuses.contains(MetadataStatus.SUCCESS))
    }

    @Test
    fun migration1To2MergesDuplicatesWithoutLosingNotesOrMetadataAndEnforcesUniqueIndex() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory database
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `collections` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `name` TEXT NOT NULL,
                            `icon_name` TEXT,
                            `created_at` INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `saved_links` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `original_url` TEXT NOT NULL,
                            `normalized_url` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `description` TEXT,
                            `site_name` TEXT,
                            `domain` TEXT NOT NULL,
                            `favicon_url` TEXT,
                            `preview_image_url` TEXT,
                            `content_type` TEXT NOT NULL,
                            `collection_id` INTEGER,
                            `personal_note` TEXT,
                            `is_read` INTEGER NOT NULL,
                            `is_archived` INTEGER NOT NULL,
                            `metadata_status` TEXT NOT NULL,
                            `retry_count` INTEGER NOT NULL,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            FOREIGN KEY(`collection_id`) REFERENCES `collections`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                        )
                        """.trimIndent()
                    )
                    // v1 had a non-unique index on normalized_url
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_links_normalized_url` ON `saved_links` (`normalized_url`)")
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = Unit
            })
            .build()

        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(config)
        val sqliteDb = helper.writableDatabase

        // Insert a collection and two duplicate normalized_url records in v1 schema
        sqliteDb.execSQL("INSERT INTO `collections` (`id`, `name`, `created_at`) VALUES (10, 'Tech', 1000)")
        sqliteDb.execSQL(
            """
            INSERT INTO `saved_links` (
                `id`, `original_url`, `normalized_url`, `title`, `description`, `site_name`, `domain`,
                `favicon_url`, `preview_image_url`, `content_type`, `collection_id`, `personal_note`,
                `is_read`, `is_archived`, `metadata_status`, `retry_count`, `created_at`, `updated_at`
            ) VALUES (
                1, 'https://example.com/post?utm_source=x', 'https://example.com/post', 'example.com',
                NULL, NULL, 'example.com', NULL, NULL, 'WEBSITE', 10, 'First note from share',
                0, 0, 'FAILED', 1, 1000, 1000
            )
            """.trimIndent()
        )
        sqliteDb.execSQL(
            """
            INSERT INTO `saved_links` (
                `id`, `original_url`, `normalized_url`, `title`, `description`, `site_name`, `domain`,
                `favicon_url`, `preview_image_url`, `content_type`, `collection_id`, `personal_note`,
                `is_read`, `is_archived`, `metadata_status`, `retry_count`, `created_at`, `updated_at`
            ) VALUES (
                2, 'https://example.com/post', 'https://example.com/post', 'Rich Article Title',
                'Rich description', 'Example Pub', 'example.com', 'https://example.com/fav.ico',
                'https://example.com/hero.jpg', 'ARTICLE', NULL, 'Second note added later',
                1, 0, 'SUCCESS', 0, 2000, 2500
            )
            """.trimIndent()
        )

        // Run MIGRATION_1_2
        LaterboxDatabase.MIGRATION_1_2.migrate(sqliteDb)

        // Verify only 1 row remains and all fields were merged non-destructively
        val cursor = sqliteDb.query("SELECT id, title, description, preview_image_url, collection_id, personal_note, is_read, metadata_status FROM saved_links WHERE normalized_url = 'https://example.com/post'")
        assertTrue(cursor.moveToFirst())
        assertEquals(1, cursor.count)
        assertEquals(1L, cursor.getLong(0))
        assertEquals("Rich Article Title", cursor.getString(1))
        assertEquals("Rich description", cursor.getString(2))
        assertEquals("https://example.com/hero.jpg", cursor.getString(3))
        assertEquals(10L, cursor.getLong(4))
        val mergedNote = cursor.getString(5)
        assertTrue(mergedNote.contains("First note from share"))
        assertTrue(mergedNote.contains("Second note added later"))
        assertEquals(1, cursor.getInt(6))
        assertEquals("SUCCESS", cursor.getString(7))
        cursor.close()

        // Verify unique index rejects duplicate insert
        var duplicateThrew = false
        try {
            sqliteDb.execSQL(
                """
                INSERT INTO `saved_links` (
                    `original_url`, `normalized_url`, `title`, `domain`, `content_type`,
                    `is_read`, `is_archived`, `metadata_status`, `retry_count`, `created_at`, `updated_at`
                ) VALUES (
                    'https://example.com/post', 'https://example.com/post', 'Dup', 'example.com', 'WEBSITE',
                    0, 0, 'PENDING', 0, 3000, 3000
                )
                """.trimIndent()
            )
        } catch (e: Exception) {
            duplicateThrew = true
        }
        assertTrue("Expected unique constraint violation on normalized_url after MIGRATION_1_2", duplicateThrew)
        helper.close()
    }
}

