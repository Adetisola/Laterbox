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

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoMarkAsRead: Boolean = true,
    val includeInBackup: Boolean = true,
    val defaultCollectionId: Long? = null
)

val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUTO_MARK_AS_READ = booleanPreferencesKey("auto_mark_as_read")
        val INCLUDE_IN_BACKUP = booleanPreferencesKey("include_in_backup")
        val DEFAULT_COLLECTION_ID = longPreferencesKey("default_collection_id")
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

        UserPreferences(
            themeMode = themeMode,
            autoMarkAsRead = autoMarkAsRead,
            includeInBackup = includeInBackup,
            defaultCollectionId = defaultCollectionId
        )
    }

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

    suspend fun setDefaultCollectionId(collectionId: Long?) {
        context.userDataStore.edit { preferences ->
            if (collectionId != null) {
                preferences[PreferencesKeys.DEFAULT_COLLECTION_ID] = collectionId
            } else {
                preferences.remove(PreferencesKeys.DEFAULT_COLLECTION_ID)
            }
        }
    }
}
