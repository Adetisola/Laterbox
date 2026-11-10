package com.laterbox.app.media.data.adapters.ytdlp

import android.content.Context
import android.util.Log
import com.laterbox.app.data.preferences.UserPreferencesRepository
import com.laterbox.app.media.domain.model.FailureCode
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class YtDlpUpdater(
    private val appContext: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    @Volatile
    private var isInitialized = false

    private fun ensureInitialized(): Boolean {
        if (android.os.Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) return false
        if (isInitialized) return true
        return synchronized(this) {
            if (isInitialized) return true
            try {
                YoutubeDL.getInstance().init(appContext)
                try {
                    FFmpeg.getInstance().init(appContext)
                } catch (t: Throwable) {
                    Log.w("YtDlpUpdater", "FFmpeg init warning (non-fatal): ${t.message}")
                }
                isInitialized = true
                true
            } catch (t: Throwable) {
                Log.w("YtDlpUpdater", "YoutubeDL init failed: ${t.message}")
                false
            }
        }
    }

    suspend fun getInstalledVersion(): String = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()
            YoutubeDL.getInstance().version(appContext) ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    suspend fun updateIfStale(maxAgeMs: Long = 24 * 60 * 60 * 1000L): Boolean = withContext(Dispatchers.IO) {
        val prefs = userPreferencesRepository.userPreferencesFlow.first()
        val now = System.currentTimeMillis()
        if (now - prefs.lastYtDlpUpdateCheck < maxAgeMs) {
            return@withContext false
        }
        forceUpdate().isSuccess
    }

    suspend fun forceUpdate(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val initialized = ensureInitialized()
            if (!initialized) {
                return@withContext Result.failure(IllegalStateException("yt-dlp engine could not be initialized on this device"))
            }
            val status = YoutubeDL.getInstance().updateYoutubeDL(appContext, YoutubeDL.UpdateChannel.STABLE)
            val version = YoutubeDL.getInstance().version(appContext) ?: "latest"
            userPreferencesRepository.recordYtDlpUpdate(version, System.currentTimeMillis())
            Result.success(version)
        } catch (t: Throwable) {
            Result.failure(if (t is Exception) t else Exception(t.message, t))
        }
    }

    suspend fun <T> updateAndRetryOnExtractorBroken(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            val message = e.message ?: ""
            if (YtDlpOutputParser.parseFailureCode(message) == FailureCode.EXTRACTOR_BROKEN) {
                // One automatic update-and-retry before surfacing error to user
                forceUpdate()
                block()
            } else {
                throw e
            }
        }
    }
}
