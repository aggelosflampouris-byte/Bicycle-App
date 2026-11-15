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
import com.fitnessapp.tracker.data.local.AudioLanguage
import com.fitnessapp.tracker.data.remote.AuthRepository
import com.fitnessapp.tracker.util.DataBackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val audioLanguage: AudioLanguage = AudioLanguage.AUTO,
    val audioCuesEnabled: Boolean = true,
    val twinVoiceNudgesEnabled: Boolean = true,
    val lockPortraitModeEnabled: Boolean = true,
    val isCrashDetectionEnabled: Boolean = true,
    val emergencyContactName: String = "",
    val emergencyContactPhone: String = "",
    val isBackupRunning: Boolean = false,
    val backupMessage: String? = null
)

private data class AudioPrefs(
    val language: AudioLanguage,
    val audioEnabled: Boolean,
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
            val audioPrefsFlow = combine(
                settingsRepository.audioLanguage,
                settingsRepository.isAudioCuesEnabled,
                settingsRepository.isTwinVoiceNudgesEnabled
            ) { language, audioEnabled, twinNudgesEnabled ->
                AudioPrefs(language, audioEnabled, twinNudgesEnabled)
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
                audioPrefsFlow,
                safetyPrefsFlow
            ) { theme, lockPortraitModeEnabled, audio, (crashEnabled, contactName, contactPhone) ->
                SettingsUiState(
                    themeMode = theme,
                    audioLanguage = audio.language,
                    audioCuesEnabled = audio.audioEnabled,
                    twinVoiceNudgesEnabled = audio.twinNudgesEnabled,
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

    fun setAudioCuesEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAudioCuesEnabled(enabled) }
    }

    fun setTwinVoiceNudgesEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setTwinVoiceNudgesEnabled(enabled) }
    }

    fun setLockPortraitModeEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setLockPortraitModeEnabled(enabled) }
    }

    fun setAudioLanguage(language: AudioLanguage) {
        viewModelScope.launch { settingsRepository.setAudioLanguage(language) }
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
