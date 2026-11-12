package com.laterbox.app.media.data.download

import android.content.Context
import com.laterbox.app.LaterboxApplication
import com.laterbox.app.media.data.adapters.ytdlp.YtDlpOutputParser
import com.laterbox.app.media.data.adapters.ytdlp.YtDlpRuntimeManager
import com.laterbox.app.media.domain.model.DownloadStage
import com.laterbox.app.media.domain.model.FailureCode
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
        infoJsonFile: File? = null,
        onProgress: (bytesDownloaded: Long, totalBytes: Long?, percent: Int, stage: DownloadStage?) -> Unit
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
        }

        val isFfmpegAvailable = if (customExecutor == null) {
            val ffmpegInitResult = runtimeManager.ensureFfmpegInitialized()
            if (ffmpegInitResult.isFailure) {
                android.util.Log.w("YtDlpDownloader", "FFmpeg not available: ${ffmpegInitResult.exceptionOrNull()?.message}")
            }
            ffmpegInitResult.isSuccess
        } else true

        destinationFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

        val isAudioOnly = formatSelector.contains("bestaudio", ignoreCase = true) ||
            (!formatSelector.contains("video", ignoreCase = true) && formatSelector.contains("audio", ignoreCase = true))

        val effectiveFormatSelector = if (!isFfmpegAvailable && formatSelector.contains("+")) {
            "$formatSelector/best[vcodec!=none][acodec!=none]/best"
        } else {
            formatSelector
        }

        val isInstagram = url.contains("instagram.com", ignoreCase = true)
        val hasValidInfoJson = infoJsonFile != null && infoJsonFile.exists() && infoJsonFile.length() > 0L &&
            (System.currentTimeMillis() - infoJsonFile.lastModified() < 5 * 3600 * 1000L)

        fun buildRequest(useInfoJson: Boolean): YoutubeDLRequest {
            return if (useInfoJson && infoJsonFile != null) {
                YoutubeDLRequest(emptyList()).apply {
                    addOption("--load-info-json", infoJsonFile.absolutePath)
                }
            } else {
                YoutubeDLRequest(url)
            }.apply {
                addOption("-f", effectiveFormatSelector)
                addOption("-o", destinationFile.absolutePath)
                if (!isAudioOnly && isFfmpegAvailable) {
                    addOption("--merge-output-format", "mp4")
                }
                addOption("--print", "after_move:filepath")
                addOption("--continue")
                addOption("--socket-timeout", "20")
                if (!isInstagram) {
                    addOption("--no-playlist")
                } else {
                    addOption("--yes-playlist")
                }
                addOption("--no-warnings")
                addOption("--cache-dir", com.laterbox.app.media.data.adapters.ytdlp.YtDlpOptimizer.getCacheDir(appContext).absolutePath)
                if (!useInfoJson) {
                    addOption("--extractor-args", "youtube:player_client=default")
                    com.laterbox.app.media.data.adapters.ytdlp.QuickJsHelper.getJsRuntimeArgument(appContext)?.let { jsRuntime ->
                        addOption("--js-runtimes", jsRuntime)
                    }
                }
            }
        }

        suspend fun executeAttempt(request: YoutubeDLRequest): DownloadResult {
            return try {
                var lastReportedPercent = -1
                val progressCallback: (Float, Long, String) -> Unit = { progress, _, line ->
                    val stage = when {
                        line.contains("[Merger]") -> DownloadStage.MERGING
                        line.contains("[ExtractAudio]") -> DownloadStage.EXTRACTING_AUDIO
                        line.contains("[ffmpeg]") -> DownloadStage.CONVERTING
                        line.contains("[download]") -> DownloadStage.DOWNLOADING
                        else -> null
                    }
                    val percent = progress.toInt().coerceIn(0, 100)
                    if (percent != lastReportedPercent || stage != null) {
                        lastReportedPercent = percent
                        val currentLen = if (destinationFile.exists()) destinationFile.length() else 0L
                        onProgress(currentLen, null, percent, stage)
                    }
                }

                val cancellationHandle = coroutineContext[Job]?.invokeOnCompletion { cause ->
                    if (cause is CancellationException) {
                        cancel(taskId)
                    }
                }

                val response = try {
                    if (customExecutor != null) {
                        customExecutor.invoke(request, taskId, progressCallback)
                    } else {
                        YoutubeDL.getInstance().execute(request, taskId, progressCallback)
                    }
                } finally {
                    cancellationHandle?.dispose()
                }

                val located = locateOutputFile(destinationFile, taskId, response.out)
                if (located != null && located.exists() && located.length() > 0L) {
                    if (located.absolutePath != destinationFile.absolutePath) {
                        if (destinationFile.exists()) destinationFile.delete()
                        located.renameTo(destinationFile)
                    }
                    val fileSize = destinationFile.length()
                    onProgress(fileSize, fileSize, 100, DownloadStage.FINALIZING)
                    DownloadResult.Success(fileSize, fileSize)
                } else {
                    val err = response.err?.takeIf { it.isNotBlank() } ?: "yt-dlp exited without creating output file"
                    val code = YtDlpOutputParser.parseFailureCode(err)
                    DownloadResult.Failure(code = code, message = err)
                }
            } catch (ce: CancellationException) {
                cancel(taskId)
                throw ce
            } catch (ye: YoutubeDL.CanceledException) {
                cancel(taskId)
                throw CancellationException("Download process cancelled", ye)
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

        val firstResult = executeAttempt(buildRequest(useInfoJson = hasValidInfoJson))
        if (firstResult is DownloadResult.Failure && hasValidInfoJson) {
            com.laterbox.app.media.data.adapters.ytdlp.InfoJsonCache.invalidate(appContext, url)
            try {
                if (infoJsonFile?.exists() == true) infoJsonFile.delete()
            } catch (_: Throwable) {}
            executeAttempt(buildRequest(useInfoJson = false))
        } else {
            firstResult
        }
    }

    private fun locateOutputFile(destinationFile: File, taskId: String, stdout: String?): File? {
        if (destinationFile.exists() && destinationFile.length() > 0L) {
            return destinationFile
        }

        // 1. Check stdout lines from --print after_move:filepath
        stdout?.lines()?.map { it.trim() }?.filter { it.isNotBlank() }?.reversed()?.forEach { line ->
            val candidate = File(line)
            if (candidate.exists() && candidate.isFile && candidate.length() > 0L) {
                return candidate
            }
        }

        // 2. Scan destination parent directory for files matching taskId
        val parent = destinationFile.parentFile ?: return null
        val candidates = parent.listFiles { file ->
            file.isFile && file.length() > 0L &&
                (file.name.startsWith(destinationFile.name) || file.name.startsWith(taskId)) &&
                !file.name.endsWith(".temp") && !file.name.endsWith(".ytdl")
        }?.sortedByDescending { it.length() }

        return candidates?.firstOrNull()
    }

    fun cancel(taskId: String) {
        try {
            YoutubeDL.getInstance().destroyProcessById(taskId)
        } catch (_: Exception) {
        }
    }
}
