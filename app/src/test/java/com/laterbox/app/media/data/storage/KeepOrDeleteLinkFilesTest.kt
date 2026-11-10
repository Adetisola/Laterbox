package com.laterbox.app.media.data.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.laterbox.app.data.local.LaterboxDatabase
import com.laterbox.app.data.local.entity.SavedLinkEntity
import com.laterbox.app.media.data.db.entity.DownloadedAssetEntity
import com.laterbox.app.media.data.repository.MediaRepositoryImpl
import com.laterbox.app.media.domain.model.AssetFileStatus
import com.laterbox.app.media.domain.model.MediaType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
class KeepOrDeleteLinkFilesTest {

    private lateinit var database: LaterboxDatabase
    private lateinit var context: Context
    private lateinit var storageManager: MediaStorageManager
    private lateinit var repository: MediaRepositoryImpl

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, LaterboxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        storageManager = MediaStorageManager(context)
        repository = MediaRepositoryImpl(context, database.downloadedAssetDao(), database.downloadTaskDao(), storageManager)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deleteLink_withKeepFiles_preservesAssetRowAndPhysicalFile() = runBlocking {
        // Arrange: Insert link 1 and associate an asset
        val link1 = SavedLinkEntity(
            id = 101L,
            originalUrl = "https://example.com/item1.mp4",
            normalizedUrl = "https://example.com/item1.mp4",
            domain = "example.com",
            title = "Video 1",
            createdAt = System.currentTimeMillis()
        )
        database.savedLinkDao().insertLink(link1)

        val relativePath = "video/test_keep.mp4"
        val physicalFile = storageManager.resolveFile(relativePath)
        physicalFile.parentFile?.mkdirs()
        physicalFile.writeBytes("mp4_test_data".toByteArray())

        val asset1 = DownloadedAssetEntity(
            id = "asset_keep_1",
            savedLinkId = 101L,
            sourcePlatform = "Direct",
            originalSourceUrl = "https://example.com/item1.mp4",
            title = "Video 1",
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO.name,
            localPath = relativePath,
            exportedUri = null,
            fileStatus = AssetFileStatus.AVAILABLE.name,
            fileSize = physicalFile.length(),
            durationMs = null,
            width = null,
            height = null,
            qualityLabel = "720p",
            checksum = "sha256_video_1",
            createdAt = System.currentTimeMillis()
        )
        database.downloadedAssetDao().insertAsset(asset1)

        // Act: User selects "Keep Files" -> link is deleted, deleteFiles = false
        repository.deleteAssetsForLink(linkId = 101L, deleteFiles = false)
        database.savedLinkDao().deleteLink(101L)

        // Assert:
        // 1. Link is gone from saved_links
        val queriedLink = database.savedLinkDao().getLinkById(101L).first()
        assertNull("Link should be deleted", queriedLink)

        // 2. Asset row survives with savedLinkId = null due to ON DELETE SET NULL
        val remainingAsset = database.downloadedAssetDao().getEntityById("asset_keep_1")
        assertNotNull("Asset row must survive when user chooses keep files", remainingAsset)
        assertNull("savedLinkId must be set to null by database foreign key", remainingAsset?.savedLinkId)

        // 3. Physical file on disk is still intact
        assertTrue("Physical file must still exist on disk", physicalFile.exists())
    }

    @Test
    fun deleteLink_withDeleteFiles_removesAssetRowAndDeletesPhysicalFileWhenUnreferenced() = runBlocking {
        // Arrange: Insert link 2 and associate an asset
        val link2 = SavedLinkEntity(
            id = 102L,
            originalUrl = "https://example.com/item2.mp4",
            normalizedUrl = "https://example.com/item2.mp4",
            domain = "example.com",
            title = "Video 2",
            createdAt = System.currentTimeMillis()
        )
        database.savedLinkDao().insertLink(link2)

        val relativePath = "video/test_delete.mp4"
        val physicalFile = storageManager.resolveFile(relativePath)
        physicalFile.parentFile?.mkdirs()
        physicalFile.writeBytes("mp4_delete_test_data".toByteArray())

        val asset2 = DownloadedAssetEntity(
            id = "asset_del_2",
            savedLinkId = 102L,
            sourcePlatform = "Direct",
            originalSourceUrl = "https://example.com/item2.mp4",
            title = "Video 2",
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO.name,
            localPath = relativePath,
            exportedUri = null,
            fileStatus = AssetFileStatus.AVAILABLE.name,
            fileSize = physicalFile.length(),
            durationMs = null,
            width = null,
            height = null,
            qualityLabel = "720p",
            checksum = "sha256_video_2",
            createdAt = System.currentTimeMillis()
        )
        database.downloadedAssetDao().insertAsset(asset2)

        // Act: User selects "Delete Link & Files" -> deleteFiles = true
        repository.deleteAssetsForLink(linkId = 102L, deleteFiles = true)
        database.savedLinkDao().deleteLink(102L)

        // Assert:
        // 1. Link is deleted
        assertNull(database.savedLinkDao().getLinkById(102L).first())

        // 2. Asset row is deleted
        assertNull("Asset row should be deleted", database.downloadedAssetDao().getEntityById("asset_del_2"))

        // 3. Physical file on disk is deleted because no other asset references it
        assertFalse("Physical file should be deleted from disk", physicalFile.exists())
    }

