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

    private fun ensureInitialized(): Boolean {
        if (android.os.Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) return false
        if (customExecutor != null) return true
        if (isInitialized) return true
        return synchronized(this) {
            if (isInitialized) return true
            try {
                YoutubeDL.getInstance().init(appContext)
                try {
                    FFmpeg.getInstance().init(appContext)
                } catch (t: Throwable) {
                    android.util.Log.w("YtDlpAdapter", "FFmpeg init warning (non-fatal for inspection): ${t.message}")
                }
                isInitialized = true
                true
            } catch (t: Throwable) {
                android.util.Log.e("YtDlpAdapter", "Native YoutubeDL init failed: ${t.message}", t)
                false
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
            val initialized = ensureInitialized()
            if (!initialized && customExecutor == null) {
                return@withContext InspectionResult.Failed(
                    code = FailureCode.UNKNOWN,
                    message = "yt-dlp engine could not be initialized on this device",
                    retryable = true
                )
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
