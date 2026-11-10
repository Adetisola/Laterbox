package com.laterbox.app.media.data.adapters.ytdlp

import android.content.Context
import com.laterbox.app.data.preferences.UserPreferencesRepository
import com.laterbox.app.media.domain.model.FailureCode
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class YtDlpUpdater(
    private val appContext: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    suspend fun getInstalledVersion(): String = withContext(Dispatchers.IO) {
        try {
            YoutubeDL.getInstance().version(appContext) ?: ""
        } catch (_: Exception) {
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
            val status = YoutubeDL.getInstance().updateYoutubeDL(appContext, YoutubeDL.UpdateChannel.STABLE)
            val version = YoutubeDL.getInstance().version(appContext) ?: "latest"
            userPreferencesRepository.recordYtDlpUpdate(version, System.currentTimeMillis())
            Result.success(version)
        } catch (e: Exception) {
            Result.failure(e)
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
