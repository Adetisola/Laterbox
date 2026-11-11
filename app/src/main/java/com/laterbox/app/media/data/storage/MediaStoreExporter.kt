package com.laterbox.app.media.data.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import com.laterbox.app.data.preferences.UserPreferencesRepository
import com.laterbox.app.media.domain.model.MediaAsset
import com.laterbox.app.media.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class MediaStoreExporter(
    private val context: Context,
    private val storageManager: MediaStorageManager,
    private val userPreferencesRepository: UserPreferencesRepository? = null
) {
    private val contentResolver: ContentResolver get() = context.contentResolver

    suspend fun exportAsset(asset: MediaAsset): Result<Uri> = withContext(Dispatchers.IO) {
        val sourceFile = storageManager.resolveFile(asset.localPath)
        if (!sourceFile.exists()) {
            return@withContext Result.failure(IllegalStateException("Local file not found at ${sourceFile.absolutePath}"))
        }

        val effectiveMimeType = normalizeMimeType(asset.mediaType, asset.mimeType)
        val ext = sourceFile.extension.takeIf { it.isNotBlank() } ?: when (asset.mediaType) {
            MediaType.IMAGE -> "jpg"
            MediaType.VIDEO -> "mp4"
            MediaType.AUDIO -> "m4a"
            MediaType.DOCUMENT -> "pdf"
            MediaType.OTHER -> "bin"
        }
        val exportFilename = "${sanitizeFilename(asset.title)}.$ext"

        // 1. Try user-configured custom export folder via SAF if present
        if (userPreferencesRepository != null) {
            try {
                val prefs = userPreferencesRepository.userPreferencesFlow.first()
                val customDirUriStr = prefs.customExportDirectoryUri
                if (!customDirUriStr.isNullOrBlank()) {
                    val customTreeUri = Uri.parse(customDirUriStr)
                    val treeDoc = DocumentFile.fromTreeUri(context, customTreeUri)
                    if (treeDoc != null && treeDoc.exists() && treeDoc.canWrite()) {
                        val newDocFile = treeDoc.createFile(effectiveMimeType, exportFilename)
                        if (newDocFile != null) {
                            contentResolver.openOutputStream(newDocFile.uri)?.use { out ->
                                FileInputStream(sourceFile).use { input ->
                                    copyStream(input, out)
                                }
                            } ?: throw IllegalStateException("Cannot open output stream for custom export file")
                            return@withContext Result.success(newDocFile.uri)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MediaStoreExporter", "Custom SAF export failed, falling back to MediaStore: ${e.message}")
            }
        }

        // 2. Fall back to standard Android MediaStore export
        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportApi29Plus(asset.mediaType, effectiveMimeType, sourceFile, exportFilename)
            } else {
                exportLegacyApi26To28(asset.mediaType, effectiveMimeType, sourceFile, exportFilename)
            }
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun normalizeMimeType(mediaType: MediaType, rawMime: String): String {
        return when {
            mediaType == MediaType.AUDIO && rawMime.startsWith("video/") -> "audio/mp4"
            mediaType == MediaType.AUDIO && rawMime == "application/octet-stream" -> "audio/mp4"
            mediaType == MediaType.VIDEO && rawMime.startsWith("audio/") -> "video/mp4"
            mediaType == MediaType.IMAGE && (rawMime.startsWith("video/") || rawMime.startsWith("audio/")) -> "image/jpeg"
            else -> rawMime
        }
    }

    private fun sanitizeFilename(name: String?): String {
        if (name.isNullOrBlank()) return "Laterbox_${System.currentTimeMillis()}"
        val sanitized = name.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_").trim()
        return if (sanitized.isBlank()) "Laterbox_${System.currentTimeMillis()}" else sanitized.take(80)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportApi29Plus(
        mediaType: MediaType,
        mimeType: String,
        sourceFile: File,
        filename: String
    ): Uri {
        val (collectionUri, relativeSubdir) = when (mediaType) {
            MediaType.IMAGE -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "${Environment.DIRECTORY_PICTURES}/Laterbox"
            MediaType.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "${Environment.DIRECTORY_MOVIES}/Laterbox"
            MediaType.AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "${Environment.DIRECTORY_MUSIC}/Laterbox"
            MediaType.DOCUMENT, MediaType.OTHER -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "${Environment.DIRECTORY_DOWNLOADS}/Laterbox"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
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
    private suspend fun exportLegacyApi26To28(
        mediaType: MediaType,
        mimeType: String,
        sourceFile: File,
        filename: String
    ): Uri = withContext(Dispatchers.IO) {
        val targetDirName = when (mediaType) {
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

        var scannedUri: Uri? = null
        MediaScannerConnection.scanFile(
            context,
            arrayOf(destination.absolutePath),
            arrayOf(mimeType)
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
