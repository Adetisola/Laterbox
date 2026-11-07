package com.laterbox.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laterbox.app.data.backup.BackupManager
import com.laterbox.app.data.backup.BackupRestoreResult
import com.laterbox.app.data.preferences.ThemeMode
import com.laterbox.app.data.preferences.UserPreferences
import com.laterbox.app.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream

class SettingsViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val backupManager: BackupManager
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = userPreferencesRepository.userPreferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserPreferences()
    )

    fun setThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            userPreferencesRepository.setThemeMode(themeMode)
        }
    }

    fun setAutoMarkAsRead(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAutoMarkAsRead(enabled)
        }
    }

    fun setAllowSystemBackup(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAllowSystemBackup(enabled)
        }
    }

    suspend fun exportBackup(outputStream: OutputStream): Result<Int> {
        return backupManager.exportToJson(outputStream)
    }

    suspend fun importBackup(inputStream: InputStream): Result<BackupRestoreResult> {
        return backupManager.importFromJson(inputStream)
    }
}
