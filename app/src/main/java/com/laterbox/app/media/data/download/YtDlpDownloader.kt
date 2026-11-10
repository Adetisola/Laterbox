package com.laterbox.app.media.data.download

import android.content.Context
import com.laterbox.app.LaterboxApplication
import com.laterbox.app.media.data.adapters.ytdlp.YtDlpOutputParser
import com.laterbox.app.media.data.adapters.ytdlp.YtDlpRuntimeManager
import com.laterbox.app.media.domain.model.FailureCode
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class YtDlpDownloader(
    private val appContext: Context,
    private val runtimeManager: YtDlpRuntimeManager = (appContext.applicationContext as? LaterboxApplication)?.container?.ytDlpRuntimeManager ?: YtDlpRuntimeManager(appContext),
    private val customExecutor: ((YoutubeDLRequest, String, (Float, Long, String) -> Unit) -> YoutubeDLResponse)? = null
) {

    suspend fun download(
        url: String,
        formatSelector: String,
        destinationFile: File,
        taskId: String,
        onProgress: suspend (bytesDownloaded: Long, totalBytes: Long?, percent: Int) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        if (customExecutor == null) {
            val ytDlpInitResult = runtimeManager.ensureYtDlpInitialized()
            if (ytDlpInitResult.isFailure) {
                val err = ytDlpInitResult.exceptionOrNull()
                return@withContext DownloadResult.Failure(
                    code = FailureCode.UNKNOWN,
                    message = err?.message ?: "yt-dlp engine could not be initialized on this device"
                )
            }

            // Attempt FFmpeg initialization for audio/video stream merging
            val ffmpegInitResult = runtimeManager.ensureFfmpegInitialized()
            if (ffmpegInitResult.isFailure) {
                android.util.Log.w("YtDlpDownloader", "FFmpeg not available: ${ffmpegInitResult.exceptionOrNull()?.message}")
            }
        }

        destinationFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

        val request = YoutubeDLRequest(url).apply {
            addOption("-f", formatSelector)
            addOption("-o", destinationFile.absolutePath)
            addOption("--merge-output-format", "mp4")
            addOption("--continue")
            addOption("--socket-timeout", "15")
            addOption("--no-playlist")
        }

        try {
            var lastReportedPercent = -1
            val progressCallback: (Float, Long, String) -> Unit = { progress, etaInSeconds, line ->
                val percent = progress.toInt().coerceIn(0, 100)
                if (percent != lastReportedPercent) {
                    lastReportedPercent = percent
                    kotlinx.coroutines.runBlocking {
                        onProgress(destinationFile.length(), null, percent)
                    }
                }
            }

            val response = if (customExecutor != null) {
                customExecutor.invoke(request, taskId, progressCallback)
            } else {
                YoutubeDL.getInstance().execute(request, taskId, progressCallback)
            }

            if (destinationFile.exists() && destinationFile.length() > 0L) {
                val fileSize = destinationFile.length()
                onProgress(fileSize, fileSize, 100)
                DownloadResult.Success(fileSize, fileSize)
            } else {
                val err = response.err ?: "yt-dlp exited without creating output file"
                val code = YtDlpOutputParser.parseFailureCode(err)
                DownloadResult.Failure(code = code, message = err)
            }
        } catch (ce: CancellationException) {
            cancel(taskId)
            throw ce
        } catch (ye: YoutubeDLException) {
            val errText = ye.message ?: "yt-dlp execution error"
            val code = YtDlpOutputParser.parseFailureCode(errText)
            DownloadResult.Failure(code = code, message = errText)
        } catch (t: Throwable) {
            DownloadResult.Failure(
                code = FailureCode.UNKNOWN,
                message = t.localizedMessage ?: "Unknown download failure"
            )
        }
    }

    fun cancel(taskId: String) {
        try {
            YoutubeDL.getInstance().destroyProcessById(taskId)
        } catch (_: Exception) {
        }
    }
}
