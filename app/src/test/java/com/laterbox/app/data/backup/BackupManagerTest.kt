package com.laterbox.app.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.laterbox.app.data.local.LaterboxDatabase
import com.laterbox.app.data.local.entity.CollectionEntity
import com.laterbox.app.data.local.entity.SavedLinkEntity
import com.laterbox.app.domain.model.ContentType
import kotlinx.coroutines.flow.first
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupManagerTest {

    private lateinit var database: LaterboxDatabase
    private lateinit var backupManager: BackupManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LaterboxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        backupManager = BackupManager(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun serializationAndDeserialization_preservesAllFields() {
        val backup = LaterboxBackup(
            version = 1,
            exportedAt = 1700000000000L,
            appName = "Laterbox",
            collections = listOf(
                BackupCollection(name = "Design Inspiration", createdAt = 1699999999000L)
            ),
            links = listOf(
                BackupLink(
                    originalUrl = "https://example.com/article",
                    normalizedUrl = "https://example.com/article",
                    title = "Design Systems in 2026",
                    description = "Comprehensive guide",
                    siteName = "Example Blog",
                    domain = "example.com",
                    faviconUrl = "https://example.com/favicon.ico",
                    previewImageUrl = "https://example.com/preview.png",
                    contentType = ContentType.ARTICLE,
                    personalNote = "Important reference for typography",
                    collectionName = "Design Inspiration",
                    isRead = true,
                    isArchived = false,
                    createdAt = 1700000000000L
                )
            )
        )

        val json = backupManager.serializeBackup(backup)
        val deserialized = backupManager.deserializeBackup(json)

        assertEquals(1, deserialized.version)
        assertEquals("Laterbox", deserialized.appName)
        assertEquals(1, deserialized.collections.size)
        assertEquals("Design Inspiration", deserialized.collections[0].name)
        assertEquals(1, deserialized.links.size)

        val link = deserialized.links[0]
        assertEquals("https://example.com/article", link.originalUrl)
        assertEquals("Design Systems in 2026", link.title)
        assertEquals("Important reference for typography", link.personalNote)
        assertEquals("Design Inspiration", link.collectionName)
        assertEquals(ContentType.ARTICLE, link.contentType)
        assertTrue(link.isRead)
    }

    @Test
    fun exportAndImport_roundtripAndDeduplication() = runTest {
        val colDao = database.collectionDao()
        val linkDao = database.savedLinkDao()

        // Insert initial data
        val colId = colDao.insertCollection(CollectionEntity(name = "Tech"))
        linkDao.insertLink(
            SavedLinkEntity(
                originalUrl = "https://github.com",
                normalizedUrl = "https://github.com",
                title = "GitHub",
                domain = "github.com",
                collectionId = colId
            )
        )

        // Export to stream
        val out = ByteArrayOutputStream()
        val exportResult = backupManager.exportToJson(out)
        assertTrue(exportResult.isSuccess)
        assertEquals(1, exportResult.getOrNull())

        // Create a second clean in-memory database to simulate restore
        val context = ApplicationProvider.getApplicationContext<Context>()
        val targetDb = Room.inMemoryDatabaseBuilder(context, LaterboxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val targetManager = BackupManager(targetDb)

        try {
            // Import into clean database
            val input = ByteArrayInputStream(out.toByteArray())
            val importResult = targetManager.importFromJson(input)
            assertTrue(importResult.isSuccess)

            val stats = importResult.getOrThrow()
            assertEquals(1, stats.restoredLinksCount)
            assertEquals(1, stats.restoredCollectionsCount)
            assertEquals(0, stats.skippedDuplicatesCount)

            val importedInbox = targetDb.savedLinkDao().getInboxLinks().first()
            assertEquals(1, importedInbox.size)
            assertEquals("GitHub", importedInbox[0].link.title)
            assertEquals("Tech", importedInbox[0].collection?.name)

            // Re-importing into the same database should skip duplicate
            val duplicateInput = ByteArrayInputStream(out.toByteArray())
            val secondImport = targetManager.importFromJson(duplicateInput)
            val secondStats = secondImport.getOrThrow()
            assertEquals(0, secondStats.restoredLinksCount)
            assertEquals(1, secondStats.skippedDuplicatesCount)
        } finally {
            targetDb.close()
        }
    }

    @Test
    fun exportAndImport_multiCollectionSupport() = runTest {
        val colDao = database.collectionDao()
        val linkDao = database.savedLinkDao()

        val techId = colDao.insertCollection(CollectionEntity(name = "Tech"))
        val readsId = colDao.insertCollection(CollectionEntity(name = "Reads"))

        val linkId = linkDao.insertLink(
            SavedLinkEntity(
                originalUrl = "https://kotlinlang.org",
                normalizedUrl = "https://kotlinlang.org",
                title = "Kotlin",
                domain = "kotlinlang.org"
            )
        )
        linkDao.insertLinkCollectionCrossRef(
            com.laterbox.app.data.local.entity.LinkCollectionCrossRef(linkId, techId)
        )
        linkDao.insertLinkCollectionCrossRef(
            com.laterbox.app.data.local.entity.LinkCollectionCrossRef(linkId, readsId)
        )

        val out = ByteArrayOutputStream()
        backupManager.exportToJson(out)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val targetDb = Room.inMemoryDatabaseBuilder(context, LaterboxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val targetManager = BackupManager(targetDb)
        try {
            val input = ByteArrayInputStream(out.toByteArray())
            val importResult = targetManager.importFromJson(input)
            assertTrue(importResult.isSuccess)

            val imported = targetDb.savedLinkDao().getInboxLinks().first()
            assertEquals(1, imported.size)
            assertEquals(2, imported[0].collections.size)
            val names = imported[0].collections.map { it.name }.toSet()
            assertTrue(names.contains("Tech"))
            assertTrue(names.contains("Reads"))
        } finally {
            targetDb.close()
        }
    }
}
