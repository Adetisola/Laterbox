package com.laterbox.app.media.data.adapters.ytdlp

import android.content.Context
import com.laterbox.app.media.domain.adapter.MediaPlatformAdapter
import com.laterbox.app.media.domain.model.FailureCode
import com.laterbox.app.media.domain.model.InspectionResult
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

class YtDlpAdapter(
    private val appContext: Context,
    private val customExecutor: ((YoutubeDLRequest) -> YoutubeDLResponse)? = null
) : MediaPlatformAdapter {

    override val id: String = "ytdlp"

    @Volatile
    private var isInitialized = false

    private fun ensureInitialized() {
        if (!isInitialized && customExecutor == null) {
            synchronized(this) {
                if (!isInitialized) {
                    try {
                        YoutubeDL.getInstance().init(appContext)
                        FFmpeg.getInstance().init(appContext)
                    } catch (e: Exception) {
                        android.util.Log.w("YtDlpAdapter", "Native YoutubeDL/FFmpeg init exception: ${e.message}")
                    }
                    isInitialized = true
                }
            }
        }
    }

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
            ensureInitialized()

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
        } catch (e: Exception) {
            InspectionResult.Failed(
                code = FailureCode.UNKNOWN,
                message = e.localizedMessage ?: "Failed to inspect URL",
                retryable = true
            )
        }
    }
}
