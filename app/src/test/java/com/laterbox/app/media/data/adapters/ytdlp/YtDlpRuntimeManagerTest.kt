package com.laterbox.app.media.data.adapters.ytdlp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.util.zip.ZipException

@RunWith(RobolectricTestRunner::class)
class YtDlpRuntimeManagerTest {

    private lateinit var context: Context
    private lateinit var runtimeManager: YtDlpRuntimeManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runtimeManager = YtDlpRuntimeManager(context)
    }

    @Test
    fun `categorizeException maps ExceptionInInitializerError to ARCHIVE_EXTRACTION_FAILURE`() {
        val rootCause = InstantiationException("class AsiExtraField is not a concrete class")
        val initError = ExceptionInInitializerError(rootCause)

        val categorized = runtimeManager.categorizeException(initError, isFfmpeg = false)
        assertEquals(YtDlpInitFailureCategory.ARCHIVE_EXTRACTION_FAILURE, categorized.category)
        assertTrue(categorized.message.contains("ExceptionInInitializerError"))
        assertTrue(categorized.message.contains("InstantiationException"))
    }

    @Test
    fun `categorizeException maps NoClassDefFoundError to ARCHIVE_EXTRACTION_FAILURE`() {
        val noClassError = NoClassDefFoundError("Could not initialize class org.apache.commons.compress.archivers.zip.ExtraFieldUtils")

        val categorized = runtimeManager.categorizeException(noClassError, isFfmpeg = false)
        assertEquals(YtDlpInitFailureCategory.ARCHIVE_EXTRACTION_FAILURE, categorized.category)
    }

    @Test
    fun `categorizeException maps ZipException to ARCHIVE_EXTRACTION_FAILURE`() {
        val zipEx = ZipException("Corrupt central directory record")

        val categorized = runtimeManager.categorizeException(zipEx, isFfmpeg = false)
        assertEquals(YtDlpInitFailureCategory.ARCHIVE_EXTRACTION_FAILURE, categorized.category)
    }

    @Test
    fun `categorizeException maps UnsatisfiedLinkError to NATIVE_LIBRARY_LOAD_FAILURE`() {
        val linkError = UnsatisfiedLinkError("dlopen failed: library libpython.so not found")

        val categorized = runtimeManager.categorizeException(linkError, isFfmpeg = false)
        assertEquals(YtDlpInitFailureCategory.NATIVE_LIBRARY_LOAD_FAILURE, categorized.category)
    }

    @Test
    fun `categorizeException maps storage space error to STORAGE_OR_PERMISSION_ERROR`() {
        val ioEx = IOException("write failed: ENOSPC (No space left on device)")

        val categorized = runtimeManager.categorizeException(ioEx, isFfmpeg = false)
        assertEquals(YtDlpInitFailureCategory.STORAGE_OR_PERMISSION_ERROR, categorized.category)
    }

    @Test
    fun `categorizeException maps FFmpeg error to FFMPEG_INIT_FAILURE`() {
        val ffmpegEx = RuntimeException("Failed to initialize ffmpeg binaries")

        val categorized = runtimeManager.categorizeException(ffmpegEx, isFfmpeg = true)
        assertEquals(YtDlpInitFailureCategory.FFMPEG_INIT_FAILURE, categorized.category)
    }

    @Test
    fun `captureDiagnosticReport produces complete formatted text`() {
        val report = runtimeManager.captureDiagnosticReport()
        val text = report.toFormattedString()

        assertTrue(text.contains("Laterbox Media Engine Diagnostics"))
        assertTrue(text.contains("Device:"))
        assertTrue(text.contains("Process:"))
        assertTrue(text.contains("Supported ABIs:"))
        assertTrue(text.contains("yt-dlp Engine State:"))
        assertTrue(text.contains("FFmpeg Engine State:"))
    }
}
