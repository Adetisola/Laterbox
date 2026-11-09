package com.laterbox.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laterbox.app.data.backup.BackupManager
import com.laterbox.app.data.backup.BackupRestoreResult
import com.laterbox.app.data.preferences.ThemeMode
import com.laterbox.app.data.preferences.UserPreferences
import com.laterbox.app.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _aiConnectionStatus = MutableStateFlow<Pair<Boolean, String>?>(null)
    val aiConnectionStatus: StateFlow<Pair<Boolean, String>?> = _aiConnectionStatus.asStateFlow()

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
                val scheduler = com.laterbox.app.reminders.ReminderScheduler(ctx)
                if (enabled) {
                    val p = preferences.value
                    scheduler.scheduleSmartDigest(
                        targetHour = p.smartReminderHour,
                        targetMinute = p.smartReminderMinute,
                        schedule = p.smartReminderSchedule
                    )
                    com.laterbox.app.ai.DailyDigestWorker.enqueuePeriodic(
                        context = ctx,
                        schedule = p.smartReminderSchedule,
                        targetHour = p.smartReminderHour,
                        targetMinute = p.smartReminderMinute
                    )
                } else {
                    scheduler.cancelSmartDigest()
                    com.laterbox.app.ai.DailyDigestWorker.cancelPeriodic(ctx)
                }
            }
        }
    }

    fun setSmartReminderSchedule(schedule: com.laterbox.app.data.preferences.SmartReminderSchedule) {
        viewModelScope.launch {
            userPreferencesRepository.setSmartReminderSchedule(schedule)
            appContext?.let { ctx ->
                if (preferences.value.smartRemindersEnabled) {
                    val p = preferences.value
                    com.laterbox.app.reminders.ReminderScheduler(ctx).scheduleSmartDigest(
                        targetHour = p.smartReminderHour,
                        targetMinute = p.smartReminderMinute,
                        schedule = schedule
                    )
                    com.laterbox.app.ai.DailyDigestWorker.enqueuePeriodic(
                        context = ctx,
                        schedule = schedule,
                        targetHour = p.smartReminderHour,
                        targetMinute = p.smartReminderMinute
                    )
                }
            }
        }
    }

    fun setSmartReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setSmartReminderTime(hour, minute)
            appContext?.let { ctx ->
                if (preferences.value.smartRemindersEnabled) {
                    val p = preferences.value
                    com.laterbox.app.reminders.ReminderScheduler(ctx).scheduleSmartDigest(
                        targetHour = hour,
                        targetMinute = minute,
                        schedule = p.smartReminderSchedule
                    )
                    com.laterbox.app.ai.DailyDigestWorker.enqueuePeriodic(
                        context = ctx,
                        schedule = p.smartReminderSchedule,
                        targetHour = hour,
                        targetMinute = minute
                    )
                }
            }
        }
    }

    fun setSmartReminderPersistence(persistence: com.laterbox.app.data.preferences.SmartReminderPersistence) {
        viewModelScope.launch {
            userPreferencesRepository.setSmartReminderPersistence(persistence)
        }
    }

    fun setAiEngine(engine: com.laterbox.app.data.preferences.AiEngine) {
        viewModelScope.launch {
            userPreferencesRepository.setAiEngine(engine)
        }
    }

    fun setGeminiApiKey(apiKey: String) {
        viewModelScope.launch {
            userPreferencesRepository.setGeminiApiKey(apiKey)
            _aiConnectionStatus.value = null
        }
    }

    fun testAiConnection() {
        viewModelScope.launch {
            _aiConnectionStatus.value = Pair(false, "Testing connection...")
            val currentKey = preferences.value.geminiApiKey
            val result = com.laterbox.app.ai.SmartDigestEngine.testGeminiApi(currentKey)
            _aiConnectionStatus.value = result
        }
    }

    suspend fun sendTestSmartDigest(): Boolean {
        val ctx = appContext ?: return false
        val currentPrefs = preferences.value
        val targetLink = linkRepository?.getSmartDigestCandidate()
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
            origin = com.laterbox.app.ai.ReminderOrigin.SMART_DIGEST,
            engine = currentPrefs.aiEngine,
            customApiKey = currentPrefs.geminiApiKey.ifBlank { null }
        )

        return com.laterbox.app.reminders.NotificationHelper.showReminderNotification(
            context = ctx,
            link = targetLink,
            userName = currentPrefs.userName.ifBlank { null },
            origin = com.laterbox.app.ai.ReminderOrigin.SMART_DIGEST,
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
