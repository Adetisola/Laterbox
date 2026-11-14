package com.laterbox.app.di

import android.content.Context
import kotlinx.coroutines.flow.first
import com.laterbox.app.ai.LaterboxContextEngine
import com.laterbox.app.data.backup.BackupManager
import com.laterbox.app.data.local.LaterboxDatabase
import com.laterbox.app.data.preferences.UserPreferencesRepository
import com.laterbox.app.data.repository.ActivityEventRepository
import com.laterbox.app.data.repository.ChatRepository
import com.laterbox.app.data.repository.CollectionRepository
import com.laterbox.app.data.repository.LinkRepository
import com.laterbox.app.data.repository.NotificationHistoryRepository
import com.laterbox.app.diagnostics.AppHealthChecker
import com.laterbox.app.reminders.ContextualNotificationEngine

interface AppContainer {
    val database: LaterboxDatabase
    val linkRepository: LinkRepository
    val collectionRepository: CollectionRepository
    val userPreferencesRepository: UserPreferencesRepository
    val backupManager: BackupManager
    val reminderScheduler: com.laterbox.app.reminders.ReminderScheduler
    val notificationHistoryRepository: NotificationHistoryRepository
    val mediaRepository: com.laterbox.app.media.domain.repository.MediaRepository
    val mediaInspectionManager: com.laterbox.app.media.data.inspection.MediaInspectionManager
    val platformResolver: com.laterbox.app.media.domain.adapter.PlatformResolver
    val ytDlpRuntimeManager: com.laterbox.app.media.data.adapters.ytdlp.YtDlpRuntimeManager
    val ytDlpUpdater: com.laterbox.app.media.data.adapters.ytdlp.YtDlpUpdater
    val diagnosticsBuffer: com.laterbox.app.media.data.diagnostics.InspectionDiagnosticsBuffer
    val warmYtDlpServer: com.laterbox.app.media.data.adapters.ytdlp.WarmYtDlpServer
    val chatRepository: ChatRepository
    val activityEventRepository: ActivityEventRepository
    val appHealthChecker: AppHealthChecker
    val laterboxContextEngine: LaterboxContextEngine
    val contextualNotificationEngine: ContextualNotificationEngine
    val speechCoordinator: com.laterbox.app.ai.speech.SpeechTranscriptionCoordinator
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

    override val mediaInspectionManager: com.laterbox.app.media.data.inspection.MediaInspectionManager by lazy {
        com.laterbox.app.media.data.inspection.MediaInspectionManager(
            appContext = context.applicationContext,
            inspectionDao = database.mediaInspectionDao(),
            platformResolver = platformResolver,
            warmServer = warmYtDlpServer
        )
    }

    override val mediaRepository: com.laterbox.app.media.domain.repository.MediaRepository by lazy {
        com.laterbox.app.media.data.repository.MediaRepositoryImpl(
            context = context.applicationContext,
            assetDao = database.downloadedAssetDao(),
            taskDao = database.downloadTaskDao(),
            inspectionManager = mediaInspectionManager
        )
    }

    override val ytDlpRuntimeManager: com.laterbox.app.media.data.adapters.ytdlp.YtDlpRuntimeManager by lazy {
        com.laterbox.app.media.data.adapters.ytdlp.YtDlpRuntimeManager(context.applicationContext)
    }

    override val ytDlpUpdater: com.laterbox.app.media.data.adapters.ytdlp.YtDlpUpdater by lazy {
        com.laterbox.app.media.data.adapters.ytdlp.YtDlpUpdater(
            appContext = context.applicationContext,
            userPreferencesRepository = userPreferencesRepository,
            runtimeManager = ytDlpRuntimeManager
        )
    }

    override val diagnosticsBuffer: com.laterbox.app.media.data.diagnostics.InspectionDiagnosticsBuffer by lazy {
        com.laterbox.app.media.data.diagnostics.InspectionDiagnosticsBuffer()
    }

    override val warmYtDlpServer: com.laterbox.app.media.data.adapters.ytdlp.WarmYtDlpServer by lazy {
        com.laterbox.app.media.data.adapters.ytdlp.WarmYtDlpServer(
            appContext = context.applicationContext,
            userPreferencesRepository = userPreferencesRepository
        )
    }

    override val speechCoordinator: com.laterbox.app.ai.speech.SpeechTranscriptionCoordinator by lazy {
        com.laterbox.app.ai.speech.SpeechTranscriptionCoordinator(
            recorder = com.laterbox.app.ai.speech.AudioRecorderController(context.applicationContext),
            gemini = com.laterbox.app.ai.speech.GeminiTranscribeClient(),
            fallback = com.laterbox.app.ai.speech.AndroidSpeechRecognizerAdapter(context.applicationContext),
            scope = kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate
            ),
            apiKeyProvider = {
                userPreferencesRepository.userPreferencesFlow.first().geminiApiKey.ifBlank { null }
            }
        )
    }

    override val chatRepository: ChatRepository by lazy {
        ChatRepository(database.chatDao())
    }

    override val activityEventRepository: ActivityEventRepository by lazy {
        ActivityEventRepository(database.activityEventDao())
    }

    override val appHealthChecker: AppHealthChecker by lazy {
        AppHealthChecker(
            appContext = context.applicationContext,
            database = database,
            runtimeManager = ytDlpRuntimeManager,
            userPreferencesRepository = userPreferencesRepository
        )
    }

    override val laterboxContextEngine: LaterboxContextEngine by lazy {
        LaterboxContextEngine(
            database = database,
            activityEventRepository = activityEventRepository,
            userPreferencesRepository = userPreferencesRepository,
            appHealthChecker = appHealthChecker
        )
    }

    override val contextualNotificationEngine: ContextualNotificationEngine by lazy {
        ContextualNotificationEngine(
            appContext = context.applicationContext,
            database = database,
            userPreferencesRepository = userPreferencesRepository,
            activityEventRepository = activityEventRepository,
            appHealthChecker = appHealthChecker
        )
    }

    override val platformResolver: com.laterbox.app.media.domain.adapter.PlatformResolver by lazy {
        val directAdapter = com.laterbox.app.media.data.adapters.direct.DirectUrlAdapter(okhttp3.OkHttpClient())
        val spotifyDiscovery = com.laterbox.app.media.data.adapters.spotify.MusicDiscoveryProviderImpl(
            appContext = context.applicationContext,
            runtimeManager = ytDlpRuntimeManager
        )
        val spotifyAdapter = com.laterbox.app.media.data.adapters.spotify.SpotifyMusicAdapter(
            musicDiscoveryProvider = spotifyDiscovery
        )
        val ytDlpAdapter = com.laterbox.app.media.data.adapters.ytdlp.YtDlpAdapter(
            appContext = context.applicationContext,
            runtimeManager = ytDlpRuntimeManager,
            diagnosticsBuffer = diagnosticsBuffer,
            warmServer = warmYtDlpServer
        )
        val xAdapter = com.laterbox.app.media.data.adapters.x.XMediaAdapter(
            okHttpClient = okhttp3.OkHttpClient(),
            fallbackYtDlpAdapter = ytDlpAdapter
        )
        val metadataAdapter = com.laterbox.app.media.data.adapters.metadata.MetadataOnlyAdapter(okhttp3.OkHttpClient())
        val registry = com.laterbox.app.media.domain.adapter.AdapterRegistry(
            listOf(directAdapter, spotifyAdapter, xAdapter, ytDlpAdapter, metadataAdapter)
        )
        com.laterbox.app.media.domain.adapter.PlatformResolver(registry)
    }
}