    @Test
    fun deleteLink_withDeleteFiles_keepsPhysicalFileIfSharedByAnotherLink() = runBlocking {
        // Arrange: Two links sharing the same physical file (deduplication)
        val linkA = SavedLinkEntity(
            id = 201L,
            originalUrl = "https://example.com/sharedA",
            normalizedUrl = "https://example.com/sharedA",
            domain = "example.com",
            title = "Shared Link A",
            createdAt = System.currentTimeMillis()
        )
        val linkB = SavedLinkEntity(
            id = 202L,
            originalUrl = "https://example.com/sharedB",
            normalizedUrl = "https://example.com/sharedB",
            domain = "example.com",
            title = "Shared Link B",
            createdAt = System.currentTimeMillis()
        )
        database.savedLinkDao().insertLink(linkA)
        database.savedLinkDao().insertLink(linkB)

        val sharedPath = "image/shared_photo.jpg"
        val physicalFile = storageManager.resolveFile(sharedPath)
        physicalFile.parentFile?.mkdirs()
        physicalFile.writeBytes("shared_image_bytes".toByteArray())

        val assetA = DownloadedAssetEntity(
            id = "asset_A",
            savedLinkId = 201L,
            sourcePlatform = "Direct",
            originalSourceUrl = "https://example.com/sharedA",
            title = "Photo A",
            mimeType = "image/jpeg",
            mediaType = MediaType.IMAGE.name,
            localPath = sharedPath,
            exportedUri = null,
            fileStatus = AssetFileStatus.AVAILABLE.name,
            fileSize = physicalFile.length(),
            durationMs = null,
            width = null,
            height = null,
            qualityLabel = "JPEG",
            checksum = "sha256_shared",
            createdAt = System.currentTimeMillis()
        )
        val assetB = DownloadedAssetEntity(
            id = "asset_B",
            savedLinkId = 202L,
            sourcePlatform = "Direct",
            originalSourceUrl = "https://example.com/sharedB",
            title = "Photo B",
            mimeType = "image/jpeg",
            mediaType = MediaType.IMAGE.name,
            localPath = sharedPath,
            exportedUri = null,
            fileStatus = AssetFileStatus.AVAILABLE.name,
            fileSize = physicalFile.length(),
            durationMs = null,
            width = null,
            height = null,
            qualityLabel = "JPEG",
            checksum = "sha256_shared",
            createdAt = System.currentTimeMillis()
        )
        database.downloadedAssetDao().insertAsset(assetA)
        database.downloadedAssetDao().insertAsset(assetB)

        // Act: Delete Link A with deleteFiles = true
        repository.deleteAssetsForLink(linkId = 201L, deleteFiles = true)
        database.savedLinkDao().deleteLink(201L)

        // Assert:
        // Asset A is deleted, but Asset B still references sharedPath
        assertNull(database.downloadedAssetDao().getEntityById("asset_A"))
        assertNotNull(database.downloadedAssetDao().getEntityById("asset_B"))

        // Physical file MUST still exist because Asset B still references it!
        assertTrue("Physical file must survive because Link B still references it", physicalFile.exists())

        // Now delete Link B with deleteFiles = true
        repository.deleteAssetsForLink(linkId = 202L, deleteFiles = true)
        database.savedLinkDao().deleteLink(202L)

        // Assert: Now that no assets reference it, physical file is removed!
        assertNull(database.downloadedAssetDao().getEntityById("asset_B"))
        assertFalse("Physical file should now be deleted since no assets reference it", physicalFile.exists())
    }
}
