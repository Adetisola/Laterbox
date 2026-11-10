package com.laterbox.app.media.data.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.laterbox.app.media.domain.model.MediaAsset
import com.laterbox.app.media.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class MediaStoreExporter(
    private val context: Context,
    private val storageManager: MediaStorageManager
) {
    private val contentResolver: ContentResolver get() = context.contentResolver

    suspend fun exportAsset(asset: MediaAsset): Result<Uri> = withContext(Dispatchers.IO) {
        val sourceFile = storageManager.resolveFile(asset.localPath)
        if (!sourceFile.exists()) {
            return@withContext Result.failure(IllegalStateException("Local file not found at ${sourceFile.absolutePath}"))
        }

        val filename = sourceFile.name

        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportApi29Plus(asset, sourceFile, filename)
            } else {
                exportLegacyApi26To28(asset, sourceFile, filename)
            }
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun exportApi29Plus(asset: MediaAsset, sourceFile: File, filename: String): Uri {
        val (collectionUri, relativeSubdir) = when (asset.mediaType) {
            MediaType.IMAGE -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "${Environment.DIRECTORY_PICTURES}/Laterbox"
            MediaType.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "${Environment.DIRECTORY_MOVIES}/Laterbox"
            MediaType.AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "${Environment.DIRECTORY_MUSIC}/Laterbox"
            MediaType.DOCUMENT, MediaType.OTHER -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "${Environment.DIRECTORY_DOWNLOADS}/Laterbox"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, asset.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativeSubdir)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = contentResolver.insert(collectionUri, values)
            ?: throw IllegalStateException("Failed to create MediaStore entry in $collectionUri")

        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    copyStream(input, out)
                }
            } ?: throw IllegalStateException("Failed to open output stream for $uri")

            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            return uri
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun exportLegacyApi26To28(asset: MediaAsset, sourceFile: File, filename: String): Uri = withContext(Dispatchers.IO) {
        val targetDirName = when (asset.mediaType) {
            MediaType.IMAGE -> Environment.DIRECTORY_PICTURES
            MediaType.VIDEO -> Environment.DIRECTORY_MOVIES
            MediaType.AUDIO -> Environment.DIRECTORY_MUSIC
            MediaType.DOCUMENT, MediaType.OTHER -> Environment.DIRECTORY_DOWNLOADS
        }

        val publicDir = File(Environment.getExternalStoragePublicDirectory(targetDirName), "Laterbox")
        if (!publicDir.exists()) {
            publicDir.mkdirs()
        }

        var destination = File(publicDir, filename)
        if (destination.exists()) {
            val base = filename.substringBeforeLast(".")
            val ext = filename.substringAfterLast(".", "")
            val extSuffix = if (ext.isNotEmpty()) ".$ext" else ""
            destination = File(publicDir, "${base}_${System.currentTimeMillis()}$extSuffix")
        }

        FileInputStream(sourceFile).use { input ->
            FileOutputStream(destination).use { output ->
                copyStream(input, output)
            }
        }

        // Trigger media scanner so it registers in MediaStore and returns a content URI
        var scannedUri: Uri? = null
        MediaScannerConnection.scanFile(
            context,
            arrayOf(destination.absolutePath),
            arrayOf(asset.mimeType)
        ) { _, uri ->
            scannedUri = uri
        }

        scannedUri ?: Uri.fromFile(destination)
    }

    suspend fun deleteExportedUri(uriString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "content") {
                val deletedRows = contentResolver.delete(uri, null, null)
                deletedRows > 0
            } else if (uri.scheme == "file") {
                val file = File(uri.path ?: return@withContext false)
                if (file.exists()) file.delete() else true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(64 * 1024)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
        }
        output.flush()
    }
}
