package com.laterbox.app.di

import android.content.Context
import com.laterbox.app.data.backup.BackupManager
import com.laterbox.app.data.local.LaterboxDatabase
import com.laterbox.app.data.preferences.UserPreferencesRepository
import com.laterbox.app.data.repository.CollectionRepository
import com.laterbox.app.data.repository.LinkRepository
import com.laterbox.app.data.repository.NotificationHistoryRepository

interface AppContainer {
    val database: LaterboxDatabase
    val linkRepository: LinkRepository
    val collectionRepository: CollectionRepository
    val userPreferencesRepository: UserPreferencesRepository
    val backupManager: BackupManager
    val reminderScheduler: com.laterbox.app.reminders.ReminderScheduler
    val notificationHistoryRepository: NotificationHistoryRepository
    val mediaRepository: com.laterbox.app.media.domain.repository.MediaRepository
    val platformResolver: com.laterbox.app.media.domain.adapter.PlatformResolver
    val ytDlpUpdater: com.laterbox.app.media.data.adapters.ytdlp.YtDlpUpdater
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val database: LaterboxDatabase by lazy {
        LaterboxDatabase.getInstance(context)
    }

    override val linkRepository: LinkRepository by lazy {
        LinkRepository(database.savedLinkDao(), context.applicationContext)
    }

    override val collectionRepository: CollectionRepository by lazy {
        CollectionRepository(database.collectionDao())
    }

    override val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(context)
    }

    override val backupManager: BackupManager by lazy {
        BackupManager(database)
    }

    override val reminderScheduler: com.laterbox.app.reminders.ReminderScheduler by lazy {
        com.laterbox.app.reminders.ReminderScheduler(context)
    }

    override val notificationHistoryRepository: NotificationHistoryRepository by lazy {
        NotificationHistoryRepository(database.notificationHistoryDao())
    }

    override val mediaRepository: com.laterbox.app.media.domain.repository.MediaRepository by lazy {
        com.laterbox.app.media.data.repository.MediaRepositoryImpl(
            context = context.applicationContext,
            assetDao = database.downloadedAssetDao(),
            taskDao = database.downloadTaskDao()
        )
    }

    override val ytDlpUpdater: com.laterbox.app.media.data.adapters.ytdlp.YtDlpUpdater by lazy {
        com.laterbox.app.media.data.adapters.ytdlp.YtDlpUpdater(
            appContext = context.applicationContext,
            userPreferencesRepository = userPreferencesRepository
        )
    }

    override val platformResolver: com.laterbox.app.media.domain.adapter.PlatformResolver by lazy {
        val directAdapter = com.laterbox.app.media.data.adapters.direct.DirectUrlAdapter(okhttp3.OkHttpClient())
        val ytDlpAdapter = com.laterbox.app.media.data.adapters.ytdlp.YtDlpAdapter(context.applicationContext)
        val metadataAdapter = com.laterbox.app.media.data.adapters.metadata.MetadataOnlyAdapter(okhttp3.OkHttpClient())
        val registry = com.laterbox.app.media.domain.adapter.AdapterRegistry(
            listOf(directAdapter, ytDlpAdapter, metadataAdapter)
        )
        com.laterbox.app.media.domain.adapter.PlatformResolver(registry)
    }
}
