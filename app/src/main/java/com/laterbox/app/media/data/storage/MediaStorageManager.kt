package com.laterbox.app.media.data.storage

import android.content.Context
import android.os.StatFs
import android.webkit.MimeTypeMap
import com.laterbox.app.media.domain.model.MediaType
import java.io.File
import java.io.FileInputStream
import java.io.IOException

data class FinalizedStorageResult(
    val relativePath: String,
    val checksum: String,
    val fileSize: Long,
    val mimeType: String,
    val isDuplicateReused: Boolean
)

class MediaStorageManager(private val context: Context) {

    private val mediaBaseDir: File
        get() = File(context.filesDir, "media").apply { if (!exists()) mkdirs() }

    private val partsBaseDir: File
        get() = File(context.cacheDir, "media_parts").apply { if (!exists()) mkdirs() }

    fun getPartFile(taskId: String): File {
        return File(partsBaseDir, "$taskId.part")
    }

    fun deletePartFile(taskId: String): Boolean {
        val file = getPartFile(taskId)
        return if (file.exists()) file.delete() else true
    }

    fun resolveFile(relativePath: String): File {
        return File(mediaBaseDir, relativePath)
    }

    /**
     * Checks available storage space against estimated bytes + 50 MB safety margin.
     * If estimatedBytes is null, requires at least 50 MB safety margin.
     */
    fun hasSufficientSpace(estimatedBytes: Long?): Boolean {
        return try {
            val stat = StatFs(context.filesDir.path)
            val available = stat.availableBytes
            val safetyMargin = 50L * 1024 * 1024 // 50 MB
            val required = (estimatedBytes ?: 0L) + safetyMargin
            available >= required
        } catch (_: Exception) {
            true // Safeguard if StatFs fails
        }
    }

    /**
     * Finalizes a completed .part download:
     * 1. Computes SHA-256 of .part
     * 2. Checks if an existing file with the same checksum exists on disk (deduplication)
     * 3. Sniffs MIME type & extension
     * 4. Atomically moves .part into filesDir/media/<type>/<sha256>.<ext>
     */
    fun finalizeDownload(
        taskId: String,
        mediaType: MediaType,
        suggestedExt: String? = null,
        existingDuplicatePath: String? = null
    ): FinalizedStorageResult {
        val partFile = getPartFile(taskId)
        if (!partFile.exists() || partFile.length() == 0L) {
            throw IOException("Part file for task $taskId does not exist or is empty")
        }

        val checksum = ChecksumUtil.calculateSha256(partFile)
        val fileSize = partFile.length()
        val mimeType = sniffMimeType(partFile, suggestedExt, mediaType)

        // Duplicate deduplication check: if caller provided existing path and that file exists
        if (existingDuplicatePath != null) {
            val existingFile = resolveFile(existingDuplicatePath)
            if (existingFile.exists() && existingFile.length() == fileSize) {
                partFile.delete()
                return FinalizedStorageResult(
                    relativePath = existingDuplicatePath,
                    checksum = checksum,
                    fileSize = fileSize,
                    mimeType = mimeType,
                    isDuplicateReused = true
                )
            }
        }

        val ext = resolveExtension(mimeType, suggestedExt, mediaType)
        val typeDir = File(mediaBaseDir, mediaType.name.lowercase()).apply { if (!exists()) mkdirs() }
        val targetFile = File(typeDir, "$checksum.$ext")
        val relativePath = "${mediaType.name.lowercase()}/$checksum.$ext"

        if (targetFile.exists() && targetFile.length() == fileSize) {
            partFile.delete()
            return FinalizedStorageResult(
                relativePath = relativePath,
                checksum = checksum,
                fileSize = fileSize,
                mimeType = mimeType,
                isDuplicateReused = true
            )
        }

        // Move atomically
        val success = partFile.renameTo(targetFile)
        if (!success) {
            // Fallback to copy & delete
            partFile.inputStream().use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            partFile.delete()
        }

        return FinalizedStorageResult(
            relativePath = relativePath,
            checksum = checksum,
            fileSize = fileSize,
            mimeType = mimeType,
            isDuplicateReused = false
        )
    }

    /**
     * Reference-counted physical file deletion:
     * Only deletes the file from disk if no other downloaded_asset row references the same localPath.
     * @param activeRefCount total number of asset rows pointing to this localPath (including the one being deleted)
     * @return true if the physical file was deleted, false if skipped because it is shared
     */
    fun deletePhysicalFileIfUnreferenced(relativePath: String, activeRefCount: Int): Boolean {
        if (activeRefCount <= 1) {
            val file = resolveFile(relativePath)
            if (file.exists()) {
                return file.delete()
            }
        }
        return false
    }

    fun deleteThumbnailFile(thumbnailRelativePath: String?): Boolean {
        if (thumbnailRelativePath.isNullOrBlank()) return false
        val thumbFile = File(context.filesDir, thumbnailRelativePath)
        return if (thumbFile.exists()) thumbFile.delete() else false
    }

