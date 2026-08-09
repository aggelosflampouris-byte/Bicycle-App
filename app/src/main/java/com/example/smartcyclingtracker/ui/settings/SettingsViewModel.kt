package com.example.smartcyclingtracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartcyclingtracker.BuildConfig
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
        if (key.isNotBlank() && !key.startsWith("AIza") && !key.startsWith("AQ.")) {
            _uiState.value = _uiState.value.copy(
                keyError = "Key should start with 'AIza' — get yours at aistudio.google.com"
            )
            return
        }
        viewModelScope.launch {
            settingsRepo.setGeminiApiKey(key)
            _uiState.value = _uiState.value.copy(isKeySaved = true, keyError = null)
        }
    }

    /** Returns the effective API key: DataStore key > BuildConfig key */
    fun getEffectiveApiKey(): String {
        val stored = _uiState.value.geminiApiKey
        if (stored.isNotBlank()) return stored
        val buildKey = BuildConfig.GEMINI_API_KEY
        return if (buildKey != "YOUR_GEMINI_API_KEY_HERE") buildKey else ""
    }
}
