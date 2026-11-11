package com.laterbox.app.media.data.adapters.ytdlp

import android.content.Context
import com.laterbox.app.LaterboxApplication
import com.laterbox.app.media.domain.adapter.MediaPlatformAdapter
import com.laterbox.app.media.domain.model.FailureCode
import com.laterbox.app.media.domain.model.InspectionResult
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class YtDlpAdapter(
    private val appContext: Context,
    private val runtimeManager: YtDlpRuntimeManager = (appContext.applicationContext as? LaterboxApplication)?.container?.ytDlpRuntimeManager ?: YtDlpRuntimeManager(appContext),
    private val customExecutor: ((YoutubeDLRequest) -> YoutubeDLResponse)? = null,
    private val timeoutMs: Long = DEFAULT_INSPECTION_TIMEOUT_MS
) : MediaPlatformAdapter {

    override val id: String = "ytdlp"
    override val inspectionTimeoutMs: Long = timeoutMs

    override fun canHandle(url: String): Boolean {
        val host = try {
            URI(url).host?.lowercase() ?: ""
        } catch (_: Exception) {
            ""
        }

        if (host.isBlank()) return false
        // Spotify is handled by MetadataOnlyAdapter / SpotifyMusicAdapter per spec
        if (host.contains("spotify.com") || host.contains("spotify.link")) {
            return false
        }

        val scheme = try {
            URI(url).scheme?.lowercase() ?: ""
        } catch (_: Exception) {
            ""
        }

        return scheme == "http" || scheme == "https"
    }

    override suspend fun inspect(url: String): InspectionResult = inspect(url, null)

    override suspend fun inspect(url: String, processId: String?): InspectionResult =
        inspectInternal(url, processId, timeoutMs = null)

    suspend fun inspect(url: String, processId: String?, timeoutMs: Long?): InspectionResult =
        inspectInternal(url, processId, timeoutMs)

    private suspend fun inspectInternal(
        url: String,
        processId: String?,
        timeoutMs: Long?
    ): InspectionResult = withContext(Dispatchers.IO) {
        val inspectionId = processId ?: "inspect_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        try {
            if (customExecutor == null) {
                val initResult = runtimeManager.ensureYtDlpInitialized()
                if (initResult.isFailure) {
                    val ex = initResult.exceptionOrNull()
                    val errorMessage = ex?.message ?: "yt-dlp engine could not be initialized on this device"
                    return@withContext InspectionResult.Failed(
                        code = FailureCode.UNKNOWN,
                        message = errorMessage,
                        retryable = true
                    )
                }
            }

            val host = try {
                URI(url).host?.lowercase() ?: ""
            } catch (_: Exception) {
                ""
            }
            val isInstagram = host.contains("instagram.com")

            val request = YoutubeDLRequest(url).apply {
                addOption("--dump-single-json")
                if (!isInstagram) {
                    addOption("--no-playlist")
                } else {
                    addOption("--yes-playlist")
                }
                addOption("--socket-timeout", "30")
                addOption("--cache-dir", YtDlpOptimizer.getCacheDir(appContext).absolutePath)
                addOption("--extractor-args", "youtube:player_client=default")
            }

            val response = try {
                executeCancellable(request, inspectionId, timeoutMs = timeoutMs)
            } catch (te: TimeoutCancellationException) {
                return@withContext InspectionResult.Failed(
                    code = FailureCode.EXTRACTION_TIMEOUT,
                    message = "Media inspection timed out after ${(timeoutMs ?: inspectionTimeoutMs) / 1000} seconds.",
                    retryable = true
                )
            } catch (ce: CancellationException) {
                throw ce
            }

            val jsonOutput = response.out
            if (jsonOutput.isNullOrBlank()) {
                val err = response.err ?: "No metadata returned from yt-dlp"
                return@withContext YtDlpOutputParser.parseError(err)
            }

            YtDlpOutputParser.parse(url, jsonOutput)
        } catch (e: YoutubeDLException) {
            YtDlpOutputParser.parseError(e.message ?: "yt-dlp execution error")
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            InspectionResult.Failed(
                code = FailureCode.UNKNOWN,
                message = t.localizedMessage ?: "Failed to inspect URL",
                retryable = true
            )
        }
    }

    private suspend fun executeCancellable(
        request: YoutubeDLRequest,
        processId: String,
        timeoutMs: Long? = null
    ): YoutubeDLResponse = coroutineScope {
        val isCancelled = AtomicBoolean(false)
        val deferred = async(Dispatchers.IO) {
            if (isCancelled.get() || cancelledProcessIds.contains(processId)) {
                throw CancellationException("Process $processId was cancelled prior to execution")
            }
            if (customExecutor != null) {
                return@async customExecutor.invoke(request)
            }
            try {
                YoutubeDL.getInstance().execute(request, processId)
            } catch (ce: YoutubeDL.CanceledException) {
                throw CancellationException("yt-dlp process was cancelled", ce)
            }
        }

        val cleanup = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                isCancelled.set(true)
                cancelledProcessIds.add(processId)
                try {
                    YoutubeDL.getInstance().destroyProcessById(processId)
                } catch (_: Throwable) {
                }
            }
        }

        try {
            if (timeoutMs != null) {
                withTimeout(timeoutMs) {
                    deferred.await()
                }
            } else {
                deferred.await()
            }
        } catch (t: Throwable) {
            try {
                YoutubeDL.getInstance().destroyProcessById(processId)
            } catch (_: Throwable) {
            }
            deferred.cancel()
            throw t
        } finally {
            cleanup?.dispose()
            cancelledProcessIds.remove(processId)
        }
    }

    companion object {
        const val DEFAULT_INSPECTION_TIMEOUT_MS = 90_000L
        private val cancelledProcessIds = ConcurrentHashMap.newKeySet<String>()
    }
}
