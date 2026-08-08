package com.example.smartcyclingtracker.updater

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdateUiState(
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val updateInfo: UpdateInfo? = null,
    val showDialog: Boolean = false,
    val userMessage: String? = null,
    val isUpToDate: Boolean = false
)

@HiltViewModel
class UpdaterViewModel @Inject constructor(
    private val appUpdater: AppUpdater
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    init {
        // Silent check on launch
        checkForUpdates(silent = true)
    }

    fun checkForUpdates(silent: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isChecking = !silent,
                userMessage = if (!silent) "Checking for updates..." else null,
                isUpToDate = false
            )

            val result = appUpdater.checkForUpdates()
            result.onSuccess { info ->
                if (info.isUpdateAvailable) {
                    _uiState.value = _uiState.value.copy(
                        isChecking = false,
                        updateInfo = info,
                        showDialog = true,
                        userMessage = null,
                        isUpToDate = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isChecking = false,
                        updateInfo = info,
                        showDialog = false,
                        userMessage = if (!silent) "You have the latest version (${info.currentVersion})!" else null,
                        isUpToDate = true
                    )
                }
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isChecking = false,
                    showDialog = false,
                    userMessage = if (!silent) "Failed to check for updates: ${error.localizedMessage}" else null
                )
            }
        }
    }

    fun startDownloadAndInstall(context: Context) {
        val url = _uiState.value.updateInfo?.downloadUrl ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDownloading = true,
                downloadProgress = 0f,
                userMessage = "Downloading update..."
            )

            val result = appUpdater.downloadAndInstallApk(
                context = context,
                downloadUrl = url,
                onProgress = { progress ->
                    _uiState.value = _uiState.value.copy(downloadProgress = progress)
                }
            )

            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    showDialog = false,
                    userMessage = "Update downloaded. Launching installer..."
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    userMessage = "Download failed: ${error.localizedMessage}"
                )
            }
        }
    }

    fun dismissDialog() {
        _uiState.value = _uiState.value.copy(showDialog = false)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
    }
}
