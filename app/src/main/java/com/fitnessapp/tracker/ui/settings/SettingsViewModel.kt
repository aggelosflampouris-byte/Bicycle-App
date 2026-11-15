package com.fitnessapp.tracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.tracker.data.local.SettingsRepository
import com.fitnessapp.tracker.data.local.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.net.Uri
import com.fitnessapp.tracker.data.local.AppDatabase
import com.fitnessapp.tracker.data.local.CoachLanguage
import com.fitnessapp.tracker.data.local.CoachPersona
import com.fitnessapp.tracker.data.remote.AuthRepository
import com.fitnessapp.tracker.util.DataBackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val coachPersona: CoachPersona = CoachPersona.SUPPORTIVE,
    val coachLanguage: CoachLanguage = CoachLanguage.AUTO,
    val voiceCoachingEnabled: Boolean = true,
    val twinVoiceNudgesEnabled: Boolean = true,
    val lockPortraitModeEnabled: Boolean = true,
    val isCrashDetectionEnabled: Boolean = true,
    val emergencyContactName: String = "",
    val emergencyContactPhone: String = "",
    val isBackupRunning: Boolean = false,
    val backupMessage: String? = null
)

private data class CoachingPrefs(
    val persona: CoachPersona,
    val language: CoachLanguage,
    val voiceEnabled: Boolean,
    val twinNudgesEnabled: Boolean
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val backupManager: DataBackupManager,
    private val db: AppDatabase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val coachingPrefsFlow = combine(
                settingsRepository.coachPersona,
                settingsRepository.coachLanguage,
                settingsRepository.isVoiceCoachingEnabled,
                settingsRepository.isTwinVoiceNudgesEnabled
            ) { persona, language, voiceEnabled, twinNudgesEnabled ->
                CoachingPrefs(persona, language, voiceEnabled, twinNudgesEnabled)
            }

            val safetyPrefsFlow = combine(
                settingsRepository.isCrashDetectionEnabled,
                settingsRepository.emergencyContactName,
                settingsRepository.emergencyContactPhone
            ) { crashEnabled, contactName, contactPhone ->
                Triple(crashEnabled, contactName, contactPhone)
            }

            combine(
                settingsRepository.themeMode,
                settingsRepository.isLockPortraitModeEnabled,
                coachingPrefsFlow,
                safetyPrefsFlow
            ) { theme, lockPortraitModeEnabled, coaching, (crashEnabled, contactName, contactPhone) ->
                SettingsUiState(
                    themeMode = theme,
                    coachPersona = coaching.persona,
                    coachLanguage = coaching.language,
                    voiceCoachingEnabled = coaching.voiceEnabled,
                    twinVoiceNudgesEnabled = coaching.twinNudgesEnabled,
                    lockPortraitModeEnabled = lockPortraitModeEnabled,
                    isCrashDetectionEnabled = crashEnabled,
                    emergencyContactName = contactName,
                    emergencyContactPhone = contactPhone
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun setCrashDetectionEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setCrashDetectionEnabled(enabled) }
    }

    fun setEmergencyContact(name: String, phone: String) {
        viewModelScope.launch { settingsRepository.setEmergencyContact(name, phone) }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setActivityType(type: String) {
        viewModelScope.launch { settingsRepository.setActivityType(type) }
    }

    fun setVoiceCoachingEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setVoiceCoachingEnabled(enabled) }
    }

    fun setTwinVoiceNudgesEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setTwinVoiceNudgesEnabled(enabled) }
    }

    fun setLockPortraitModeEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setLockPortraitModeEnabled(enabled) }
    }

    fun setCoachPersona(persona: CoachPersona) {
        viewModelScope.launch { settingsRepository.setCoachPersona(persona) }
    }

    fun setCoachLanguage(language: CoachLanguage) {
        viewModelScope.launch { settingsRepository.setCoachLanguage(language) }
    }

    fun exportData(uri: Uri, password: String) {
        _uiState.value = _uiState.value.copy(isBackupRunning = true, backupMessage = null)
        viewModelScope.launch {
            val result = backupManager.exportData(uri, password)
            _uiState.value = _uiState.value.copy(
                isBackupRunning = false,
                backupMessage = if (result.isSuccess) "Data exported successfully!" else "Export failed."
            )
        }
    }

    fun importData(uri: Uri, password: String) {
        _uiState.value = _uiState.value.copy(isBackupRunning = true, backupMessage = null)
        viewModelScope.launch {
            val result = backupManager.importData(uri, password)
            _uiState.value = _uiState.value.copy(
                isBackupRunning = false,
                backupMessage = if (result.isSuccess) "Data imported successfully!" else "Import failed. Incorrect password?"
            )
        }
    }
    
    fun clearBackupMessage() {
        _uiState.value = _uiState.value.copy(backupMessage = null)
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            // Purge user-scoped Room cache before returning to the login screen.
            withContext(Dispatchers.IO) {
                db.clearAllTables()
            }
            onComplete()
        }
    }
}
