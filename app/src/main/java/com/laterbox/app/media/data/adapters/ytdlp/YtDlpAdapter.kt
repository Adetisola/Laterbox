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
        // Spotify is handled by MetadataOnlyAdapter per spec
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

    override suspend fun inspect(url: String): InspectionResult = withContext(Dispatchers.IO) {
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
            }

            val response = customExecutor?.invoke(request) ?: YoutubeDL.getInstance().execute(request)
            val jsonOutput = response.out
            if (jsonOutput.isNullOrBlank()) {
                val err = response.err ?: "No metadata returned from yt-dlp"
                return@withContext YtDlpOutputParser.parseError(err)
            }

            YtDlpOutputParser.parse(url, jsonOutput)
        } catch (e: YoutubeDLException) {
            YtDlpOutputParser.parseError(e.message ?: "yt-dlp execution error")
        } catch (t: Throwable) {
            InspectionResult.Failed(
                code = FailureCode.UNKNOWN,
                message = t.localizedMessage ?: "Failed to inspect URL",
                retryable = true
            )
        }
    }
}
