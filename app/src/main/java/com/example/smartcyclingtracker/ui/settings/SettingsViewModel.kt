package com.example.smartcyclingtracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartcyclingtracker.data.local.SettingsRepository
import com.example.smartcyclingtracker.data.local.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val challengesEnabled: Boolean = true,
    val voiceCoachingEnabled: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
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
}
