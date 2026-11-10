package com.laterbox.app

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.laterbox.app.data.metadata.MetadataSyncWorker
import com.laterbox.app.di.AppContainer
import com.laterbox.app.di.DefaultAppContainer
import com.laterbox.app.reminders.smart.SmartReminderCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LaterboxApplication : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)

        // Bypass background workers and App Check in Robolectric / unit test environments
        if (android.os.Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) {
            return
        }

        // Initialize Firebase App Check: Debug provider in debug builds, Play Integrity in release builds
        try {
            val isDebug = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val providerFactory = if (isDebug) {
                com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory.getInstance()
            } else {
                PlayIntegrityAppCheckProviderFactory.getInstance()
            }
            Firebase.appCheck.installAppCheckProviderFactory(providerFactory)
            Log.d("LaterboxApplication", "Installed App Check provider factory")
        } catch (e: Exception) {
            Log.w("LaterboxApplication", "Failed to install App Check provider: ${e.message}")
        }

        // Reset any links left stuck in FETCHING from an abrupt process termination and enqueue workers
        applicationScope.launch {
            try {
                // Initialize yt-dlp core engine asynchronously via YtDlpRuntimeManager
                try {
                    container.ytDlpRuntimeManager.ensureYtDlpInitialized()
                    // Pre-warm FFmpeg asynchronously without blocking other startup tasks
                    applicationScope.launch {
                        try {
                            container.ytDlpRuntimeManager.ensureFfmpegInitialized()
                        } catch (t: Throwable) {
                            Log.w("LaterboxApplication", "FFmpeg background pre-warm warning: ${t.message}")
                        }
                    }
                } catch (t: Throwable) {
                    Log.w("LaterboxApplication", "yt-dlp runtime init warning: ${t.message}")
                }

                container.database.savedLinkDao().resetStuckFetchingLinks()
                MetadataSyncWorker.enqueue(this@LaterboxApplication)

                val prefs = container.userPreferencesRepository.userPreferencesFlow.first()
                SmartReminderCoordinator.sync(
                    context = this@LaterboxApplication,
                    settings = prefs.toSmartReminderSettings()
                )

                // Recover any interrupted media downloads and sweep orphan files
                container.mediaRepository.recoverInterruptedTasks()
                container.mediaRepository.runOrphanSweep()

                // Check for yt-dlp binary updates if > 24 hours (deferred to prevent startup lock contention)
                applicationScope.launch {
                    kotlinx.coroutines.delay(15_000L)
                    try {
                        container.ytDlpUpdater.updateIfStale()
                    } catch (t: Throwable) {
                        Log.w("LaterboxApplication", "Deferred yt-dlp update check error: ${t.message}")
                    }
                }
            } catch (e: Exception) {
                // Safeguard against test environments
            }
        }
    }
}
