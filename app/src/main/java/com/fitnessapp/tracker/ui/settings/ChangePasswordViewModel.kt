package com.fitnessapp.tracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.tracker.data.remote.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ChangePasswordStep { VERIFY, UPDATE }

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState = _uiState.asStateFlow()

    fun onCurrentPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(currentPassword = value, error = null)
    }

    fun onNewPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(newPassword = value, error = null)
    }

    fun onConfirmNewPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(confirmNewPassword = value, error = null)
    }

    /** Step 1: Re-authenticate the user with their current password. */
    fun verify() {
        val current = _uiState.value.currentPassword
        if (current.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please enter your current password")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            authRepository.reauthenticate(current)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        step = ChangePasswordStep.UPDATE,
                        error = null
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Incorrect password. Please try again."
                    )
                }
        }
    }

    /** Step 2: Validate and apply the new password. */
    fun updatePassword(onSuccess: () -> Unit) {
        val state = _uiState.value
        when {
            state.newPassword.isBlank() -> {
                _uiState.value = state.copy(error = "Please enter a new password")
                return
            }
            state.newPassword.length < 8 -> {
                _uiState.value = state.copy(error = "Password must be at least 8 characters")
                return
            }
            state.newPassword != state.confirmNewPassword -> {
                _uiState.value = state.copy(error = "Passwords do not match")
                return
            }
        }
        _uiState.value = state.copy(isLoading = true, error = null)
        viewModelScope.launch {
            authRepository.updatePassword(state.newPassword)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    onSuccess()
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = it.localizedMessage ?: "Failed to update password"
                    )
                }
        }
    }
}

data class ChangePasswordUiState(
    val step: ChangePasswordStep = ChangePasswordStep.VERIFY,
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmNewPassword: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)
