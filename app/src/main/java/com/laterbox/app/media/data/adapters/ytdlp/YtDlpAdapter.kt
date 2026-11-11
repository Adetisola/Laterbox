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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.UUID

class YtDlpAdapter(
    private val appContext: Context,
    private val runtimeManager: YtDlpRuntimeManager = (appContext.applicationContext as? LaterboxApplication)?.container?.ytDlpRuntimeManager ?: YtDlpRuntimeManager(appContext),
    private val customExecutor: ((YoutubeDLRequest) -> YoutubeDLResponse)? = null
) : MediaPlatformAdapter {

    override val id: String = "ytdlp"

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

    override suspend fun inspect(url: String, processId: String?): InspectionResult = withContext(Dispatchers.IO) {
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

            val request = YoutubeDLRequest(url).apply {
                addOption("--dump-single-json")
                addOption("--no-playlist")
                addOption("--socket-timeout", "15")
                addOption("--no-warnings")
                addOption("--extractor-args", "youtube:player_client=default")
            }

            val response = try {
                withTimeout(25_000L) {
                    customExecutor?.invoke(request) ?: YoutubeDL.getInstance().execute(request, inspectionId)
                }
            } catch (te: TimeoutCancellationException) {
                withContext(NonCancellable) {
                    try {
                        YoutubeDL.getInstance().destroyProcessById(inspectionId)
                    } catch (_: Exception) {
                    }
                }
                return@withContext InspectionResult.Failed(
                    code = FailureCode.EXTRACTION_TIMEOUT,
                    message = "Media inspection timed out after 25 seconds.",
                    retryable = true
                )
            } catch (ce: CancellationException) {
                withContext(NonCancellable) {
                    try {
                        YoutubeDL.getInstance().destroyProcessById(inspectionId)
                    } catch (_: Exception) {
                    }
                }
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
}
