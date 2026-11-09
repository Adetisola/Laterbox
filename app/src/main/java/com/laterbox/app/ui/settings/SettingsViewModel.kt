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
    private val backupManager: BackupManager,
    private val linkRepository: com.laterbox.app.data.repository.LinkRepository? = null,
    private val appContext: android.content.Context? = null
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

    fun setFontSizeScale(scale: com.laterbox.app.data.preferences.FontSizeScale) {
        viewModelScope.launch {
            userPreferencesRepository.setFontSizeScale(scale)
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

    fun setUserName(name: String) {
        viewModelScope.launch {
            userPreferencesRepository.setUserName(name)
        }
    }

    fun setSmartRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setSmartRemindersEnabled(enabled)
            appContext?.let { ctx ->
                if (enabled) {
                    com.laterbox.app.ai.DailyDigestWorker.enqueuePeriodic(ctx)
                } else {
                    com.laterbox.app.ai.DailyDigestWorker.cancelPeriodic(ctx)
                }
            }
        }
    }

    fun setAiEngine(engine: com.laterbox.app.data.preferences.AiEngine) {
        viewModelScope.launch {
            userPreferencesRepository.setAiEngine(engine)
        }
    }

    suspend fun sendTestSmartDigest(): Boolean {
        val ctx = appContext ?: return false
        val currentPrefs = preferences.value
        val targetLink = linkRepository?.getLatestUnreadLink()
            ?: com.laterbox.app.domain.model.SavedLink(
                id = 999_999L,
                originalUrl = "https://instagram.com/p/sample",
                normalizedUrl = "https://instagram.com/p/sample",
                title = "Design systems and typography in practice",
                domain = "instagram.com",
                siteName = "Instagram",
                contentType = com.laterbox.app.domain.model.ContentType.VIDEO,
                createdAt = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000L)
            )

        val copy = com.laterbox.app.ai.SmartDigestEngine.generateDigestCopy(
            link = targetLink,
            userName = currentPrefs.userName.ifBlank { null },
            engine = currentPrefs.aiEngine
        )

        return com.laterbox.app.reminders.NotificationHelper.showSmartDigestNotification(
            context = ctx,
            link = targetLink,
            copy = copy
        )
    }

    suspend fun exportBackup(outputStream: OutputStream): Result<Int> {
        return backupManager.exportToJson(outputStream)
    }

    suspend fun importBackup(inputStream: InputStream): Result<BackupRestoreResult> {
        return backupManager.importFromJson(inputStream)
    }
}
