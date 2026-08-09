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
    val geminiApiKey: String = "",
    val geminiApiKeyInput: String = "",
    val isKeySaved: Boolean = false,
    val keyError: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepo.themeMode,
                settingsRepo.geminiApiKey
            ) { theme, key ->
                _uiState.value = _uiState.value.copy(
                    themeMode = theme,
                    geminiApiKey = key,
                    geminiApiKeyInput = key
                )
            }.collect()
        }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settingsRepo.setThemeMode(mode) }
    }

    fun updateApiKeyInput(key: String) {
        _uiState.value = _uiState.value.copy(
            geminiApiKeyInput = key,
            isKeySaved = false,
            keyError = null
        )
    }

    fun saveApiKey() {
        val key = _uiState.value.geminiApiKeyInput.trim()
        // Accept both AIza... (standard) and AQ... (newer Google AI Studio format)
        val isValidFormat = key.isBlank() ||
            key.startsWith("AIza") ||
            key.startsWith("AQ.")
        if (key.isNotBlank() && !isValidFormat) {
            _uiState.value = _uiState.value.copy(
                keyError = "Unexpected key format. Keys from aistudio.google.com should start with 'AIza' or 'AQ.'"
            )
            return
        }
        viewModelScope.launch {
            settingsRepo.setGeminiApiKey(key)
            _uiState.value = _uiState.value.copy(isKeySaved = true, keyError = null)
        }
    }

}
