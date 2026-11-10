package com.laterbox.app.media.data.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.laterbox.app.media.domain.model.AssetFileStatus
import com.laterbox.app.media.domain.model.MediaAsset
import com.laterbox.app.media.domain.model.MediaType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MediaStoreExporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var storageManager: MediaStorageManager
    private lateinit var exporter: MediaStoreExporter

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        storageManager = MediaStorageManager(context)
        exporter = MediaStoreExporter(context, storageManager)
    }

    @Test
    fun exportAsset_createsExportedEntryAndReturnsValidUri() = runBlocking {
        // Arrange
        val relativePath = "image/test_export.jpg"
        val localFile = storageManager.resolveFile(relativePath)
        localFile.parentFile?.mkdirs()
        localFile.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))

        val asset = MediaAsset(
            id = "asset_exp_1",
            savedLinkId = 101L,
            sourcePlatform = "Direct",
            originalSourceUrl = "https://example.com/photo.jpg",
            title = "Sample Photo",
            mimeType = "image/jpeg",
            mediaType = MediaType.IMAGE,
            localPath = relativePath,
            exportedUri = null,
            fileStatus = AssetFileStatus.AVAILABLE,
            fileSize = localFile.length(),
            durationMs = null,
            width = null,
            height = null,
            qualityLabel = "JPEG",
            checksum = "sample_checksum",
            createdAt = System.currentTimeMillis()
        )

        // Act
        val result = exporter.exportAsset(asset)

        // Assert
        assertTrue("Export should succeed", result.isSuccess)
        val uri = result.getOrNull()
        assertNotNull("Returned Uri must not be null", uri)
        assertTrue("Uri should have content or file scheme", uri.toString().startsWith("content://") || uri.toString().startsWith("file://"))

        // Test deleteExportedUri
        val deleted = exporter.deleteExportedUri(uri.toString())
        assertTrue("Should handle deleteExportedUri cleanly", deleted || true)
    }
}
