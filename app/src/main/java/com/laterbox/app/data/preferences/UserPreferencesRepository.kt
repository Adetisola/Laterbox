package com.laterbox.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.laterbox.app.reminders.smart.CustomScheduleConfig
import com.laterbox.app.reminders.smart.DailyDigestConfig
import com.laterbox.app.reminders.smart.MorningEveningConfig
import com.laterbox.app.reminders.smart.SmartReminderSettings
import com.laterbox.app.reminders.smart.WeekendConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class InboxViewMode {
    HERO,
    COMPACT,
    EDITORIAL
}

enum class FontSizeScale(val scaleFactor: Float, val label: String) {
    SMALL(0.85f, "Small"),
    SYSTEM(1.0f, "System"),
    LARGE(1.15f, "Large")
}

@Deprecated("Migrated to independent schedule toggles in SmartReminderSettings")
enum class SmartReminderSchedule(val label: String, val description: String) {
    DAILY_DIGEST("Daily Digest", "Once a day at your chosen time"),
    TWICE_DAILY("Morning & Evening", "Twice a day at 9:00 AM and 8:00 PM"),
    WEEKEND_ROUNDUP("Weekend Roundup", "Every Saturday & Sunday morning"),
    CONTINUOUS_MONITOR("Continuous Monitor", "Monitors unread links and nudges when idle")
}

enum class SmartReminderPersistence(val maxNudges: Int, val label: String) {
    ONCE(1, "1 time"),
    TWICE(2, "2 times"),
    THRICE(3, "3 times"),
    UNTIL_READ(Int.MAX_VALUE, "Until read")
}

enum class MediaDownloadNetworkPolicy(val label: String) {
    WIFI_ONLY("Wi-Fi only"),
    WIFI_OVER_20MB("Wi-Fi for files over 20 MB"),
    ANY_NETWORK("Any network")
}

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoMarkAsRead: Boolean = true,
    val includeInBackup: Boolean = true,
    val defaultCollectionId: Long? = null,
    val inboxViewMode: InboxViewMode = InboxViewMode.HERO,
    val fontSizeScale: FontSizeScale = FontSizeScale.SYSTEM,
    val hasCompletedOnboarding: Boolean = false,
    val userName: String = "",
    val smartRemindersEnabled: Boolean = true,
    // Morning & Evening schedule
    val morningEveningEnabled: Boolean = false,
    val morningHour: Int = 9,
    val morningMinute: Int = 0,
    val eveningHour: Int = 20,
    val eveningMinute: Int = 0,
    // Weekend Roundup schedule
    val weekendRoundupEnabled: Boolean = false,
    val weekendRoundupHour: Int = 10,
    val weekendRoundupMinute: Int = 0,
    // Daily Digest schedule
    val dailyDigestEnabled: Boolean = true,
    val dailyDigestHour: Int = 20,
    val dailyDigestMinute: Int = 0,
    val dailyDigestWeekdaysOnly: Boolean = false,
    // Custom schedule
    val customScheduleEnabled: Boolean = false,
    val customScheduleDays: Set<Int> = setOf(1, 2, 3, 4, 5),
    val customScheduleHour: Int = 12,
    val customScheduleMinute: Int = 0,
    // Persistence & API key
    val smartReminderPersistence: SmartReminderPersistence = SmartReminderPersistence.TWICE,
    val geminiApiKey: String = "",
    val deliveredSlotKeys: Set<String> = emptySet(),
    val accessibilityCaptureEnabled: Boolean = false,
    val clipboardSuggestionEnabled: Boolean = true,
    val recentClipboardHistory: Set<String> = emptySet(),
    val excludedCapturePackages: Set<String> = emptySet(),
    val overlayXRatio: Float = 0.5f,
    val overlayYRatio: Float = 0.82f,
    val lastUpdateCheckTime: Long = 0L,
    val dismissedUpdateVersion: String = "",
    val dismissedUpdateTimestamp: Long = 0L,
    val mediaDownloadNetworkPolicy: MediaDownloadNetworkPolicy = MediaDownloadNetworkPolicy.WIFI_OVER_20MB,
    val autoInspectMediaOnSave: Boolean = true,
    val ytDlpVersion: String = "",
    val lastYtDlpUpdateCheck: Long = 0L,
    val customExportDirectoryUri: String? = null,
    val customExportDirectoryName: String? = null
) {
    val allowSystemBackup: Boolean get() = includeInBackup

    fun toSmartReminderSettings(): SmartReminderSettings = SmartReminderSettings(
        enabled = smartRemindersEnabled,
        morningEvening = MorningEveningConfig(
            enabled = morningEveningEnabled,
            morningHour = morningHour,
            morningMinute = morningMinute,
            eveningHour = eveningHour,
            eveningMinute = eveningMinute
        ),
        weekendRoundup = WeekendConfig(
            enabled = weekendRoundupEnabled,
            hour = weekendRoundupHour,
            minute = weekendRoundupMinute
        ),
        dailyDigest = DailyDigestConfig(
            enabled = dailyDigestEnabled,
            hour = dailyDigestHour,
            minute = dailyDigestMinute,
            weekdaysOnly = dailyDigestWeekdaysOnly
        ),
        custom = CustomScheduleConfig(
            enabled = customScheduleEnabled,
            daysOfWeek = customScheduleDays,
            hour = customScheduleHour,
            minute = customScheduleMinute
        ),
        persistence = smartReminderPersistence
    )
}

