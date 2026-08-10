package com.example.smartcyclingtracker.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { DARK, LIGHT, SYSTEM }

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        private val ACTIVITY_TYPE_KEY = stringPreferencesKey("activity_type")
    }

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        when (prefs[THEME_MODE_KEY]) {
            "LIGHT" -> ThemeMode.LIGHT
            "SYSTEM" -> ThemeMode.SYSTEM
            else -> ThemeMode.DARK
        }
    }

    val geminiApiKey: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[GEMINI_API_KEY] ?: ""
    }

    val activityType: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[ACTIVITY_TYPE_KEY] ?: "CYCLING"
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = mode.name
        }
    }

    suspend fun setGeminiApiKey(key: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[GEMINI_API_KEY] = key.trim()
        }
    }

    suspend fun setActivityType(type: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[ACTIVITY_TYPE_KEY] = type
        }
    }
}
