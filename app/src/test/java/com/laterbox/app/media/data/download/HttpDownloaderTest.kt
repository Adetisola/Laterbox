package com.laterbox.app.media.data.download

import com.laterbox.app.media.data.storage.ChecksumUtil
import com.laterbox.app.media.domain.model.FailureCode
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HttpDownloaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var downloader: HttpDownloader

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        downloader = HttpDownloader()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun downloadDirectFile_writesToDestinationAndReportsProgress() = runTest {
        val payload = "Hello Laterbox Media Downloader Payload! 1234567890"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", payload.toByteArray().size)
                .setBody(payload)
        )

        val destFile = File(tempFolder.root, "test_download.bin")
        val progressUpdates = mutableListOf<Int>()

        val result = downloader.download(
            url = server.url("/media.bin").toString(),
            destinationFile = destFile,
            onProgress = { _, _, percent, _ ->
                progressUpdates.add(percent)
            }
        )

        assertTrue(result is DownloadResult.Success)
        val success = result as DownloadResult.Success
        assertEquals(payload.length.toLong(), success.downloadedBytes)
        assertEquals(payload, destFile.readText())
        assertEquals(ChecksumUtil.calculateSha256(destFile), ChecksumUtil.calculateSha256(payload.byteInputStream()))
        assertTrue(progressUpdates.contains(100))
    }

    @Test
    fun download_supportsRangeResume_whenPartFileExists() = runTest {
        val firstPart = "Part1Bytes"
        val secondPart = "Part2BytesFinished"
        val fullContent = firstPart + secondPart

        val destFile = File(tempFolder.root, "resume_test.part")
        destFile.writeText(firstPart)
        assertEquals(firstPart.length.toLong(), destFile.length())

        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes ${firstPart.length}-${fullContent.length - 1}/${fullContent.length}")
                .setHeader("Content-Length", secondPart.length)
                .setBody(secondPart)
        )

        val result = downloader.download(
            url = server.url("/resumable_media.mp4").toString(),
            destinationFile = destFile,
            onProgress = { _, _, _, _ -> }
        )

        val recordedRequest = server.takeRequest()
        assertEquals("bytes=${firstPart.length}-", recordedRequest.getHeader("Range"))

        assertTrue(result is DownloadResult.Success)
        val success = result as DownloadResult.Success
        assertEquals(fullContent.length.toLong(), success.downloadedBytes)
        assertEquals(fullContent, destFile.readText())
    }

    @Test
    fun download_handlesServerErrorGracefully() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val destFile = File(tempFolder.root, "not_found.bin")
        val result = downloader.download(
            url = server.url("/missing.jpg").toString(),
            destinationFile = destFile,
            onProgress = { _, _, _, _ -> }
        )

        assertTrue(result is DownloadResult.Failure)
        val failure = result as DownloadResult.Failure
        assertEquals(FailureCode.UNSUPPORTED_URL, failure.code)
        assertEquals(404, failure.httpStatus)
    }
}
