package com.laterbox.app.media.data.download

import com.laterbox.app.media.domain.model.DownloadStage
import com.laterbox.app.media.domain.model.FailureCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

sealed class DownloadResult {
    data class Success(val downloadedBytes: Long, val totalBytes: Long) : DownloadResult()
    data class Failure(val code: FailureCode, val message: String, val httpStatus: Int? = null) : DownloadResult()
}

class HttpDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {

    suspend fun download(
        url: String,
        destinationFile: File,
        headers: Map<String, String> = emptyMap(),
        onProgress: suspend (bytesDownloaded: Long, totalBytes: Long?, percent: Int, stage: DownloadStage?) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        val existingLength = if (destinationFile.exists()) destinationFile.length() else 0L

        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Laterbox) AppleWebKit/537.36")

        headers.forEach { (k, v) -> requestBuilder.header(k, v) }

        if (existingLength > 0L) {
            requestBuilder.header("Range", "bytes=$existingLength-")
        }

        val request = requestBuilder.build()

        try {
            val response = client.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful && resp.code != 206) {
                    val code = when (resp.code) {
                        401, 403 -> FailureCode.PRIVATE_CONTENT
                        404, 410 -> FailureCode.UNSUPPORTED_URL
                        429 -> FailureCode.RATE_LIMITED
                        in 500..599 -> FailureCode.HTTP_ERROR
                        else -> FailureCode.HTTP_ERROR
                    }
                    return@withContext DownloadResult.Failure(
                        code = code,
                        message = "HTTP ${resp.code}: ${resp.message}",
                        httpStatus = resp.code
                    )
                }

                val body = resp.body ?: return@withContext DownloadResult.Failure(
                    code = FailureCode.HTTP_ERROR,
                    message = "Response body is empty"
                )

                val isPartial = resp.code == 206
                val appendMode = isPartial && existingLength > 0L
                val contentLength = body.contentLength()
                val totalBytes = if (contentLength > 0L) {
                    if (isPartial) existingLength + contentLength else contentLength
                } else null

                var bytesDownloaded = if (appendMode) existingLength else 0L
                destinationFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

                val buffer = ByteArray(32 * 1024)
                var lastProgressTime = 0L
                var lastReportedPercent = -1

                FileOutputStream(destinationFile, appendMode).use { output ->
                    body.byteStream().use { input ->
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            coroutineContext.ensureActive()
                            output.write(buffer, 0, read)
                            bytesDownloaded += read

                            val now = System.currentTimeMillis()
                            val percent = if (totalBytes != null && totalBytes > 0L) {
                                ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                            } else 0

                            if (percent != lastReportedPercent || now - lastProgressTime >= 250L) {
                                onProgress(bytesDownloaded, totalBytes, percent, DownloadStage.DOWNLOADING)
                                lastReportedPercent = percent
                                lastProgressTime = now
                            }
                        }
                    }
                }

                onProgress(bytesDownloaded, totalBytes, 100, DownloadStage.FINALIZING)
                DownloadResult.Success(bytesDownloaded, totalBytes ?: bytesDownloaded)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            val failureCode = when (e) {
                is UnknownHostException, is ConnectException, is SocketTimeoutException -> FailureCode.NETWORK_ERROR
                is IOException -> FailureCode.NETWORK_ERROR
                else -> FailureCode.UNKNOWN
            }
            DownloadResult.Failure(
                code = failureCode,
                message = e.localizedMessage ?: "Download failed: ${e.javaClass.simpleName}"
            )
        }
    }
}
