package com.laterbox.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class InboxViewMode {
    HERO,
    COMPACT
}

enum class FontSizeScale(val scaleFactor: Float, val label: String) {
    SMALL(0.85f, "Small"),
    SYSTEM(1.0f, "System"),
    LARGE(1.15f, "Large")
}

enum class AiEngine(val label: String, val description: String) {
    CLOUD(
        label = "Cloud (Gemini 2.5 Flash-Lite)",
        description = "Fast, context-aware reminders powered by Firebase AI Logic"
    ),
    ON_DEVICE(
        label = "On-device (Gemini Nano)",
        description = "Private on-device inference with zero cloud transmission"
    )
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
    val aiEngine: AiEngine = AiEngine.CLOUD
) {
    val allowSystemBackup: Boolean get() = includeInBackup
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
        val AI_ENGINE = stringPreferencesKey("ai_engine")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.userDataStore.data.map { preferences ->
        val themeModeStr = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val themeMode = try {
            ThemeMode.valueOf(themeModeStr)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }

        val autoMarkAsRead = preferences[PreferencesKeys.AUTO_MARK_AS_READ] ?: true
        val includeInBackup = preferences[PreferencesKeys.INCLUDE_IN_BACKUP] ?: true
        val defaultCollectionId = preferences[PreferencesKeys.DEFAULT_COLLECTION_ID]

        val viewModeStr = preferences[PreferencesKeys.INBOX_VIEW_MODE] ?: InboxViewMode.HERO.name
        val inboxViewMode = try {
            InboxViewMode.valueOf(viewModeStr)
        } catch (e: Exception) {
            InboxViewMode.HERO
        }

        val fontScaleStr = preferences[PreferencesKeys.FONT_SIZE_SCALE] ?: FontSizeScale.SYSTEM.name
        val fontSizeScale = try {
            FontSizeScale.valueOf(fontScaleStr)
        } catch (e: Exception) {
            FontSizeScale.SYSTEM
        }

        val hasCompletedOnboarding = preferences[PreferencesKeys.HAS_COMPLETED_ONBOARDING] ?: false
        val userName = preferences[PreferencesKeys.USER_NAME] ?: ""

        val smartRemindersEnabled = preferences[PreferencesKeys.SMART_REMINDERS_ENABLED] ?: true
        val aiEngineStr = preferences[PreferencesKeys.AI_ENGINE] ?: AiEngine.CLOUD.name
        val aiEngine = try {
            AiEngine.valueOf(aiEngineStr)
        } catch (e: Exception) {
            AiEngine.CLOUD
        }

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
            aiEngine = aiEngine
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

    suspend fun setAiEngine(engine: AiEngine) {
        context.userDataStore.edit { preferences ->
            preferences[PreferencesKeys.AI_ENGINE] = engine.name
        }
    }
}
