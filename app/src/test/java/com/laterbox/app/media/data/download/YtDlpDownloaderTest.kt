package com.laterbox.app.media.data.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.laterbox.app.media.domain.model.FailureCode
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YtDlpDownloaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun download_withCustomExecutor_success_writesToDestinationAndReportsProgress() = runBlocking {
        val destFile = File(tempFolder.root, "test_video.part")

        val customExecutor: (com.yausername.youtubedl_android.YoutubeDLRequest, String, (Float, Long, String) -> Unit) -> YoutubeDLResponse =
            { _, _, progressCb ->
                destFile.writeBytes(ByteArray(1024) { 1 })
                progressCb(50.0f, 10L, "[download] 50%")
                progressCb(100.0f, 0L, "[download] 100%")
                YoutubeDLResponse(emptyList(), 0, 50L, "", "")
            }

        val downloader = YtDlpDownloader(context, customExecutor = customExecutor)
        var progressReported = false

        val result = downloader.download(
            url = "https://youtube.com/watch?v=sample",
            formatSelector = "best",
            destinationFile = destFile,
            taskId = "task_test_123",
            onProgress = { _, _, percent, _ ->
                if (percent == 100) progressReported = true
            }
        )

        assertTrue(result is DownloadResult.Success)
        val success = result as DownloadResult.Success
        assertEquals(1024L, success.downloadedBytes)
        assertTrue(progressReported)
    }

    @Test
    fun download_withCustomExecutor_failure_returnsAppropriateFailureCode() = runBlocking {
        val destFile = File(tempFolder.root, "failed_video.part")

        val customExecutor: (com.yausername.youtubedl_android.YoutubeDLRequest, String, (Float, Long, String) -> Unit) -> YoutubeDLResponse =
            { _, _, _ ->
                YoutubeDLResponse(emptyList(), 1, 10L, "", "ERROR: video is geo-blocked in your country")
            }

        val downloader = YtDlpDownloader(context, customExecutor = customExecutor)

        val result = downloader.download(
            url = "https://youtube.com/watch?v=geoblocked",
            formatSelector = "best",
            destinationFile = destFile,
            taskId = "task_geo_123",
            onProgress = { _, _, _, _ -> }
        )

        assertTrue(result is DownloadResult.Failure)
        val failure = result as DownloadResult.Failure
        assertEquals(FailureCode.GEO_BLOCKED, failure.code)
    }
}
