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
                container.database.savedLinkDao().resetStuckFetchingLinks()
                MetadataSyncWorker.enqueue(this@LaterboxApplication)

                val prefs = container.userPreferencesRepository.userPreferencesFlow.first()
                if (prefs.smartRemindersEnabled) {
                    com.laterbox.app.reminders.ReminderScheduler(this@LaterboxApplication).scheduleSmartDigest(
                        targetHour = prefs.smartReminderHour,
                        targetMinute = prefs.smartReminderMinute,
                        schedule = prefs.smartReminderSchedule
                    )
                    com.laterbox.app.ai.DailyDigestWorker.enqueuePeriodic(
                        context = this@LaterboxApplication,
                        schedule = prefs.smartReminderSchedule,
                        targetHour = prefs.smartReminderHour,
                        targetMinute = prefs.smartReminderMinute
                    )
                }
            } catch (e: Exception) {
                // Safeguard against test environments
            }
        }
    }
}
