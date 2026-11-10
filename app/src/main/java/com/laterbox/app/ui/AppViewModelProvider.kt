package com.laterbox.app.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.laterbox.app.LaterboxApplication
import com.laterbox.app.ui.archive.ArchiveViewModel
import com.laterbox.app.ui.collections.CollectionDetailViewModel
import com.laterbox.app.ui.collections.CollectionsViewModel
import com.laterbox.app.ui.inbox.InboxViewModel
import com.laterbox.app.ui.settings.SettingsViewModel
import com.laterbox.app.ui.sheets.SaveLinkViewModel
import com.laterbox.app.ui.sheets.SavedItemDetailViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            val app = laterboxApplication()
            InboxViewModel(
                linkRepository = app.container.linkRepository,
                userPreferencesRepository = app.container.userPreferencesRepository,
                notificationHistoryRepository = app.container.notificationHistoryRepository
            )
        }
        initializer {
            val app = laterboxApplication()
            com.laterbox.app.ui.notifications.NotificationHistoryViewModel(
                app.container.notificationHistoryRepository
            )
        }
        initializer {
            val app = laterboxApplication()
            CollectionsViewModel(app.container.collectionRepository)
        }
        initializer {
            val app = laterboxApplication()
            CollectionDetailViewModel(app.container.linkRepository)
        }
        initializer {
            val app = laterboxApplication()
            ArchiveViewModel(app.container.linkRepository)
        }
        initializer {
            val app = laterboxApplication()
            SaveLinkViewModel(app.container.linkRepository, app.container.collectionRepository)
        }
        initializer {
            val app = laterboxApplication()
            SavedItemDetailViewModel(
                linkRepository = app.container.linkRepository,
                collectionRepository = app.container.collectionRepository,
                mediaRepository = app.container.mediaRepository,
                platformResolver = app.container.platformResolver
            )
        }
        initializer {
            val app = laterboxApplication()
            com.laterbox.app.media.ui.downloads.DownloadsViewModel(app.container.mediaRepository)
        }
        initializer {
            val app = laterboxApplication()
            SettingsViewModel(
                userPreferencesRepository = app.container.userPreferencesRepository,
                backupManager = app.container.backupManager,
                linkRepository = app.container.linkRepository,
                appContext = app.applicationContext,
                ytDlpUpdater = app.container.ytDlpUpdater
            )
        }
    }
}

fun CreationExtras.laterboxApplication(): LaterboxApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LaterboxApplication)
