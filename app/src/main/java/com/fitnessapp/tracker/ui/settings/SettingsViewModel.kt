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
import com.fitnessapp.tracker.util.DataBackupManager

import com.fitnessapp.tracker.data.local.CoachLanguage
import com.fitnessapp.tracker.data.local.CoachPersona

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val coachPersona: CoachPersona = CoachPersona.SUPPORTIVE,
    val coachLanguage: CoachLanguage = CoachLanguage.AUTO,
    val challengesEnabled: Boolean = true,
    val voiceCoachingEnabled: Boolean = true,
    val lockPortraitModeEnabled: Boolean = true,
    val isCrashDetectionEnabled: Boolean = true,
    val emergencyContactName: String = "",
    val emergencyContactPhone: String = "",
    val isBackupRunning: Boolean = false,
    val backupMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val backupManager: DataBackupManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val coachingPrefsFlow = combine(
                settingsRepository.coachPersona,
                settingsRepository.coachLanguage,
                settingsRepository.isVoiceCoachingEnabled
            ) { persona, language, voiceEnabled ->
                Triple(persona, language, voiceEnabled)
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
                settingsRepository.challengesEnabled,
                settingsRepository.isLockPortraitModeEnabled,
                coachingPrefsFlow,
                safetyPrefsFlow
            ) { theme, challengesEnabled, lockPortraitModeEnabled, (persona, language, voiceEnabled), (crashEnabled, contactName, contactPhone) ->
                SettingsUiState(
                    themeMode = theme,
                    coachPersona = persona,
                    coachLanguage = language,
                    challengesEnabled = challengesEnabled,
                    voiceCoachingEnabled = voiceEnabled,
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

    fun setChallengesEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setChallengesEnabled(enabled) }
    }

    fun setVoiceCoachingEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setVoiceCoachingEnabled(enabled) }
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
}
