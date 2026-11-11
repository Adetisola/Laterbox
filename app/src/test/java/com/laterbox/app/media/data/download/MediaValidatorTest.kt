package com.laterbox.app.media.data.download

import com.laterbox.app.media.domain.model.FailureCode
import com.laterbox.app.media.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaValidatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun validate_rejectsEmptyOrMissingFile() {
        val nonExistent = File(tempFolder.root, "does_not_exist.mp4")
        val result = MediaValidator.validate(nonExistent, MediaType.VIDEO)
        assertTrue(result is ValidationResult.Invalid)
        assertEquals(FailureCode.CORRUPT_MEDIA, (result as ValidationResult.Invalid).failureCode)

        val emptyFile = tempFolder.newFile("empty.mp4")
        val emptyResult = MediaValidator.validate(emptyFile, MediaType.VIDEO)
        assertTrue(emptyResult is ValidationResult.Invalid)
    }

    @Test
    fun validate_rejectsHtmlWebPage() {
        val htmlFile = tempFolder.newFile("error.html").apply {
            writeText("<!DOCTYPE html><html><body>Error 404 Not Found</body></html>")
        }
        val result = MediaValidator.validate(htmlFile, MediaType.VIDEO)
        assertTrue(result is ValidationResult.Invalid)
        assertTrue((result as ValidationResult.Invalid).reason.contains("HTML or XML web document"))
    }

    @Test
    fun validate_rejectsJsonErrorPayload() {
        val jsonFile = tempFolder.newFile("error.json").apply {
            writeText("""{"error": "Video unavailable", "code": 403}""")
        }
        val result = MediaValidator.validate(jsonFile, MediaType.VIDEO)
        assertTrue(result is ValidationResult.Invalid)
        assertTrue((result as ValidationResult.Invalid).reason.contains("API error payload"))
    }

    @Test
    fun validate_acceptsValidJpegImage() {
        val jpgFile = tempFolder.newFile("photo.jpg").apply {
            // Write JPEG SOI header: FF D8 FF E0
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10))
        }
        val result = MediaValidator.validate(jpgFile, MediaType.IMAGE)
        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun validate_acceptsValidPngImage() {
        val pngFile = tempFolder.newFile("photo.png").apply {
            // Write PNG signature: 89 50 4E 47
            writeBytes(byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D, 0x0A, 0x1A, 0x0A))
        }
        val result = MediaValidator.validate(pngFile, MediaType.IMAGE)
        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun validate_acceptsValidMp4VideoMagicBytes() {
        val mp4File = tempFolder.newFile("video.mp4").apply {
            // Write MP4 box header: size 0x00 0x00 0x00 0x20, "ftyp", "isom"
            val bytes = byteArrayOf(
                0x00, 0x00, 0x00, 0x20,
                'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
                'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte()
            )
            writeBytes(bytes)
        }
        val result = MediaValidator.validate(mp4File, MediaType.VIDEO)
        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun validate_acceptsValidMp3AudioMagicBytes() {
        val mp3File = tempFolder.newFile("audio.mp3").apply {
            // Write MP3 ID3v2 tag header: "ID3"
            val bytes = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 0x04, 0x00, 0x00)
            writeBytes(bytes)
        }
        val result = MediaValidator.validate(mp3File, MediaType.AUDIO)
        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun validate_rejectsRandomGarbageDataForVideo() {
        val badFile = tempFolder.newFile("garbage.bin").apply {
            writeBytes(byteArrayOf(0x12, 0x34, 0x56, 0x78, 0x9A.toByte(), 0xBC.toByte()))
        }
        val result = MediaValidator.validate(badFile, MediaType.VIDEO)
        assertTrue(result is ValidationResult.Invalid)
        assertEquals(FailureCode.CORRUPT_MEDIA, (result as ValidationResult.Invalid).failureCode)
    }
}
