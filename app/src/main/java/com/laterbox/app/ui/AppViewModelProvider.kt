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
            InboxViewModel(app.container.linkRepository, app.container.userPreferencesRepository)
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
            SavedItemDetailViewModel(app.container.linkRepository, app.container.collectionRepository)
        }
        initializer {
            val app = laterboxApplication()
            SettingsViewModel(app.container.userPreferencesRepository, app.container.backupManager)
        }
    }
}

fun CreationExtras.laterboxApplication(): LaterboxApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LaterboxApplication)
