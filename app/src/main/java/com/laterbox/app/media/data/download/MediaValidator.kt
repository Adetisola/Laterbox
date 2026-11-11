package com.laterbox.app.media.data.download

import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Log
import com.laterbox.app.media.domain.model.FailureCode
import com.laterbox.app.media.domain.model.MediaType
import java.io.File
import java.io.FileInputStream

sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Invalid(val reason: String, val failureCode: FailureCode) : ValidationResult()
}

object MediaValidator {
    private const val TAG = "MediaValidator"

    fun validate(
        file: File,
        expectedType: MediaType,
        expectedHasAudio: Boolean = false
    ): ValidationResult {
        if (!file.exists() || file.length() == 0L) {
            return ValidationResult.Invalid("Downloaded file is empty or missing", FailureCode.CORRUPT_MEDIA)
        }

        val headerSize = 512.coerceAtMost(file.length().toInt())
        val header = ByteArray(headerSize)
        try {
            FileInputStream(file).use { it.read(header) }
        } catch (e: Exception) {
            return ValidationResult.Invalid("Could not read file header: ${e.message}", FailureCode.CORRUPT_MEDIA)
        }

        val headerStr = String(header, Charsets.UTF_8).trimStart()
        if (headerStr.startsWith("<!DOCTYPE", ignoreCase = true) ||
            headerStr.startsWith("<html", ignoreCase = true) ||
            headerStr.startsWith("<?xml", ignoreCase = true)
        ) {
            return ValidationResult.Invalid("Downloaded file is an HTML or XML web document instead of media", FailureCode.CORRUPT_MEDIA)
        }

        if (headerStr.startsWith("{") && (headerStr.contains("\"error\"", ignoreCase = true) || headerStr.contains("\"message\"", ignoreCase = true))) {
            return ValidationResult.Invalid("Downloaded file contains an API error payload instead of media", FailureCode.CORRUPT_MEDIA)
        }

        return when (expectedType) {
            MediaType.IMAGE -> validateImage(file, header)
            MediaType.AUDIO -> validateAudio(file, header)
            MediaType.VIDEO -> validateVideo(file, header, expectedHasAudio)
            MediaType.DOCUMENT, MediaType.OTHER -> ValidationResult.Valid
        }
    }

    private fun validateImage(file: File, header: ByteArray): ValidationResult {
        val matchesMagic = isJpeg(header) || isPng(header) || isGif(header) || isWebp(header) || isBmp(header)
        if (matchesMagic) {
            return ValidationResult.Valid
        }

        return ValidationResult.Invalid(
            "File does not match recognized image format magic bytes (size: ${file.length()} bytes)",
            FailureCode.CORRUPT_MEDIA
        )
    }

