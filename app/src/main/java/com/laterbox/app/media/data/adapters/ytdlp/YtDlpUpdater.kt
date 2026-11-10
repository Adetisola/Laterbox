package com.laterbox.app.media.data.adapters.ytdlp

import android.content.Context
import android.util.Log
import com.laterbox.app.LaterboxApplication
import com.laterbox.app.data.preferences.UserPreferencesRepository
import com.laterbox.app.media.domain.model.FailureCode
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class YtDlpUpdater(
    private val appContext: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val runtimeManager: YtDlpRuntimeManager = (appContext.applicationContext as? LaterboxApplication)?.container?.ytDlpRuntimeManager ?: YtDlpRuntimeManager(appContext)
) {
    private val tag = "YtDlpUpdater"
    private val updateMutex = Mutex()

    suspend fun getInstalledVersion(): String = withContext(Dispatchers.IO) {
        try {
            val initResult = runtimeManager.ensureYtDlpInitialized()
            if (initResult.isSuccess) {
                YoutubeDL.getInstance().version(appContext) ?: ""
            } else {
                ""
            }
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
        updateMutex.withLock {
            try {
                val initResult = runtimeManager.ensureYtDlpInitialized()
                if (initResult.isFailure) {
                    val ex = initResult.exceptionOrNull()
                    return@withContext Result.failure(
                        ex ?: IllegalStateException("yt-dlp engine could not be initialized on this device")
                    )
                }

                Log.i(tag, "Checking and updating yt-dlp binary from GitHub...")
                val status = YoutubeDL.getInstance().updateYoutubeDL(appContext, YoutubeDL.UpdateChannel.STABLE)
                val version = YoutubeDL.getInstance().version(appContext) ?: "latest"
                userPreferencesRepository.recordYtDlpUpdate(version, System.currentTimeMillis())
                Log.i(tag, "yt-dlp update completed: status=$status, version=$version")
                Result.success(version)
            } catch (t: Throwable) {
                Log.e(tag, "Failed to update yt-dlp: ${t.message}", t)
                val mapped = if (t is Exception) t else Exception(t.message, t)
                Result.failure(mapped)
            }
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
