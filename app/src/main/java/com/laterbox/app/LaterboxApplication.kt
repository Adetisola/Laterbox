package com.laterbox.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
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

        // Reset any links left stuck in FETCHING from an abrupt process termination and enqueue workers
        applicationScope.launch {
            try {
                container.database.savedLinkDao().resetStuckFetchingLinks()
                MetadataSyncWorker.enqueue(this@LaterboxApplication)

                val prefs = container.userPreferencesRepository.userPreferencesFlow.first()
                if (prefs.smartRemindersEnabled) {
                    com.laterbox.app.ai.DailyDigestWorker.enqueuePeriodic(this@LaterboxApplication)
                }
            } catch (e: Exception) {
                // Safeguard against test environments
            }
        }
    }
}
