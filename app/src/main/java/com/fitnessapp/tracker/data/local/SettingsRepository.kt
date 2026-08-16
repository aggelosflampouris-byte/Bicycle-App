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

enum class CoachPersona(val title: String, val subtitle: String) {
    SUPPORTIVE("Supportive Mentor", "Empathetic, positive, and wellness-focused encouragement"),
    DRILL_SERGEANT("Pro Drill Sergeant", "Direct, intense, and challenge-driven pushing your limits"),
    DATA_SCIENTIST("Sports Scientist", "Analytical, metrics-driven, focusing on efficiency & power")
}

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val ACTIVITY_TYPE_KEY = stringPreferencesKey("activity_type")
        private val CHALLENGES_ENABLED_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("challenges_enabled")
        private val VOICE_COACHING_ENABLED_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("voice_coaching_enabled")
        private val LOCK_PORTRAIT_MODE_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("lock_portrait_mode")
        private val COACH_PERSONA_KEY = stringPreferencesKey("coach_persona")
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

    val challengesEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[CHALLENGES_ENABLED_KEY] ?: true
    }

    val isVoiceCoachingEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[VOICE_COACHING_ENABLED_KEY] ?: true
    }

    val isLockPortraitModeEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[LOCK_PORTRAIT_MODE_KEY] ?: true
    }

    val coachPersona: Flow<CoachPersona> = context.settingsDataStore.data.map { prefs ->
        when (prefs[COACH_PERSONA_KEY]) {
            "DRILL_SERGEANT" -> CoachPersona.DRILL_SERGEANT
            "DATA_SCIENTIST" -> CoachPersona.DATA_SCIENTIST
            else -> CoachPersona.SUPPORTIVE
        }
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

    suspend fun setChallengesEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[CHALLENGES_ENABLED_KEY] = enabled
        }
    }

    suspend fun setVoiceCoachingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[VOICE_COACHING_ENABLED_KEY] = enabled
        }
    }

    suspend fun setLockPortraitModeEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[LOCK_PORTRAIT_MODE_KEY] = enabled
        }
    }

    suspend fun setCoachPersona(persona: CoachPersona) {
        context.settingsDataStore.edit { prefs ->
            prefs[COACH_PERSONA_KEY] = persona.name
        }
    }
}
