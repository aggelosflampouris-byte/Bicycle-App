package com.fitnessapp.tracker.data.local

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

enum class AudioLanguage(val title: String) {
    AUTO("System Default 🌐"),
    ENGLISH("English 🇬🇧"),
    GREEK("Ελληνικά 🇬🇷"),
    GERMAN("Deutsch 🇩🇪"),
    FRENCH("Français 🇫🇷"),
    RUSSIAN("Русский 🇷🇺")
}

@Singleton
class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val ACTIVITY_TYPE_KEY = stringPreferencesKey("activity_type")
        // Keep DataStore key names unchanged so saved user preferences persist across upgrades.
        private val AUDIO_CUES_ENABLED_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("voice_coaching_enabled")
        private val LOCK_PORTRAIT_MODE_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("lock_portrait_mode")
        private val AUDIO_LANGUAGE_KEY = stringPreferencesKey("coach_language")
        private val CRASH_DETECTION_ENABLED_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("crash_detection_enabled")
        private val EMERGENCY_CONTACT_NAME_KEY = stringPreferencesKey("emergency_contact_name")
        private val EMERGENCY_CONTACT_PHONE_KEY = stringPreferencesKey("emergency_contact_phone")
        private val TWIN_VOICE_NUDGES_ENABLED_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("twin_voice_nudges_enabled")
        private val CACHED_ENVIRONMENT_JSON_KEY = stringPreferencesKey("cached_environment_json")
    }

    val isTwinVoiceNudgesEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[TWIN_VOICE_NUDGES_ENABLED_KEY] ?: true
    }

    val cachedEnvironmentJson: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[CACHED_ENVIRONMENT_JSON_KEY]
    }

    val isCrashDetectionEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[CRASH_DETECTION_ENABLED_KEY] ?: true
    }

    val emergencyContactName: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[EMERGENCY_CONTACT_NAME_KEY] ?: ""
    }

    val emergencyContactPhone: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[EMERGENCY_CONTACT_PHONE_KEY] ?: ""
    }

    val audioLanguage: Flow<AudioLanguage> = context.settingsDataStore.data.map { prefs ->
        when (prefs[AUDIO_LANGUAGE_KEY]) {
            "ENGLISH" -> AudioLanguage.ENGLISH
            "GREEK" -> AudioLanguage.GREEK
            "GERMAN" -> AudioLanguage.GERMAN
            "FRENCH" -> AudioLanguage.FRENCH
            "RUSSIAN" -> AudioLanguage.RUSSIAN
            else -> AudioLanguage.AUTO
        }
    }

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        when (prefs[THEME_MODE_KEY]) {
            "LIGHT" -> ThemeMode.LIGHT
            "SYSTEM" -> ThemeMode.SYSTEM
            else -> ThemeMode.DARK
        }
    }

    val activityType: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[ACTIVITY_TYPE_KEY] ?: "CYCLING"
    }

    val isAudioCuesEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[AUDIO_CUES_ENABLED_KEY] ?: true
    }

    val isLockPortraitModeEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[LOCK_PORTRAIT_MODE_KEY] ?: true
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = mode.name
        }
    }

    suspend fun setActivityType(type: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[ACTIVITY_TYPE_KEY] = type
        }
    }

    suspend fun setAudioCuesEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[AUDIO_CUES_ENABLED_KEY] = enabled
        }
    }

    suspend fun setLockPortraitModeEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[LOCK_PORTRAIT_MODE_KEY] = enabled
        }
    }

    suspend fun setAudioLanguage(language: AudioLanguage) {
        context.settingsDataStore.edit { prefs ->
            prefs[AUDIO_LANGUAGE_KEY] = language.name
        }
    }

    suspend fun setCrashDetectionEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[CRASH_DETECTION_ENABLED_KEY] = enabled
        }
    }

    suspend fun setEmergencyContact(name: String, phone: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[EMERGENCY_CONTACT_NAME_KEY] = name.trim()
            prefs[EMERGENCY_CONTACT_PHONE_KEY] = phone.trim()
        }
    }

    suspend fun setTwinVoiceNudgesEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[TWIN_VOICE_NUDGES_ENABLED_KEY] = enabled
        }
    }

    suspend fun saveCachedEnvironmentJson(json: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[CACHED_ENVIRONMENT_JSON_KEY] = json
        }
    }
}