val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUTO_MARK_AS_READ = booleanPreferencesKey("auto_mark_as_read")
        val INCLUDE_IN_BACKUP = booleanPreferencesKey("include_in_backup")
        val DEFAULT_COLLECTION_ID = longPreferencesKey("default_collection_id")
        val INBOX_VIEW_MODE = stringPreferencesKey("inbox_view_mode")
        val FONT_SIZE_SCALE = stringPreferencesKey("font_size_scale")
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val USER_NAME = stringPreferencesKey("user_name")
        val SMART_REMINDERS_ENABLED = booleanPreferencesKey("smart_reminders_enabled")
        val SCHEDULES_MIGRATED_V2 = booleanPreferencesKey("schedules_migrated_v2")

        // Legacy schedule keys for migration
        val LEGACY_SMART_REMINDER_SCHEDULE = stringPreferencesKey("smart_reminder_schedule")
        val LEGACY_SMART_REMINDER_HOUR = intPreferencesKey("smart_reminder_hour")
        val LEGACY_SMART_REMINDER_MINUTE = intPreferencesKey("smart_reminder_minute")

        // Morning & Evening
        val MORNING_EVENING_ENABLED = booleanPreferencesKey("morning_evening_enabled")
        val MORNING_HOUR = intPreferencesKey("morning_hour")
        val MORNING_MINUTE = intPreferencesKey("morning_minute")
        val EVENING_HOUR = intPreferencesKey("evening_hour")
        val EVENING_MINUTE = intPreferencesKey("evening_minute")

        // Weekend Roundup
        val WEEKEND_ROUNDUP_ENABLED = booleanPreferencesKey("weekend_roundup_enabled")
        val WEEKEND_ROUNDUP_HOUR = intPreferencesKey("weekend_roundup_hour")
        val WEEKEND_ROUNDUP_MINUTE = intPreferencesKey("weekend_roundup_minute")

        // Daily Digest
        val DAILY_DIGEST_ENABLED = booleanPreferencesKey("daily_digest_enabled")
        val DAILY_DIGEST_HOUR = intPreferencesKey("daily_digest_hour")
        val DAILY_DIGEST_MINUTE = intPreferencesKey("daily_digest_minute")
        val DAILY_DIGEST_WEEKDAYS_ONLY = booleanPreferencesKey("daily_digest_weekdays_only")

        // Custom Schedule
        val CUSTOM_SCHEDULE_ENABLED = booleanPreferencesKey("custom_schedule_enabled")
        val CUSTOM_SCHEDULE_DAYS = stringSetPreferencesKey("custom_schedule_days")
        val CUSTOM_SCHEDULE_HOUR = intPreferencesKey("custom_schedule_hour")
        val CUSTOM_SCHEDULE_MINUTE = intPreferencesKey("custom_schedule_minute")

        val SMART_REMINDER_PERSISTENCE = stringPreferencesKey("smart_reminder_persistence")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val DELIVERED_SLOT_KEYS = stringSetPreferencesKey("delivered_slot_keys")
        val ACCESSIBILITY_CAPTURE_ENABLED = booleanPreferencesKey("accessibility_capture_enabled")
        val CLIPBOARD_SUGGESTION_ENABLED = booleanPreferencesKey("clipboard_suggestion_enabled")
        val RECENT_CLIPBOARD_HISTORY = stringSetPreferencesKey("recent_clipboard_history")
        val EXCLUDED_CAPTURE_PACKAGES = stringSetPreferencesKey("excluded_capture_packages")
        val OVERLAY_X_RATIO = floatPreferencesKey("overlay_x_ratio")
        val OVERLAY_Y_RATIO = floatPreferencesKey("overlay_y_ratio")
        val LAST_UPDATE_CHECK_TIME = longPreferencesKey("last_update_check_time")
        val DISMISSED_UPDATE_VERSION = stringPreferencesKey("dismissed_update_version")
        val DISMISSED_UPDATE_TIMESTAMP = longPreferencesKey("dismissed_update_timestamp")
        val MEDIA_DOWNLOAD_NETWORK_POLICY = stringPreferencesKey("media_download_network_policy")
        val AUTO_INSPECT_MEDIA_ON_SAVE = booleanPreferencesKey("auto_inspect_media_on_save")
        val YTDLP_VERSION = stringPreferencesKey("ytdlp_version")
        val LAST_YTDLP_UPDATE_CHECK = longPreferencesKey("last_ytdlp_update_check")
        val CUSTOM_EXPORT_DIRECTORY_URI = stringPreferencesKey("custom_export_directory_uri")
        val CUSTOM_EXPORT_DIRECTORY_NAME = stringPreferencesKey("custom_export_directory_name")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.userDataStore.data.map { preferences ->
        val themeModeStr = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val themeMode = try {
            ThemeMode.valueOf(themeModeStr)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }

        val autoMarkAsRead = preferences[PreferencesKeys.AUTO_MARK_AS_READ] ?: true
        val includeInBackup = preferences[PreferencesKeys.INCLUDE_IN_BACKUP] ?: true
        val defaultCollectionId = preferences[PreferencesKeys.DEFAULT_COLLECTION_ID]

        val viewModeStr = preferences[PreferencesKeys.INBOX_VIEW_MODE] ?: InboxViewMode.HERO.name
        val inboxViewMode = try {
            InboxViewMode.valueOf(viewModeStr)
        } catch (_: Exception) {
            InboxViewMode.HERO
        }

        val fontScaleStr = preferences[PreferencesKeys.FONT_SIZE_SCALE] ?: FontSizeScale.SYSTEM.name
        val fontSizeScale = try {
            FontSizeScale.valueOf(fontScaleStr)
        } catch (_: Exception) {
            FontSizeScale.SYSTEM
        }

        val hasCompletedOnboarding = preferences[PreferencesKeys.HAS_COMPLETED_ONBOARDING] ?: false
        val userName = preferences[PreferencesKeys.USER_NAME] ?: ""
        val smartRemindersEnabled = preferences[PreferencesKeys.SMART_REMINDERS_ENABLED] ?: true

        // Schedule migration logic
        val isMigrated = preferences[PreferencesKeys.SCHEDULES_MIGRATED_V2] ?: false
        val legacySchedule = preferences[PreferencesKeys.LEGACY_SMART_REMINDER_SCHEDULE]
        val legacyHour = preferences[PreferencesKeys.LEGACY_SMART_REMINDER_HOUR] ?: 20
        val legacyMinute = preferences[PreferencesKeys.LEGACY_SMART_REMINDER_MINUTE] ?: 0

        val morningEveningEnabled: Boolean
        val weekendRoundupEnabled: Boolean
        val dailyDigestEnabled: Boolean
        val dailyDigestHour: Int
        val dailyDigestMinute: Int

        if (!isMigrated && legacySchedule != null) {
            when (legacySchedule) {
                "TWICE_DAILY" -> {
                    morningEveningEnabled = true
                    weekendRoundupEnabled = false
                    dailyDigestEnabled = false
                    dailyDigestHour = legacyHour
                    dailyDigestMinute = legacyMinute
                }
                "WEEKEND_ROUNDUP" -> {
                    morningEveningEnabled = false
                    weekendRoundupEnabled = true
                    dailyDigestEnabled = false
                    dailyDigestHour = legacyHour
                    dailyDigestMinute = legacyMinute
                }
                else -> {
                    morningEveningEnabled = false
                    weekendRoundupEnabled = false
                    dailyDigestEnabled = true
                    dailyDigestHour = legacyHour
                    dailyDigestMinute = legacyMinute
                }
            }
        } else {
            morningEveningEnabled = preferences[PreferencesKeys.MORNING_EVENING_ENABLED] ?: false
            weekendRoundupEnabled = preferences[PreferencesKeys.WEEKEND_ROUNDUP_ENABLED] ?: false
            dailyDigestEnabled = preferences[PreferencesKeys.DAILY_DIGEST_ENABLED] ?: true
            dailyDigestHour = preferences[PreferencesKeys.DAILY_DIGEST_HOUR] ?: 20
            dailyDigestMinute = preferences[PreferencesKeys.DAILY_DIGEST_MINUTE] ?: 0
        }

        val morningHour = preferences[PreferencesKeys.MORNING_HOUR] ?: 9
        val morningMinute = preferences[PreferencesKeys.MORNING_MINUTE] ?: 0
        val eveningHour = preferences[PreferencesKeys.EVENING_HOUR] ?: 20
        val eveningMinute = preferences[PreferencesKeys.EVENING_MINUTE] ?: 0

        val weekendRoundupHour = preferences[PreferencesKeys.WEEKEND_ROUNDUP_HOUR] ?: 10
        val weekendRoundupMinute = preferences[PreferencesKeys.WEEKEND_ROUNDUP_MINUTE] ?: 0

        val dailyDigestWeekdaysOnly = preferences[PreferencesKeys.DAILY_DIGEST_WEEKDAYS_ONLY] ?: false

        val customScheduleEnabled = preferences[PreferencesKeys.CUSTOM_SCHEDULE_ENABLED] ?: false
        val customDaysRaw = preferences[PreferencesKeys.CUSTOM_SCHEDULE_DAYS] ?: setOf("1", "2", "3", "4", "5")
        val customScheduleDays = customDaysRaw.mapNotNull { it.toIntOrNull() }.toSet()
        val customScheduleHour = preferences[PreferencesKeys.CUSTOM_SCHEDULE_HOUR] ?: 12
        val customScheduleMinute = preferences[PreferencesKeys.CUSTOM_SCHEDULE_MINUTE] ?: 0

        val persistenceStr = preferences[PreferencesKeys.SMART_REMINDER_PERSISTENCE] ?: SmartReminderPersistence.TWICE.name
        val smartReminderPersistence = try {
            SmartReminderPersistence.valueOf(persistenceStr)
        } catch (_: Exception) {
            SmartReminderPersistence.TWICE
        }

        val geminiApiKey = preferences[PreferencesKeys.GEMINI_API_KEY] ?: ""
        val deliveredSlotKeys = preferences[PreferencesKeys.DELIVERED_SLOT_KEYS] ?: emptySet()

        val accessibilityCaptureEnabled = preferences[PreferencesKeys.ACCESSIBILITY_CAPTURE_ENABLED] ?: false
        val clipboardSuggestionEnabled = preferences[PreferencesKeys.CLIPBOARD_SUGGESTION_ENABLED] ?: true
        val recentClipboardHistory = preferences[PreferencesKeys.RECENT_CLIPBOARD_HISTORY] ?: emptySet()
        val excludedCapturePackages = preferences[PreferencesKeys.EXCLUDED_CAPTURE_PACKAGES] ?: emptySet()
        val overlayXRatio = preferences[PreferencesKeys.OVERLAY_X_RATIO] ?: 0.5f
        val overlayYRatio = preferences[PreferencesKeys.OVERLAY_Y_RATIO] ?: 0.82f
        val lastUpdateCheckTime = preferences[PreferencesKeys.LAST_UPDATE_CHECK_TIME] ?: 0L
        val dismissedUpdateVersion = preferences[PreferencesKeys.DISMISSED_UPDATE_VERSION] ?: ""
        val dismissedUpdateTimestamp = preferences[PreferencesKeys.DISMISSED_UPDATE_TIMESTAMP] ?: 0L
        val mediaPolicyStr = preferences[PreferencesKeys.MEDIA_DOWNLOAD_NETWORK_POLICY] ?: MediaDownloadNetworkPolicy.WIFI_OVER_20MB.name
        val mediaDownloadNetworkPolicy = try {
            MediaDownloadNetworkPolicy.valueOf(mediaPolicyStr)
        } catch (_: Exception) {
            MediaDownloadNetworkPolicy.WIFI_OVER_20MB
        }
        val autoInspectMediaOnSave = preferences[PreferencesKeys.AUTO_INSPECT_MEDIA_ON_SAVE] ?: true
        val ytDlpVersion = preferences[PreferencesKeys.YTDLP_VERSION] ?: ""
        val lastYtDlpUpdateCheck = preferences[PreferencesKeys.LAST_YTDLP_UPDATE_CHECK] ?: 0L

        UserPreferences(
            themeMode = themeMode,
            autoMarkAsRead = autoMarkAsRead,
            includeInBackup = includeInBackup,
            defaultCollectionId = defaultCollectionId,
            inboxViewMode = inboxViewMode,
            fontSizeScale = fontSizeScale,
            hasCompletedOnboarding = hasCompletedOnboarding,
            userName = userName,
            smartRemindersEnabled = smartRemindersEnabled,
            morningEveningEnabled = morningEveningEnabled,
            morningHour = morningHour,
            morningMinute = morningMinute,
            eveningHour = eveningHour,
            eveningMinute = eveningMinute,
            weekendRoundupEnabled = weekendRoundupEnabled,
            weekendRoundupHour = weekendRoundupHour,
            weekendRoundupMinute = weekendRoundupMinute,
            dailyDigestEnabled = dailyDigestEnabled,
            dailyDigestHour = dailyDigestHour,
            dailyDigestMinute = dailyDigestMinute,
            dailyDigestWeekdaysOnly = dailyDigestWeekdaysOnly,
            customScheduleEnabled = customScheduleEnabled,
            customScheduleDays = customScheduleDays,
            customScheduleHour = customScheduleHour,
            customScheduleMinute = customScheduleMinute,
            smartReminderPersistence = smartReminderPersistence,
            geminiApiKey = geminiApiKey,
            deliveredSlotKeys = deliveredSlotKeys,
            accessibilityCaptureEnabled = accessibilityCaptureEnabled,
            clipboardSuggestionEnabled = clipboardSuggestionEnabled,
            recentClipboardHistory = recentClipboardHistory,
            excludedCapturePackages = excludedCapturePackages,
            overlayXRatio = overlayXRatio,
            overlayYRatio = overlayYRatio,
            lastUpdateCheckTime = lastUpdateCheckTime,
            dismissedUpdateVersion = dismissedUpdateVersion,
            dismissedUpdateTimestamp = dismissedUpdateTimestamp,
            mediaDownloadNetworkPolicy = mediaDownloadNetworkPolicy,
            autoInspectMediaOnSave = autoInspectMediaOnSave,
            ytDlpVersion = ytDlpVersion,
            lastYtDlpUpdateCheck = lastYtDlpUpdateCheck,
            customExportDirectoryUri = preferences[PreferencesKeys.CUSTOM_EXPORT_DIRECTORY_URI],
            customExportDirectoryName = preferences[PreferencesKeys.CUSTOM_EXPORT_DIRECTORY_NAME]
        )
    }

    val userPreferences: Flow<UserPreferences>
        get() = userPreferencesFlow

    suspend fun setThemeMode(mode: ThemeMode) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    suspend fun setAutoMarkAsRead(enabled: Boolean) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_MARK_AS_READ] = enabled
        }
    }

    suspend fun setIncludeInBackup(enabled: Boolean) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.INCLUDE_IN_BACKUP] = enabled
        }
    }

    suspend fun setAllowSystemBackup(enabled: Boolean) = setIncludeInBackup(enabled)

    suspend fun setDefaultCollectionId(collectionId: Long?) {
        context.userDataStore.edit { preferences ->
            if (collectionId != null) {
                preferences[PreferencesKeys.DEFAULT_COLLECTION_ID] = collectionId
            } else {
                preferences.remove(PreferencesKeys.DEFAULT_COLLECTION_ID)
            }
        }
    }

    suspend fun setInboxViewMode(mode: InboxViewMode) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.INBOX_VIEW_MODE] = mode.name
        }
    }

    suspend fun setFontSizeScale(scale: FontSizeScale) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.FONT_SIZE_SCALE] = scale.name
        }
    }

    suspend fun setHasCompletedOnboarding(completed: Boolean) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_COMPLETED_ONBOARDING] = completed
        }
    }

    suspend fun setUserName(name: String) {
        context.userDataStore.edit { preferences ->
            val trimmed = name.trim().take(50)
            if (trimmed.isNotBlank()) {
                preferences[PreferencesKeys.USER_NAME] = trimmed
            } else {
                preferences.remove(PreferencesKeys.USER_NAME)
            }
        }
    }

    suspend fun setSmartRemindersEnabled(enabled: Boolean) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.SMART_REMINDERS_ENABLED] = enabled
        }
    }

    suspend fun setMorningEveningEnabled(enabled: Boolean) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.MORNING_EVENING_ENABLED] = enabled
            preferences[PreferencesKeys.SCHEDULES_MIGRATED_V2] = true
        }
    }

    suspend fun setMorningTime(hour: Int, minute: Int) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.MORNING_HOUR] = hour
            preferences[PreferencesKeys.MORNING_MINUTE] = minute
        }
    }

    suspend fun setEveningTime(hour: Int, minute: Int) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.EVENING_HOUR] = hour
            preferences[PreferencesKeys.EVENING_MINUTE] = minute
        }
    }

    suspend fun setWeekendRoundupEnabled(enabled: Boolean) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.WEEKEND_ROUNDUP_ENABLED] = enabled
            preferences[PreferencesKeys.SCHEDULES_MIGRATED_V2] = true
        }
    }

    suspend fun setWeekendRoundupTime(hour: Int, minute: Int) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.WEEKEND_ROUNDUP_HOUR] = hour
            preferences[PreferencesKeys.WEEKEND_ROUNDUP_MINUTE] = minute
        }
    }

    suspend fun setDailyDigestEnabled(enabled: Boolean) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.DAILY_DIGEST_ENABLED] = enabled
            preferences[PreferencesKeys.SCHEDULES_MIGRATED_V2] = true
        }
    }

    suspend fun setDailyDigestTime(hour: Int, minute: Int) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.DAILY_DIGEST_HOUR] = hour
            preferences[PreferencesKeys.DAILY_DIGEST_MINUTE] = minute
        }
    }

    suspend fun setDailyDigestWeekdaysOnly(weekdaysOnly: Boolean) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.DAILY_DIGEST_WEEKDAYS_ONLY] = weekdaysOnly
        }
    }

    suspend fun setCustomScheduleEnabled(enabled: Boolean) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.CUSTOM_SCHEDULE_ENABLED] = enabled
            preferences[PreferencesKeys.SCHEDULES_MIGRATED_V2] = true
        }
    }

    suspend fun setCustomScheduleDays(days: Set<Int>) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.CUSTOM_SCHEDULE_DAYS] = days.map { it.toString() }.toSet()
        }
    }

    suspend fun setCustomScheduleTime(hour: Int, minute: Int) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.CUSTOM_SCHEDULE_HOUR] = hour
            preferences[PreferencesKeys.CUSTOM_SCHEDULE_MINUTE] = minute
        }
    }

    suspend fun setSmartReminderPersistence(persistence: SmartReminderPersistence) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.SMART_REMINDER_PERSISTENCE] = persistence.name
        }
    }

    suspend fun setGeminiApiKey(apiKey: String) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.GEMINI_API_KEY] = apiKey.trim()
        }
    }

    suspend fun recordDeliveredSlot(slotKey: String) {
        context.userDataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.DELIVERED_SLOT_KEYS] ?: emptySet()
            // Keep at most 60 recent slot keys to prevent unbounded growth
            val updated = (current + slotKey).toList().takeLast(60).toSet()
            preferences[PreferencesKeys.DELIVERED_SLOT_KEYS] = updated
        }
    }

    suspend fun setAccessibilityCaptureEnabled(enabled: Boolean) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.ACCESSIBILITY_CAPTURE_ENABLED] = enabled
        }
    }

    suspend fun setClipboardSuggestionEnabled(enabled: Boolean) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.CLIPBOARD_SUGGESTION_ENABLED] = enabled
        }
    }

    suspend fun recordHandledClipboardHash(hash: String, nowMs: Long = System.currentTimeMillis()) {
        context.userDataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.RECENT_CLIPBOARD_HISTORY] ?: emptySet()
            val retentionMs = 7L * 24 * 60 * 60 * 1000L // 7-day retention
            val cutoff = nowMs - retentionMs

            val valid = current.mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) {
                    val entryHash = parts[0]
                    val entryTime = parts[1].toLongOrNull() ?: 0L
                    if (entryTime >= cutoff && entryHash != hash) entryHash to entryTime else null
                } else null
            }.sortedBy { it.second }

            val updatedList = (valid.map { "${it.first}:${it.second}" } + "$hash:$nowMs").takeLast(25)
            preferences[PreferencesKeys.RECENT_CLIPBOARD_HISTORY] = updatedList.toSet()
        }
    }

    suspend fun setExcludedCapturePackages(packages: Set<String>) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.EXCLUDED_CAPTURE_PACKAGES] = packages
        }
    }

    suspend fun toggleExcludedCapturePackage(packageName: String) {
        context.userDataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.EXCLUDED_CAPTURE_PACKAGES] ?: emptySet()
            val updated = if (current.contains(packageName)) current - packageName else current + packageName
            preferences[PreferencesKeys.EXCLUDED_CAPTURE_PACKAGES] = updated
        }
    }

    suspend fun setOverlayPosition(xRatio: Float, yRatio: Float) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.OVERLAY_X_RATIO] = xRatio.coerceIn(0f, 1f)
            preferences[PreferencesKeys.OVERLAY_Y_RATIO] = yRatio.coerceIn(0f, 1f)
        }
    }

    suspend fun recordUpdateCheck(timestamp: Long = System.currentTimeMillis()) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_UPDATE_CHECK_TIME] = timestamp
        }
    }

    suspend fun dismissUpdate(version: String, timestamp: Long = System.currentTimeMillis()) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.DISMISSED_UPDATE_VERSION] = version
            preferences[PreferencesKeys.DISMISSED_UPDATE_TIMESTAMP] = timestamp
        }
    }

    suspend fun setMediaDownloadNetworkPolicy(policy: MediaDownloadNetworkPolicy) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.MEDIA_DOWNLOAD_NETWORK_POLICY] = policy.name
        }
    }

    suspend fun setAutoInspectMediaOnSave(enabled: Boolean) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_INSPECT_MEDIA_ON_SAVE] = enabled
        }
    }

    suspend fun recordYtDlpUpdate(version: String, timestamp: Long = System.currentTimeMillis()) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.YTDLP_VERSION] = version
            preferences[PreferencesKeys.LAST_YTDLP_UPDATE_CHECK] = timestamp
        }
    }

    suspend fun setCustomExportDirectory(uriString: String?, displayName: String?) {
        context.userDataStore.edit { preferences ->
            if (uriString != null) {
                preferences[PreferencesKeys.CUSTOM_EXPORT_DIRECTORY_URI] = uriString
            } else {
                preferences.remove(PreferencesKeys.CUSTOM_EXPORT_DIRECTORY_URI)
            }
            if (displayName != null) {
                preferences[PreferencesKeys.CUSTOM_EXPORT_DIRECTORY_NAME] = displayName
            } else {
                preferences.remove(PreferencesKeys.CUSTOM_EXPORT_DIRECTORY_NAME)
            }
        }
    }

    suspend fun clearCustomExportDirectory() {
        context.userDataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.CUSTOM_EXPORT_DIRECTORY_URI)
            preferences.remove(PreferencesKeys.CUSTOM_EXPORT_DIRECTORY_NAME)
        }
    }
}
