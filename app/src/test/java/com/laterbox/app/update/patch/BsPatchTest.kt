package com.laterbox.app.update.patch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

class BsPatchTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(16384)
            var read: Int
            while (input.read(buf).also { read = it } != -1) {
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Test
    fun decodeOffT_positiveAndNegativeValues() {
        // 0
        val zeroBuf = ByteArray(8)
        assertEquals(0L, BsPatch.decodeOffT(zeroBuf, 0))

        // 123456789
        val posBuf = ByteArray(8)
        val posVal = 123456789L
        for (i in 0..7) {
            posBuf[i] = ((posVal ushr (i * 8)) and 0xFF).toByte()
        }
        assertEquals(posVal, BsPatch.decodeOffT(posBuf, 0))

        // -500: In sign-magnitude, 500 is 0x01F4, high bit of byte 7 set to 0x80
        val negBuf = ByteArray(8)
        val mag = 500L
        for (i in 0..6) {
            negBuf[i] = ((mag ushr (i * 8)) and 0xFF).toByte()
        }
        negBuf[7] = (((mag ushr 56) and 0x7F) or 0x80).toByte()
        assertEquals(-500L, BsPatch.decodeOffT(negBuf, 0))
    }

    @Test
    fun applyPatch_throwsOnInvalidMagic() {
        val oldFile = tempFolder.newFile("old.bin").apply { writeText("Hello Old") }
        val patchFile = tempFolder.newFile("invalid.patch").apply {
            writeBytes("NOT_BSDIFF_INVALID_HEADER_32_BYTES!".toByteArray())
        }
        val newFile = File(tempFolder.root, "out.bin")

        try {
            BsPatch.applyPatch(oldFile, patchFile, newFile)
            fail("Expected IOException on invalid magic")
        } catch (e: IOException) {
            assertTrue("Expected invalid magic error message", e.message?.contains("Invalid patch magic header") == true)
        }
        assertFalse("New file should not exist after failure", newFile.exists())
    }

    @Test
    fun applyPatch_throwsOnTruncatedPatchHeader() {
        val oldFile = tempFolder.newFile("old2.bin").apply { writeText("Hello Old") }
        val patchFile = tempFolder.newFile("truncated.patch").apply { writeBytes("SHORT".toByteArray()) }
        val newFile = File(tempFolder.root, "out2.bin")

        try {
            BsPatch.applyPatch(oldFile, patchFile, newFile)
            fail("Expected IOException on truncated patch")
        } catch (e: IOException) {
            // EOFException is a subclass of IOException
            assertTrue(e is java.io.EOFException || e.message?.contains("EOF") == true)
        }
        assertFalse("New file should not exist after failure", newFile.exists())
    }

    @Test
    fun applyPatch_reconstructsRealApkByteForByte_whenBenchFilesPresent() {
        val oldApk = File("/tmp/apk-bench/v33/Laterbox-ARM32.apk")
        val newApk = File("/tmp/apk-bench/v34/Laterbox-ARM32.apk")
        val patch = File("/tmp/apk-bench/arm32.patch")

        if (!oldApk.exists() || !newApk.exists() || !patch.exists()) {
            // Skip if benchmark files are absent (e.g. standard clean test runner)
            return
        }

        val reconstructed = tempFolder.newFile("reconstructed-Laterbox-ARM32.apk")
        var lastProgress = 0
        BsPatch.applyPatch(oldApk, patch, reconstructed) { progress ->
            lastProgress = progress
        }

        assertTrue("Progress should reach 100", lastProgress >= 99)
        assertEquals(newApk.length(), reconstructed.length())

        val expectedSha = sha256(newApk)
        val actualSha = sha256(reconstructed)
        assertEquals("Reconstructed APK must be byte-for-byte identical to the target APK", expectedSha, actualSha)
    }
}