    /**
     * Cleans up partial .part files that do not correspond to any active or paused task.
     */
    fun sweepOrphanPartFiles(activeTaskIds: Set<String>): Int {
        var deleted = 0
        val files = partsBaseDir.listFiles() ?: return 0
        for (file in files) {
            if (file.isFile && file.name.endsWith(".part")) {
                val taskId = file.name.removeSuffix(".part")
                if (taskId !in activeTaskIds) {
                    if (file.delete()) deleted++
                }
            }
        }
        return deleted
    }

    internal fun resolveExtension(mimeType: String, suggestedExt: String?, mediaType: MediaType? = null): String {
        val cleanSuggested = suggestedExt?.trim()?.removePrefix(".")?.lowercase()
        if (!cleanSuggested.isNullOrBlank() && cleanSuggested.length in 2..5 && !cleanSuggested.contains("[") && !cleanSuggested.contains("/")) {
            return cleanSuggested
        }
        val fromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        if (!fromMime.isNullOrBlank()) {
            return fromMime
        }
        return when (mimeType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "video/mp4" -> if (mediaType == MediaType.AUDIO) "m4a" else "mp4"
            "video/webm" -> "webm"
            "audio/webm" -> "webm"
            "audio/ogg" -> "ogg"
            "audio/flac" -> "flac"
            "audio/mp4", "audio/m4a" -> "m4a"
            "audio/mpeg" -> "mp3"
            "application/pdf" -> "pdf"
            else -> if (mediaType == MediaType.AUDIO) "m4a" else "bin"
        }
    }

    fun sniffMimeType(file: File, fallbackExt: String? = null, mediaType: MediaType? = null): String {
        try {
            val header = ByteArray(32)
            val bytesRead = FileInputStream(file).use { it.read(header) }
            if (bytesRead >= 4) {
                // JPEG: FF D8 FF
                if (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()) {
                    return "image/jpeg"
                }
                // PNG: 89 50 4E 47
                if (header[0] == 0x89.toByte() && header[1] == 0x50.toByte() && header[2] == 0x4E.toByte() && header[3] == 0x47.toByte()) {
                    return "image/png"
                }
                // GIF: 47 49 46 38
                if (header[0] == 0x47.toByte() && header[1] == 0x49.toByte() && header[2] == 0x46.toByte() && header[3] == 0x38.toByte()) {
                    return "image/gif"
                }
                // PDF: 25 50 44 46 (%PDF)
                if (header[0] == 0x25.toByte() && header[1] == 0x50.toByte() && header[2] == 0x44.toByte() && header[3] == 0x46.toByte()) {
                    return "application/pdf"
                }
                // WEBP: RIFF....WEBP
                if (bytesRead >= 12 &&
                    header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() && header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte() &&
                    header[8] == 'W'.code.toByte() && header[9] == 'E'.code.toByte() && header[10] == 'B'.code.toByte() && header[11] == 'P'.code.toByte()
                ) {
                    return "image/webp"
                }
                // MP4 / M4A: offset 4 ftyp
                if (bytesRead >= 8 &&
                    header[4] == 'f'.code.toByte() && header[5] == 't'.code.toByte() && header[6] == 'y'.code.toByte() && header[7] == 'p'.code.toByte()
                ) {
                    val brand = if (bytesRead >= 12) String(header, 8, 4, Charsets.US_ASCII) else ""
                    return if (brand.startsWith("M4A") || brand.startsWith("m4a") || mediaType == MediaType.AUDIO) "audio/mp4" else "video/mp4"
                }
                // Matroska / WebM: 1A 45 DF A3
                if (header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() && header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte()) {
                    return if (mediaType == MediaType.AUDIO) "audio/webm" else "video/webm"
                }
                // MP3: ID3
                if (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
                    return "audio/mpeg"
                }
                // OGG: OggS
                if (header[0] == 'O'.code.toByte() && header[1] == 'g'.code.toByte() && header[2] == 'g'.code.toByte() && header[3] == 'S'.code.toByte()) {
                    return "audio/ogg"
                }
                // FLAC: fLaC
                if (header[0] == 'f'.code.toByte() && header[1] == 'L'.code.toByte() && header[2] == 'a'.code.toByte() && header[3] == 'C'.code.toByte()) {
                    return "audio/flac"
                }
            }
        } catch (_: Exception) {
            // Fall through to extension
        }

        if (mediaType == MediaType.AUDIO) {
            return "audio/mp4"
        }

        if (!fallbackExt.isNullOrBlank() && !fallbackExt.contains("[") && !fallbackExt.contains("/")) {
            val ext = fallbackExt.removePrefix(".").lowercase()
            val fromExt = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            if (!fromExt.isNullOrBlank()) return fromExt
        }

        return "application/octet-stream"
    }
}
