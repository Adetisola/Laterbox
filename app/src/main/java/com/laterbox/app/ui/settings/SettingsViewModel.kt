package com.laterbox.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laterbox.app.ai.AiCopyResult
import com.laterbox.app.ai.ReminderOrigin
import com.laterbox.app.ai.SmartDigestEngine
import com.laterbox.app.data.backup.BackupManager
import com.laterbox.app.data.backup.BackupRestoreResult
import com.laterbox.app.data.preferences.FontSizeScale
import com.laterbox.app.data.preferences.SmartReminderPersistence
import com.laterbox.app.data.preferences.ThemeMode
import com.laterbox.app.data.preferences.UserPreferences
import com.laterbox.app.data.preferences.UserPreferencesRepository
import com.laterbox.app.data.repository.LinkRepository
import com.laterbox.app.domain.model.ContentType
import com.laterbox.app.domain.model.SavedLink
import com.laterbox.app.reminders.NotificationHelper
import com.laterbox.app.reminders.smart.SmartReminderCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream

class SettingsViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val backupManager: BackupManager,
    private val linkRepository: LinkRepository? = null,
    private val appContext: Context? = null,
    private val ytDlpUpdater: com.laterbox.app.media.data.adapters.ytdlp.YtDlpUpdater? = null,
    private val ytDlpRuntimeManager: com.laterbox.app.media.data.adapters.ytdlp.YtDlpRuntimeManager? = null,
    private val diagnosticsBuffer: com.laterbox.app.media.data.diagnostics.InspectionDiagnosticsBuffer? = null
) : ViewModel() {

    private val runtimeManager: com.laterbox.app.media.data.adapters.ytdlp.YtDlpRuntimeManager? =
        ytDlpRuntimeManager ?: appContext?.let { ctx ->
            (ctx.applicationContext as? com.laterbox.app.LaterboxApplication)?.container?.ytDlpRuntimeManager
                ?: com.laterbox.app.media.data.adapters.ytdlp.YtDlpRuntimeManager(ctx)
        }

    val ytDlpState: StateFlow<com.laterbox.app.media.data.adapters.ytdlp.YtDlpInitState> =
        runtimeManager?.ytDlpState ?: MutableStateFlow(com.laterbox.app.media.data.adapters.ytdlp.YtDlpInitState.NotInitialized).asStateFlow()

    val ffmpegState: StateFlow<com.laterbox.app.media.data.adapters.ytdlp.FfmpegInitState> =
        runtimeManager?.ffmpegState ?: MutableStateFlow(com.laterbox.app.media.data.adapters.ytdlp.FfmpegInitState.NotInitialized).asStateFlow()

    val preferences: StateFlow<UserPreferences> = userPreferencesRepository.userPreferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserPreferences()
    )

    private val _aiConnectionStatus = MutableStateFlow<Pair<Boolean, String>?>(null)
    val aiConnectionStatus: StateFlow<Pair<Boolean, String>?> = _aiConnectionStatus.asStateFlow()

    private val _isUpdatingYtDlp = MutableStateFlow(false)
    val isUpdatingYtDlp: StateFlow<Boolean> = _isUpdatingYtDlp.asStateFlow()

    fun setThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            userPreferencesRepository.setThemeMode(themeMode)
        }
    }

    fun setFontSizeScale(scale: FontSizeScale) {
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

    fun setAccessibilityCaptureEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAccessibilityCaptureEnabled(enabled)
        }
    }

    fun setClipboardSuggestionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setClipboardSuggestionEnabled(enabled)
        }
    }

    fun toggleExcludedCapturePackage(packageName: String) {
        viewModelScope.launch {
            userPreferencesRepository.toggleExcludedCapturePackage(packageName)
        }
    }

    private suspend fun syncReminders() {
        appContext?.let { ctx ->
            val p = userPreferencesRepository.userPreferencesFlow.first()
            SmartReminderCoordinator.sync(ctx, p.toSmartReminderSettings())
        }
    }

    fun setSmartRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setSmartRemindersEnabled(enabled)
            syncReminders()
        }
    }

    fun setMorningEveningEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setMorningEveningEnabled(enabled)
            syncReminders()
        }
    }

    fun setMorningTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setMorningTime(hour, minute)
            syncReminders()
        }
    }

    fun setEveningTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setEveningTime(hour, minute)
            syncReminders()
        }
    }

    fun setWeekendRoundupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setWeekendRoundupEnabled(enabled)
            syncReminders()
        }
    }

    fun setWeekendRoundupTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setWeekendRoundupTime(hour, minute)
            syncReminders()
        }
    }

    fun setDailyDigestEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDailyDigestEnabled(enabled)
            syncReminders()
        }
    }

    fun setDailyDigestTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setDailyDigestTime(hour, minute)
            syncReminders()
        }
    }

    fun setDailyDigestWeekdaysOnly(weekdaysOnly: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDailyDigestWeekdaysOnly(weekdaysOnly)
            syncReminders()
        }
    }

    fun setCustomScheduleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setCustomScheduleEnabled(enabled)
            syncReminders()
        }
    }

    fun setCustomScheduleDays(days: Set<Int>) {
        viewModelScope.launch {
            userPreferencesRepository.setCustomScheduleDays(days)
            syncReminders()
        }
    }

    fun setCustomScheduleTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setCustomScheduleTime(hour, minute)
            syncReminders()
        }
    }

    fun setSmartReminderPersistence(persistence: SmartReminderPersistence) {
        viewModelScope.launch {
            userPreferencesRepository.setSmartReminderPersistence(persistence)
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
            val result = SmartDigestEngine.testGeminiApi(currentKey)
            _aiConnectionStatus.value = result
        }
    }

    suspend fun sendTestSmartDigest(): Boolean {
        val ctx = appContext ?: return false
        val currentPrefs = preferences.value
        val targetLink = linkRepository?.getSmartDigestCandidate()
            ?: SavedLink(
                id = 999_999L,
                originalUrl = "https://instagram.com/p/sample",
                normalizedUrl = "https://instagram.com/p/sample",
                title = "Design systems and typography in practice",
                domain = "instagram.com",
                siteName = "Instagram",
                contentType = ContentType.VIDEO,
                createdAt = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000L)
            )

        val result = SmartDigestEngine.generateDigestCopy(
            link = targetLink,
            userName = currentPrefs.userName.ifBlank { null },
            origin = ReminderOrigin.SMART_DIGEST,
            customApiKey = currentPrefs.geminiApiKey.ifBlank { null }
        )

        return when (result) {
            is AiCopyResult.Success -> {
                NotificationHelper.showReminderNotification(
                    context = ctx,
                    link = targetLink,
                    userName = currentPrefs.userName.ifBlank { null },
                    origin = ReminderOrigin.SMART_DIGEST,
                    copy = result.copy
                )
                true
            }
            is AiCopyResult.Failure -> {
                _aiConnectionStatus.value = Pair(false, "Test digest failed: ${result.reason.userMessage}")
                false
            }
        }
    }

    suspend fun exportBackup(outputStream: OutputStream): Result<Int> {
        return backupManager.exportToJson(outputStream)
    }

    suspend fun importBackup(inputStream: InputStream): Result<BackupRestoreResult> {
        return backupManager.importFromJson(inputStream)
    }

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _updateCheckResult = MutableStateFlow<com.laterbox.app.update.AppUpdateInfo?>(null)
    val updateCheckResult: StateFlow<com.laterbox.app.update.AppUpdateInfo?> = _updateCheckResult.asStateFlow()

    fun checkForUpdates(
        currentVersion: String = com.laterbox.app.BuildConfig.VERSION_NAME,
        onResult: (String) -> Unit = {}
    ) {
        if (_isCheckingUpdate.value) return
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            try {
                userPreferencesRepository.recordUpdateCheck()
                appContext?.let { ctx ->
                    try {
                        com.laterbox.app.update.ApkInstallerHelper.cleanupOldUpdateApks(ctx, currentVersion)
                    } catch (_: Exception) {}
                }
                when (val result = com.laterbox.app.update.GitHubUpdateChecker.checkForUpdate(currentVersion)) {
                    is com.laterbox.app.update.UpdateCheckResult.UpdateAvailable -> {
                        _updateCheckResult.value = result.updateInfo
                    }
                    is com.laterbox.app.update.UpdateCheckResult.UpToDate -> {
                        onResult("Laterbox is up to date (v$currentVersion)")
                    }
                    is com.laterbox.app.update.UpdateCheckResult.NetworkError -> {
                        onResult("Network error: ${result.message}")
                    }
                    is com.laterbox.app.update.UpdateCheckResult.ApiError -> {
                        onResult("Update check failed (${result.statusCode}): ${result.message}")
                    }
                    is com.laterbox.app.update.UpdateCheckResult.InvalidRelease -> {
                        onResult("Could not parse release info: ${result.message}")
                    }
                    is com.laterbox.app.update.UpdateCheckResult.NoCompatibleApk -> {
                        onResult("Version ${result.releaseVersion} is available, but no APK matching your device (${result.deviceAbis.joinToString()}) was found.")
                    }
                }
            } catch (e: Exception) {
                onResult("Could not check for updates: ${e.message}")
            } finally {
                _isCheckingUpdate.value = false
            }
        }
    }

    fun dismissUpdateDialog() {
        _updateCheckResult.value = null
    }

    fun postponeUpdate(version: String) {
        _updateCheckResult.value = null
        viewModelScope.launch {
            userPreferencesRepository.dismissUpdate(version)
        }
    }

    fun setMediaDownloadNetworkPolicy(policy: com.laterbox.app.data.preferences.MediaDownloadNetworkPolicy) {
        viewModelScope.launch {
            userPreferencesRepository.setMediaDownloadNetworkPolicy(policy)
        }
    }

    fun setAutoInspectMediaOnSave(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAutoInspectMediaOnSave(enabled)
        }
    }

    fun updateYtDlp(onResult: (String) -> Unit) {
        if (_isUpdatingYtDlp.value) return
        _isUpdatingYtDlp.value = true
        viewModelScope.launch {
            try {
                val updater = ytDlpUpdater ?: appContext?.let { ctx ->
                    com.laterbox.app.media.data.adapters.ytdlp.YtDlpUpdater(ctx, userPreferencesRepository)
                }
                if (updater == null) {
                    onResult("Updater not available")
                    return@launch
                }
                val result = updater.forceUpdate()
                if (result.isSuccess) {
                    val version = result.getOrThrow()
                    onResult("yt-dlp updated ($version)")
                } else {
                    onResult("Failed to update yt-dlp: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                onResult("Error updating yt-dlp: ${e.message}")
            } finally {
                _isUpdatingYtDlp.value = false
            }
        }
    }

    fun setFastEngineEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setFastEngineEnabled(enabled)
        }
    }

    fun getDiagnosticReport(): com.laterbox.app.media.data.adapters.ytdlp.YtDlpDiagnosticReport? {
        return runtimeManager?.captureDiagnosticReport()
    }

    fun getDiagnosticsReportText(): String {
        val runtimeText = runtimeManager?.captureDiagnosticReport()?.toFormattedString() ?: "No runtime diagnostic report available."
        val inspectionsText = diagnosticsBuffer?.exportAsText() ?: "No inspection timings recorded yet."
        return """
            $runtimeText

            ========================================
            INSPECTION TIMING DIAGNOSTICS (LAST 50)
            ========================================

            $inspectionsText
        """.trimIndent()
    }

    fun retryInitializeEngine(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val mgr = runtimeManager
            if (mgr == null) {
                onResult("Runtime manager not available")
                return@launch
            }
            val ytResult = mgr.ensureYtDlpInitialized()
            if (ytResult.isSuccess) {
                val ffmpegResult = mgr.ensureFfmpegInitialized()
                if (ffmpegResult.isSuccess) {
                    onResult("Media engine (yt-dlp + FFmpeg) initialized successfully!")
                } else {
                    onResult("yt-dlp initialized, but FFmpeg failed: ${ffmpegResult.exceptionOrNull()?.message}")
                }
            } else {
                onResult("Initialization failed: ${ytResult.exceptionOrNull()?.message}")
            }
        }
    }

    fun setCustomExportDirectory(uri: String, name: String) {
        viewModelScope.launch {
            userPreferencesRepository.setCustomExportDirectory(uri, name)
        }
    }

    fun clearCustomExportDirectory() {
        viewModelScope.launch {
            userPreferencesRepository.clearCustomExportDirectory()
        }
    }
}

