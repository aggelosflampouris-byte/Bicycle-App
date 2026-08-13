package com.example.smartcyclingtracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartcyclingtracker.data.local.SettingsRepository
import com.example.smartcyclingtracker.data.local.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.net.Uri
import com.example.smartcyclingtracker.util.DataBackupManager

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val challengesEnabled: Boolean = true,
    val voiceCoachingEnabled: Boolean = true,
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
            combine(
                settingsRepository.themeMode,
                settingsRepository.challengesEnabled,
                settingsRepository.isVoiceCoachingEnabled
            ) { theme, challengesEnabled, voiceCoachingEnabled ->
                SettingsUiState(
                    themeMode = theme,
                    challengesEnabled = challengesEnabled,
                    voiceCoachingEnabled = voiceCoachingEnabled
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
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
