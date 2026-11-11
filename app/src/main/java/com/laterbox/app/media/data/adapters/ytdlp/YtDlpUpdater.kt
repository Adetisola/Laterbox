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
            val ytdlDir = java.io.File(appContext.noBackupFilesDir, "youtubedl-android/yt-dlp")
            val ytdlBin = java.io.File(ytdlDir, "yt-dlp")
            val backupBin = java.io.File(ytdlDir, "yt-dlp.backup")

            try {
                val initResult = runtimeManager.ensureYtDlpInitialized()
                if (initResult.isFailure) {
                    val ex = initResult.exceptionOrNull()
                    return@withContext Result.failure(
                        ex ?: IllegalStateException("yt-dlp engine could not be initialized on this device")
                    )
                }

                // 1. Take a safe backup of the current binary before replacing
                if (ytdlBin.exists()) {
                    try {
                        ytdlBin.copyTo(backupBin, overwrite = true)
                    } catch (e: Exception) {
                        Log.w(tag, "Could not create yt-dlp backup: ${e.message}")
                    }
                }

                Log.i(tag, "Checking and updating yt-dlp binary from GitHub...")
                val status = YoutubeDL.getInstance().updateYoutubeDL(appContext, YoutubeDL.UpdateChannel.STABLE)

                val pythonInfo = runtimeManager.inspectPythonInterpreter()
                val minPythonReq = YtDlpOptimizer.getMinimumPythonRequirement(ytdlBin)
                if (pythonInfo.major > 0 && (pythonInfo.major < minPythonReq.first || (pythonInfo.major == minPythonReq.first && pythonInfo.minor < minPythonReq.second))) {
                    throw IllegalStateException("yt-dlp requires Python >= ${minPythonReq.first}.${minPythonReq.second}, but bundled Python interpreter is ${pythonInfo.versionString}.")
                }

                if (status == YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE) {
                    val currentVersion = YoutubeDL.getInstance().version(appContext) ?: "latest"
                    if (backupBin.exists()) backupBin.delete()
                    userPreferencesRepository.recordYtDlpUpdate(currentVersion, System.currentTimeMillis())
                    Log.i(tag, "yt-dlp is already up to date: $currentVersion")
                    return@withContext Result.success(currentVersion)
                }

                // 3. Unpack, patch player cache, and validate new script
                if (!android.os.Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) {
                    val optResult = YtDlpOptimizer.ensureOptimized(appContext, forceReextract = true)
                    Log.i(tag, "Post-update optimization status: isOptimized=${optResult.isOptimized}, patched=${optResult.isPlayerCachePatched}")
                    if (!optResult.isOptimized) {
                        throw IllegalStateException("Post-update optimization failed: ${optResult.error ?: "unpacked validation failed"}")
                    }
                }

                val detectedPython = pythonInfo.versionString
                Log.i(tag, "Validating updated yt-dlp against bundled Python interpreter ($detectedPython)...")

                var probeSuccess = false
                val probeRequest = com.yausername.youtubedl_android.YoutubeDLRequest(emptyList()).apply {
                    addOption("--version")
                    addOption("--cache-dir", YtDlpOptimizer.getCacheDir(appContext).absolutePath)
                }
                val probeId = "probe_${System.currentTimeMillis()}"
                try {
                    val probeResponse = YoutubeDL.getInstance().execute(probeRequest, probeId)
                    val probeOutput = probeResponse.out?.trim()
                    if (!probeOutput.isNullOrBlank()) {
                        probeSuccess = true
                        Log.i(tag, "Script probe succeeded with output: $probeOutput (Python: $detectedPython)")
                    }
                } catch (pe: Exception) {
                    Log.w(tag, "Script probe execution failed on newly updated yt-dlp: ${pe.message}", pe)
                }

                if (!probeSuccess && !android.os.Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) {
                    throw IllegalStateException("Newly updated yt-dlp failed probe validation on Python $detectedPython.")
                }

                // 3. Verification passed: clean up backup
                if (backupBin.exists()) backupBin.delete()

                val version = YoutubeDL.getInstance().version(appContext) ?: "latest"
                userPreferencesRepository.recordYtDlpUpdate(version, System.currentTimeMillis())
                Log.i(tag, "yt-dlp update completed and verified: status=$status, version=$version, python=$detectedPython")
                Result.success(version)
            } catch (t: Throwable) {
                Log.e(tag, "Failed to update yt-dlp: ${t.message}. Initiating rollback...", t)
                // 4. Rollback: restore backup or re-extract bundled binary
                try {
                    if (backupBin.exists()) {
                        backupBin.copyTo(ytdlBin, overwrite = true)
                        backupBin.delete()
                        Log.i(tag, "Successfully rolled back to previous yt-dlp binary from backup")
                    } else if (ytdlDir.exists()) {
                        YoutubeDL.getInstance().init_ytdlp(appContext, ytdlDir)
                        Log.i(tag, "Successfully re-extracted bundled yt-dlp binary from APK")
                    }
                    if (!android.os.Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) {
                        YtDlpOptimizer.ensureOptimized(appContext, forceReextract = true)
                    }
                } catch (re: Exception) {
                    Log.e(tag, "Rollback error: ${re.message}", re)
                }
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