    private fun validateAudio(file: File, header: ByteArray): ValidationResult {
        // Try MediaMetadataRetriever first
        var retrieverChecked = false
        var hasAudioTrack = false

        try {
            if (!Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    retrieverChecked = true
                    val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                    hasAudioTrack = (hasAudio == "yes")
                } finally {
                    try {
                        retriever.release()
                    } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {
            // MediaMetadataRetriever unmocked in pure host JVM unit tests
        }

        if (retrieverChecked && hasAudioTrack) {
            return ValidationResult.Valid
        }

        val matchesMagic = isMp3(header) || isM4aOrAac(header) || isOgg(header) || isFlac(header) || isWav(header)
        if (matchesMagic) {
            return ValidationResult.Valid
        }

        return ValidationResult.Invalid(
            "File does not contain a valid audio stream or recognizable audio format header",
            FailureCode.CORRUPT_MEDIA
        )
    }

    private fun validateVideo(file: File, header: ByteArray, expectedHasAudio: Boolean): ValidationResult {
        var retrieverChecked = false
        var hasVideoTrack = false
        var hasAudioTrack = false
        var width = 0
        var height = 0

        try {
            if (!Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    retrieverChecked = true
                    val videoMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
                    hasVideoTrack = (videoMeta == "yes")
                    val audioMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                    hasAudioTrack = (audioMeta == "yes")
                    width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                } finally {
                    try {
                        retriever.release()
                    } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {
            // MediaMetadataRetriever unmocked in pure host JVM unit tests
        }

        if (retrieverChecked) {
            if (!hasVideoTrack || width <= 0 || height <= 0) {
                return ValidationResult.Invalid(
                    "File has no detectable video track or zero dimensions (hasVideo=$hasVideoTrack, w=$width, h=$height)",
                    FailureCode.CORRUPT_MEDIA
                )
            }
            if (expectedHasAudio && !hasAudioTrack) {
                return ValidationResult.Invalid(
                    "Downloaded video stream was expected to contain audio but has no audio track",
                    FailureCode.CORRUPT_MEDIA
                )
            }
            return ValidationResult.Valid
        }

        // Magic bytes fallback (e.g. Robolectric test or unsupported container format)
        val matchesMagic = isMp4(header) || isWebm(header) || isAvi(header) || isTs(header)
        if (matchesMagic) {
            return ValidationResult.Valid
        }

        return ValidationResult.Invalid(
            "File does not match recognized video container format headers",
            FailureCode.CORRUPT_MEDIA
        )
    }

    private fun isJpeg(h: ByteArray): Boolean {
        return h.size >= 3 &&
            h[0] == 0xFF.toByte() &&
            h[1] == 0xD8.toByte() &&
            h[2] == 0xFF.toByte()
    }

    private fun isPng(h: ByteArray): Boolean {
        return h.size >= 4 &&
            h[0] == 0x89.toByte() &&
            h[1] == 0x50.toByte() && // 'P'
            h[2] == 0x4E.toByte() && // 'N'
            h[3] == 0x47.toByte()    // 'G'
    }

    private fun isGif(h: ByteArray): Boolean {
        if (h.size < 6) return false
        val s = String(h, 0, 6, Charsets.US_ASCII)
        return s.startsWith("GIF87a") || s.startsWith("GIF89a")
    }

    private fun isWebp(h: ByteArray): Boolean {
        if (h.size < 12) return false
        val riff = String(h, 0, 4, Charsets.US_ASCII)
        val webp = String(h, 8, 4, Charsets.US_ASCII)
        return riff == "RIFF" && webp == "WEBP"
    }

    private fun isBmp(h: ByteArray): Boolean {
        return h.size >= 2 && h[0] == 0x42.toByte() && h[1] == 0x4D.toByte()
    }

    private fun isMp3(h: ByteArray): Boolean {
        if (h.size >= 3 && h[0] == 'I'.code.toByte() && h[1] == 'D'.code.toByte() && h[2] == '3'.code.toByte()) {
            return true
        }
        if (h.size >= 2) {
            val b0 = h[0].toInt() and 0xFF
            val b1 = h[1].toInt() and 0xFF
            if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) return true
        }
        return false
    }

    private fun isM4aOrAac(h: ByteArray): Boolean {
        if (h.size >= 8) {
            val tag = String(h, 4, 4, Charsets.US_ASCII)
            if (tag == "ftyp" || tag == "moov") return true
        }
        return false
    }

    private fun isOgg(h: ByteArray): Boolean {
        return h.size >= 4 && String(h, 0, 4, Charsets.US_ASCII) == "OggS"
    }

    private fun isFlac(h: ByteArray): Boolean {
        return h.size >= 4 && String(h, 0, 4, Charsets.US_ASCII) == "fLaC"
    }

    private fun isWav(h: ByteArray): Boolean {
        if (h.size < 12) return false
        val riff = String(h, 0, 4, Charsets.US_ASCII)
        val wave = String(h, 8, 4, Charsets.US_ASCII)
        return riff == "RIFF" && wave == "WAVE"
    }

    private fun isMp4(h: ByteArray): Boolean {
        if (h.size >= 8) {
            val tag = String(h, 4, 4, Charsets.US_ASCII)
            if (tag == "ftyp" || tag == "moov") return true
        }
        return false
    }

    private fun isWebm(h: ByteArray): Boolean {
        return h.size >= 4 &&
            h[0] == 0x1A.toByte() &&
            h[1] == 0x45.toByte() &&
            h[2] == 0xDF.toByte() &&
            h[3] == 0xA3.toByte()
    }

    private fun isAvi(h: ByteArray): Boolean {
        if (h.size < 12) return false
        val riff = String(h, 0, 4, Charsets.US_ASCII)
        val avi = String(h, 8, 4, Charsets.US_ASCII)
        return riff == "RIFF" && avi.startsWith("AVI")
    }

    private fun isTs(h: ByteArray): Boolean {
        return h.isNotEmpty() && h[0] == 0x47.toByte()
    }
}
