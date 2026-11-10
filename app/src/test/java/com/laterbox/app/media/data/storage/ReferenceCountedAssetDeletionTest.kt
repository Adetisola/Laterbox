package com.laterbox.app.media.data.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.laterbox.app.data.local.LaterboxDatabase
import com.laterbox.app.data.local.entity.SavedLinkEntity
import com.laterbox.app.media.data.db.entity.DownloadedAssetEntity
import com.laterbox.app.media.data.repository.MediaRepositoryImpl
import com.laterbox.app.media.domain.model.MediaType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReferenceCountedAssetDeletionTest {

    private lateinit var context: Context
    private lateinit var db: LaterboxDatabase
    private lateinit var storageManager: MediaStorageManager
    private lateinit var repository: MediaRepositoryImpl

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, LaterboxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        storageManager = MediaStorageManager(context)
        repository = MediaRepositoryImpl(
            context = context,
            assetDao = db.downloadedAssetDao(),
            taskDao = db.downloadTaskDao(),
            storageManager = storageManager
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun deleteAsset_onlyDeletesPhysicalFileWhenNoOtherAssetReferencesLocalPath() = runTest {
        // 1. Create a physical file on disk in media storage
        val relativePath = "video/shared_file_123.mp4"
        val physicalFile = storageManager.resolveFile(relativePath)
        physicalFile.parentFile?.mkdirs()
        physicalFile.writeText("Shared media content bytes")
        assertTrue("Physical file must exist on disk before test", physicalFile.exists())

        // 2. Insert two saved links
        val linkDao = db.savedLinkDao()
        val link1Id = linkDao.insertLink(
            SavedLinkEntity(
                originalUrl = "https://example.com/video1",
                normalizedUrl = "https://example.com/video1",
                title = "Video One",
                domain = "example.com"
            )
        )
        val link2Id = linkDao.insertLink(
            SavedLinkEntity(
                originalUrl = "https://example.com/video2",
                normalizedUrl = "https://example.com/video2",
                title = "Video Two",
                domain = "example.com"
            )
        )

        // 3. Insert TWO downloaded_asset rows that share the same localPath (deduplication)
        val asset1 = DownloadedAssetEntity(
            id = "asset-one",
            savedLinkId = link1Id,
            sourcePlatform = "Direct",
            originalSourceUrl = "https://example.com/video1",
            title = "Video One",
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO.name,
            localPath = relativePath,
            fileSize = physicalFile.length(),
            checksum = "shared_sha256",
            createdAt = 1000L
        )
        val asset2 = DownloadedAssetEntity(
            id = "asset-two",
            savedLinkId = link2Id,
            sourcePlatform = "Direct",
            originalSourceUrl = "https://example.com/video2",
            title = "Video Two",
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO.name,
            localPath = relativePath,
            fileSize = physicalFile.length(),
            checksum = "shared_sha256",
            createdAt = 2000L
        )

        db.downloadedAssetDao().insertAsset(asset1)
        db.downloadedAssetDao().insertAsset(asset2)

        assertEquals(2, db.downloadedAssetDao().countByLocalPath(relativePath))

        // 4. Delete asset1 -> asset row is removed, but physical file MUST NOT be deleted because asset2 still references it!
        repository.deleteAsset(asset1.id)

        assertEquals(1, db.downloadedAssetDao().countByLocalPath(relativePath))
        val remainingAssets = repository.getAllAssets().first()
        assertEquals(1, remainingAssets.size)
        assertEquals("asset-two", remainingAssets[0].id)
        assertTrue(
            "Physical file MUST still exist on disk after deleting one of the shared references",
            physicalFile.exists()
        )

        // 5. Delete asset2 -> no more references exist, so physical file MUST now be deleted
        repository.deleteAsset(asset2.id)

        assertEquals(0, db.downloadedAssetDao().countByLocalPath(relativePath))
        assertEquals(0, repository.getAllAssets().first().size)
        assertFalse(
            "Physical file MUST be deleted from disk when the last referencing asset is removed",
            physicalFile.exists()
        )
    }

    @Test
    fun deleteAssetsForLink_withDeleteFiles_honorsReferenceCounting() = runTest {
        val relativePath = "image/common_avatar.jpg"
        val physicalFile = storageManager.resolveFile(relativePath)
        physicalFile.parentFile?.mkdirs()
        physicalFile.writeText("Shared avatar bytes")
        assertTrue(physicalFile.exists())

        val linkDao = db.savedLinkDao()
        val link1Id = linkDao.insertLink(
            SavedLinkEntity(
                originalUrl = "https://example.com/post1",
                normalizedUrl = "https://example.com/post1",
                title = "Post 1",
                domain = "example.com"
            )
        )
        val link2Id = linkDao.insertLink(
            SavedLinkEntity(
                originalUrl = "https://example.com/post2",
                normalizedUrl = "https://example.com/post2",
                title = "Post 2",
                domain = "example.com"
            )
        )

        val asset1 = DownloadedAssetEntity(
            id = "avatar-1",
            savedLinkId = link1Id,
            sourcePlatform = "Direct",
            originalSourceUrl = "https://example.com/avatar.jpg",
            title = "Avatar 1",
            mimeType = "image/jpeg",
            mediaType = MediaType.IMAGE.name,
            localPath = relativePath,
            fileSize = physicalFile.length(),
            checksum = "avatar_sha256",
            createdAt = 1000L
        )
        val asset2 = DownloadedAssetEntity(
            id = "avatar-2",
            savedLinkId = link2Id,
            sourcePlatform = "Direct",
            originalSourceUrl = "https://example.com/avatar.jpg",
            title = "Avatar 2",
            mimeType = "image/jpeg",
            mediaType = MediaType.IMAGE.name,
            localPath = relativePath,
            fileSize = physicalFile.length(),
            checksum = "avatar_sha256",
            createdAt = 2000L
        )

        db.downloadedAssetDao().insertAsset(asset1)
        db.downloadedAssetDao().insertAsset(asset2)

        // Delete link1 with deleteFiles = true
        repository.deleteAssetsForLink(link1Id, deleteFiles = true)

        // Physical file should remain because link2 still has asset2 referencing it
        assertTrue("Physical file must still exist after deleting link 1", physicalFile.exists())

        // Delete link2 with deleteFiles = true
        repository.deleteAssetsForLink(link2Id, deleteFiles = true)

        // Now physical file must be deleted
        assertFalse("Physical file must be deleted after deleting link 2", physicalFile.exists())
    }
}
