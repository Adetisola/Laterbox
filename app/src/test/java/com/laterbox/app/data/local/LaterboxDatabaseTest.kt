package com.laterbox.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.laterbox.app.data.local.dao.CollectionDao
import com.laterbox.app.data.local.dao.NotificationHistoryDao
import com.laterbox.app.data.local.dao.SavedLinkDao
import com.laterbox.app.data.local.entity.CollectionEntity
import com.laterbox.app.data.local.entity.NotificationHistoryEntity
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
    private lateinit var notificationDao: NotificationHistoryDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LaterboxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        linkDao = db.savedLinkDao()
        collectionDao = db.collectionDao()
        notificationDao = db.notificationHistoryDao()
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
                            `last_fetched_at` INTEGER,
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

    @Test
    fun migration2To3MigratesLegacyCollectionAndAddsReminderColumns() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
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
                            `last_fetched_at` INTEGER,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            FOREIGN KEY(`collection_id`) REFERENCES `collections`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_saved_links_normalized_url` ON `saved_links` (`normalized_url`)")
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

        // Insert collection and link in v2 schema
        sqliteDb.execSQL("INSERT INTO `collections` (`id`, `name`, `created_at`) VALUES (42, 'News', 5000)")
        sqliteDb.execSQL(
            """
            INSERT INTO `saved_links` (
                `id`, `original_url`, `normalized_url`, `title`, `domain`, `content_type`,
                `collection_id`, `is_read`, `is_archived`, `metadata_status`, `retry_count`,
                `created_at`, `updated_at`
            ) VALUES (
                100, 'https://news.ycombinator.com', 'https://news.ycombinator.com', 'Hacker News', 'ycombinator.com',
                'WEBSITE', 42, 0, 0, 'SUCCESS', 0, 5000, 5000
            )
            """.trimIndent()
        )

        // Run MIGRATION_2_3
        LaterboxDatabase.MIGRATION_2_3.migrate(sqliteDb)

        // Verify cross-ref table was created and populated with legacy link
        val crossRefCursor = sqliteDb.query("SELECT link_id, collection_id FROM link_collection_cross_ref WHERE link_id = 100")
        assertTrue(crossRefCursor.moveToFirst())
        assertEquals(100L, crossRefCursor.getLong(0))
        assertEquals(42L, crossRefCursor.getLong(1))
        crossRefCursor.close()

        // Verify reminder columns were added with default values
        val linkCursor = sqliteDb.query("SELECT reminder_at, last_nudged_at, nudge_count, nudge_dismissed FROM saved_links WHERE id = 100")
        assertTrue(linkCursor.moveToFirst())
        assertTrue(linkCursor.isNull(0))
        assertTrue(linkCursor.isNull(1))
        assertEquals(0, linkCursor.getInt(2))
        assertEquals(0, linkCursor.getInt(3))
        linkCursor.close()

        helper.close()
    }

    @Test
    fun reminderDao_updateAndDismissQueries() = runTest {
        val link = SavedLinkEntity(
            originalUrl = "https://example.com/reminder-test",
            normalizedUrl = "https://example.com/reminder-test",
            title = "Reminder Test Article",
            domain = "example.com"
        )
        val linkId = linkDao.insertLink(link)

        // Set reminder
        val reminderTime = System.currentTimeMillis() + 3600_000L
        linkDao.updateReminder(linkId, reminderTime)

        var entity = linkDao.getEntityById(linkId)
        assertNotNull(entity)
        assertEquals(reminderTime, entity?.reminderAt)
        assertEquals(false, entity?.nudgeDismissed)

        // Dismiss reminder
        linkDao.dismissReminder(linkId)
        entity = linkDao.getEntityById(linkId)
        assertEquals(true, entity?.nudgeDismissed)

        // Record nudge
        val now = System.currentTimeMillis()
        linkDao.recordNudge(linkId, now)
        entity = linkDao.getEntityById(linkId)
        assertEquals(1, entity?.nudgeCount)
        assertEquals(now, entity?.lastNudgedAt)
    }

    @Test
    fun reminderDao_dueAndScheduledQueries() = runTest {
        val now = 1_000_000L

        // Past reminder (due)
        val pastId = linkDao.insertLink(
            SavedLinkEntity(
                originalUrl = "https://example.com/past",
                normalizedUrl = "https://example.com/past",
                title = "Past",
                domain = "example.com",
                reminderAt = now - 500L,
                nudgeDismissed = false
            )
        )

        // Future reminder (scheduled)
        val futureId = linkDao.insertLink(
            SavedLinkEntity(
                originalUrl = "https://example.com/future",
                normalizedUrl = "https://example.com/future",
                title = "Future",
                domain = "example.com",
                reminderAt = now + 5000L,
                nudgeDismissed = false
            )
        )

        // Dismissed reminder (should not show in either)
        linkDao.insertLink(
            SavedLinkEntity(
                originalUrl = "https://example.com/dismissed",
                normalizedUrl = "https://example.com/dismissed",
                title = "Dismissed",
                domain = "example.com",
                reminderAt = now - 500L,
                nudgeDismissed = true
            )
        )

        val due = linkDao.getDueReminders(now)
        assertEquals(1, due.size)
        assertEquals(pastId, due[0].link.id)

        val scheduled = linkDao.getScheduledReminders(now)
        assertEquals(1, scheduled.size)
        assertEquals(futureId, scheduled[0].link.id)
    }

    @Test
    fun getSmartDigestCandidates_honorsMaxNudgeCount() = runTest {
        val now = System.currentTimeMillis()
        val oldTimestamp = now - (48 * 60 * 60 * 1000L) // 2 days old

        // Candidate with nudgeCount = 1
        val id1 = linkDao.insertLink(
            SavedLinkEntity(
                originalUrl = "https://example.com/one",
                normalizedUrl = "https://example.com/one",
                title = "Link One",
                domain = "example.com",
                isArchived = false,
                isRead = false,
                nudgeDismissed = false,
                nudgeCount = 1,
                createdAt = oldTimestamp
            )
        )

        // Candidate with nudgeCount = 2
        val id2 = linkDao.insertLink(
            SavedLinkEntity(
                originalUrl = "https://example.com/two",
                normalizedUrl = "https://example.com/two",
                title = "Link Two",
                domain = "example.com",
                isArchived = false,
                isRead = false,
                nudgeDismissed = false,
                nudgeCount = 2,
                createdAt = oldTimestamp
            )
        )

        // When maxNudgeCount = 2, only id1 is returned
        val candidatesForTwo = linkDao.getSmartDigestCandidates(
            minAgeThreshold = now - (24 * 60 * 60 * 1000L),
            recentNudgeThreshold = now - (24 * 60 * 60 * 1000L),
            maxNudgeCount = 2
        )
        assertEquals(1, candidatesForTwo.size)
        assertEquals(id1, candidatesForTwo[0].link.id)

        // When maxNudgeCount = 3, both id1 and id2 are returned
        val candidatesForThree = linkDao.getSmartDigestCandidates(
            minAgeThreshold = now - (24 * 60 * 60 * 1000L),
            recentNudgeThreshold = now - (24 * 60 * 60 * 1000L),
            maxNudgeCount = 3
        )
        assertEquals(2, candidatesForThree.size)
    }

    @Test
    fun insertAndRetrieveNotificationHistoryItem() = runTest {
        val entity = NotificationHistoryEntity(
            url = "https://example.com/notification-item",
            title = "Scheduled Read",
            body = "Remember to review this article",
            origin = "MANUAL_REMINDER",
            domain = "example.com",
            siteName = "Example",
            isRead = false,
            createdAt = 1000L
        )
        val id = notificationDao.insert(entity)
        assertTrue(id > 0)

        val history = notificationDao.getAllHistory().first()
        assertEquals(1, history.size)
        assertEquals("Scheduled Read", history[0].title)
        assertEquals("https://example.com/notification-item", history[0].url)
        assertFalse(history[0].isRead)
    }

    @Test
    fun notificationMarkReadAndUnreadWorkflow() = runTest {
        val id = notificationDao.insert(
            NotificationHistoryEntity(
                url = "https://example.com/item",
                title = "Title",
                body = "Body",
                origin = "SMART_DIGEST",
                domain = "example.com",
                isRead = false,
                createdAt = 2000L
            )
        )

        assertEquals(1, notificationDao.getUnreadCount().first())

        notificationDao.markAsRead(id)
        assertEquals(0, notificationDao.getUnreadCount().first())
        val readList = notificationDao.getAllHistory().first()
        assertTrue(readList[0].isRead)

        notificationDao.markAsUnread(id)
        assertEquals(1, notificationDao.getUnreadCount().first())
        val unreadList = notificationDao.getAllHistory().first()
        assertFalse(unreadList[0].isRead)
    }

    @Test
    fun notificationMarkReadByLinkId() = runTest {
        val linkId = 777L
        notificationDao.insert(
            NotificationHistoryEntity(
                linkId = linkId,
                url = "https://example.com/item1",
                title = "Item 1",
                body = "Body 1",
                origin = "SMART_DIGEST",
                domain = "example.com",
                isRead = false,
                createdAt = 1000L
            )
        )
        notificationDao.insert(
            NotificationHistoryEntity(
                linkId = linkId,
                url = "https://example.com/item2",
                title = "Item 2",
                body = "Body 2",
                origin = "MANUAL_REMINDER",
                domain = "example.com",
                isRead = false,
                createdAt = 2000L
            )
        )

        assertEquals(2, notificationDao.getUnreadCount().first())
        notificationDao.markReadByLinkId(linkId)
        assertEquals(0, notificationDao.getUnreadCount().first())
    }

    @Test
    fun notificationClearAll() = runTest {
        notificationDao.insert(
            NotificationHistoryEntity(
                url = "https://example.com/item1",
                title = "Item 1",
                body = "Body 1",
                origin = "SMART_DIGEST",
                domain = "example.com",
                createdAt = 1000L
            )
        )
        assertEquals(1, notificationDao.getAllHistory().first().size)
        notificationDao.clearAll()
        assertEquals(0, notificationDao.getAllHistory().first().size)
    }

    @Test
    fun migration3To4CreatesNotificationHistoryTableAndPreservesData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(3) {
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
                            `last_fetched_at` INTEGER,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            `reminder_at` INTEGER,
                            `last_nudged_at` INTEGER,
                            `nudge_count` INTEGER NOT NULL DEFAULT 0,
                            `nudge_dismissed` INTEGER NOT NULL DEFAULT 0,
                            FOREIGN KEY(`collection_id`) REFERENCES `collections`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `link_collection_cross_ref` (
                            `link_id` INTEGER NOT NULL,
                            `collection_id` INTEGER NOT NULL,
                            PRIMARY KEY(`link_id`, `collection_id`),
                            FOREIGN KEY(`link_id`) REFERENCES `saved_links`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                            FOREIGN KEY(`collection_id`) REFERENCES `collections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
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

        // Insert sample collection and link in v3 schema
        sqliteDb.execSQL("INSERT INTO `collections` (`id`, `name`, `created_at`) VALUES (99, 'Reading', 1000)")
        sqliteDb.execSQL(
            """
            INSERT INTO `saved_links` (
                `id`, `original_url`, `normalized_url`, `title`, `domain`, `content_type`,
                `is_read`, `is_archived`, `metadata_status`, `retry_count`, `created_at`, `updated_at`,
                `nudge_count`, `nudge_dismissed`
            ) VALUES (
                500, 'https://example.com/v3', 'https://example.com/v3', 'V3 Link', 'example.com',
                'WEBSITE', 0, 0, 'SUCCESS', 0, 1000, 1000, 0, 0
            )
            """.trimIndent()
        )

        // Run MIGRATION_3_4
        LaterboxDatabase.MIGRATION_3_4.migrate(sqliteDb)

        // Verify notification_history table exists and supports inserts
        sqliteDb.execSQL(
            """
            INSERT INTO `notification_history` (
                `url`, `title`, `body`, `origin`, `domain`, `is_read`, `created_at`
            ) VALUES (
                'https://example.com/migrated', 'Migrated Reminder', 'Test body', 'SMART_DIGEST', 'example.com', 0, 2000
            )
            """.trimIndent()
        )

        val cursor = sqliteDb.query("SELECT title, url FROM notification_history WHERE url = 'https://example.com/migrated'")
        assertTrue(cursor.moveToFirst())
        assertEquals("Migrated Reminder", cursor.getString(0))
        cursor.close()

        // Verify existing saved_links and collections are preserved
        val linkCursor = sqliteDb.query("SELECT title FROM saved_links WHERE id = 500")
        assertTrue(linkCursor.moveToFirst())
        assertEquals("V3 Link", linkCursor.getString(0))
        linkCursor.close()

        helper.close()
    }

    @Test
    fun migration4To5CreatesMediaTablesAndMaintainsForeignKeys() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(4) {
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
                            `last_fetched_at` INTEGER,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            `reminder_at` INTEGER,
                            `last_nudged_at` INTEGER,
                            `nudge_count` INTEGER NOT NULL DEFAULT 0,
                            `nudge_dismissed` INTEGER NOT NULL DEFAULT 0,
                            FOREIGN KEY(`collection_id`) REFERENCES `collections`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_saved_links_normalized_url` ON `saved_links` (`normalized_url`)")
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `notification_history` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `link_id` INTEGER,
                            `url` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `body` TEXT NOT NULL,
                            `origin` TEXT NOT NULL,
                            `domain` TEXT NOT NULL,
                            `site_name` TEXT,
                            `preview_image_url` TEXT,
                            `favicon_url` TEXT,
                            `is_read` INTEGER NOT NULL DEFAULT 0,
                            `created_at` INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
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

        // Insert sample link in v4 schema
        sqliteDb.execSQL(
            """
            INSERT INTO `saved_links` (
                `id`, `original_url`, `normalized_url`, `title`, `domain`, `content_type`,
                `is_read`, `is_archived`, `metadata_status`, `retry_count`, `created_at`, `updated_at`,
                `nudge_count`, `nudge_dismissed`
            ) VALUES (
                1001, 'https://example.com/media-link', 'https://example.com/media-link', 'Media Link', 'example.com',
                'WEBSITE', 0, 0, 'SUCCESS', 0, 1000, 1000, 0, 0
            )
            """.trimIndent()
        )

        // Run MIGRATION_4_5
        LaterboxDatabase.MIGRATION_4_5.migrate(sqliteDb)

        // Insert downloaded_asset and download_task referencing saved_links(id = 1001)
        sqliteDb.execSQL(
            """
            INSERT INTO `downloaded_asset` (
                `id`, `savedLinkId`, `sourcePlatform`, `originalSourceUrl`, `title`, `mimeType`,
                `mediaType`, `localPath`, `fileStatus`, `fileSize`, `checksum`, `createdAt`
            ) VALUES (
                'asset-1', 1001, 'Direct', 'https://example.com/media.mp4', 'Sample Video', 'video/mp4',
                'VIDEO', 'video/abc123.mp4', 'AVAILABLE', 10240, 'abc123sha256', 2000
            )
            """.trimIndent()
        )

        sqliteDb.execSQL(
            """
            INSERT INTO `download_task` (
                `id`, `savedLinkId`, `sourceUrl`, `sourcePlatform`, `mediaType`,
                `status`, `progress`, `downloadedBytes`, `retryCount`, `createdAt`, `updatedAt`
            ) VALUES (
                'task-1', 1001, 'https://example.com/media.mp4', 'Direct', 'VIDEO',
                'QUEUED', 0, 0, 0, 2000, 2000
            )
            """.trimIndent()
        )

        // Verify inserted rows exist
        val assetCursor = sqliteDb.query("SELECT id, savedLinkId, title FROM downloaded_asset WHERE id = 'asset-1'")
        assertTrue(assetCursor.moveToFirst())
        assertEquals("asset-1", assetCursor.getString(0))
        assertEquals(1001L, assetCursor.getLong(1))
        assertEquals("Sample Video", assetCursor.getString(2))
        assetCursor.close()

        val taskCursor = sqliteDb.query("SELECT id, status FROM download_task WHERE id = 'task-1'")
        assertTrue(taskCursor.moveToFirst())
        assertEquals("task-1", taskCursor.getString(0))
        assertEquals("QUEUED", taskCursor.getString(1))
        taskCursor.close()

        helper.close()
    }

    @Test
    fun resetInterruptedTasksToQueued_resetsActiveTasksWhilePreservingPausedAndCompleted() = runTest {
        val link = SavedLinkEntity(
            originalUrl = "https://example.com/test-active",
            normalizedUrl = "https://example.com/test-active",
            title = "Test Link",
            domain = "example.com"
        )
        val linkId = linkDao.insertLink(link)
        val taskDao = db.downloadTaskDao()

        val taskDownloading = com.laterbox.app.media.data.db.entity.DownloadTaskEntity(
            id = "task-downloading",
            savedLinkId = linkId,
            sourceUrl = "https://example.com/video1.mp4",
            sourcePlatform = "Direct",
            candidateId = null,
            formatSelector = null,
            mediaType = "VIDEO",
            qualityLabel = null,
            title = "Downloading Video",
            status = "DOWNLOADING",
            createdAt = 1000L,
            updatedAt = 1000L
        )

        val taskProcessing = com.laterbox.app.media.data.db.entity.DownloadTaskEntity(
            id = "task-processing",
            savedLinkId = linkId,
            sourceUrl = "https://example.com/video2.mp4",
            sourcePlatform = "Direct",
            candidateId = null,
            formatSelector = null,
            mediaType = "VIDEO",
            qualityLabel = null,
            title = "Processing Video",
            status = "PROCESSING",
            createdAt = 1000L,
            updatedAt = 1000L
        )

        val taskPaused = com.laterbox.app.media.data.db.entity.DownloadTaskEntity(
            id = "task-paused",
            savedLinkId = linkId,
            sourceUrl = "https://example.com/video3.mp4",
            sourcePlatform = "Direct",
            candidateId = null,
            formatSelector = null,
            mediaType = "VIDEO",
            qualityLabel = null,
            title = "Paused Video",
            status = "PAUSED",
            createdAt = 1000L,
            updatedAt = 1000L
        )

        val taskCompleted = com.laterbox.app.media.data.db.entity.DownloadTaskEntity(
            id = "task-completed",
            savedLinkId = linkId,
            sourceUrl = "https://example.com/video4.mp4",
            sourcePlatform = "Direct",
            candidateId = null,
            formatSelector = null,
            mediaType = "VIDEO",
            qualityLabel = null,
            title = "Completed Video",
            status = "COMPLETED",
            createdAt = 1000L,
            updatedAt = 1000L
        )

        taskDao.insertTask(taskDownloading)
        taskDao.insertTask(taskProcessing)
        taskDao.insertTask(taskPaused)
        taskDao.insertTask(taskCompleted)

        val updatedCount = taskDao.resetInterruptedTasksToQueued()
        assertEquals(2, updatedCount)

        assertEquals("QUEUED", taskDao.getEntityById("task-downloading")?.status)
        assertEquals("QUEUED", taskDao.getEntityById("task-processing")?.status)
        assertEquals("PAUSED", taskDao.getEntityById("task-paused")?.status)
        assertEquals("COMPLETED", taskDao.getEntityById("task-completed")?.status)
    }

    @Test
    fun testMusicMetadataColumnsInDownloadedAssetAndTask() = runTest {
        val link = SavedLinkEntity(
            originalUrl = "https://open.spotify.com/track/123",
            normalizedUrl = "https://open.spotify.com/track/123",
            title = "Spotify Track",
            domain = "open.spotify.com"
        )
        val linkId = linkDao.insertLink(link)
        val assetDao = db.downloadedAssetDao()
        val taskDao = db.downloadTaskDao()

        val task = com.laterbox.app.media.data.db.entity.DownloadTaskEntity(
            id = "task-music-1",
            savedLinkId = linkId,
            sourceUrl = "https://youtube.com/watch?v=123",
            sourcePlatform = "Spotify",
            candidateId = "cand-1",
            formatSelector = "bestaudio",
            mediaType = "AUDIO",
            qualityLabel = "Audio (m4a)",
            title = "Blinding Lights",
            status = "QUEUED",
            createdAt = 1000L,
            updatedAt = 1000L,
            artist = "The Weeknd",
            album = "After Hours",
            artworkUrl = "https://example.com/art.jpg",
            spotifyTrackId = "track123",
            versionTag = null
        )
        taskDao.insertTask(task)

        val retrievedTask = taskDao.getEntityById("task-music-1")
        assertNotNull(retrievedTask)
        assertEquals("The Weeknd", retrievedTask?.artist)
        assertEquals("After Hours", retrievedTask?.album)
        assertEquals("track123", retrievedTask?.spotifyTrackId)

        val asset = com.laterbox.app.media.data.db.entity.DownloadedAssetEntity(
            id = "asset-music-1",
            savedLinkId = linkId,
            sourcePlatform = "Spotify",
            originalSourceUrl = "https://youtube.com/watch?v=123",
            title = "Blinding Lights",
            mimeType = "audio/mp4",
            mediaType = "AUDIO",
            localPath = "audio/asset-music-1.m4a",
            fileSize = 5000000L,
            checksum = "sha256fake",
            createdAt = 1000L,
            artist = "The Weeknd",
            album = "After Hours",
            artworkUrl = "https://example.com/art.jpg",
            spotifyTrackId = "track123",
            versionTag = null
        )
        assetDao.insertAsset(asset)

        val retrievedAsset = assetDao.getEntityById("asset-music-1")
        assertNotNull(retrievedAsset)
        assertEquals("The Weeknd", retrievedAsset?.artist)
        assertEquals("After Hours", retrievedAsset?.album)
        assertEquals("track123", retrievedAsset?.spotifyTrackId)

        // Verify filterAssets matches artist
        val matchesArtist = assetDao.filterAssets("AUDIO", "Weeknd").first()
        assertEquals(1, matchesArtist.size)

        // Verify filterAssets matches album
        val matchesAlbum = assetDao.filterAssets("AUDIO", "After Hours").first()
        assertEquals(1, matchesAlbum.size)
    }
}

