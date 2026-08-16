package com.fitnessapp.tracker.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.tracker.data.remote.AuthRepository
import com.fitnessapp.tracker.data.remote.FirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val firestoreRepository: FirestoreRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    fun toggleAuthMode() {
        _uiState.value = _uiState.value.copy(
            isLogin = !_uiState.value.isLogin,
            error = null,
            confirmPassword = "",
            passwordResetSent = false
        )
    }

    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(email = email, error = null)
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = null)
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = value, error = null)
    }

    fun onUsernameChange(username: String) {
        _uiState.value = _uiState.value.copy(username = username, error = null)
    }

    /**
     * Authenticates the user.
     * @param onLoginSuccess called when an existing user signs in successfully.
     * @param onSignUpSuccess called when a new user registers successfully (→ Onboarding).
     */
    fun authenticate(
        onLoginSuccess: () -> Unit,
        onSignUpSuccess: () -> Unit
    ) {
        val state = _uiState.value
        val email = state.email.trim()
        val password = state.password

        if (email.isBlank() || password.isBlank()) {
            _uiState.value = state.copy(error = "Email and password cannot be empty")
            return
        }

        if (!state.isLogin) {
            if (state.confirmPassword.isBlank()) {
                _uiState.value = state.copy(error = "Please confirm your password")
                return
            }
            if (password != state.confirmPassword) {
                _uiState.value = state.copy(error = "Passwords do not match")
                return
            }
            if (password.length < 8) {
                _uiState.value = state.copy(error = "Password must be at least 8 characters")
                return
            }
        }

        _uiState.value = state.copy(isLoading = true, error = null)

        viewModelScope.launch {
            val result = if (state.isLogin) {
                authRepository.signIn(email, password)
            } else {
                authRepository.signUp(email, password)
            }

            result.onSuccess {
                if (state.isLogin) {
                    firestoreRepository.pullAndRestoreUserData()
                }
                _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                if (state.isLogin) onLoginSuccess() else onSignUpSuccess()
            }.onFailure {
                val isEmailInUse = it is FirebaseAuthUserCollisionException || 
                    it.localizedMessage?.contains("email address is already in use", ignoreCase = true) == true
                
                if (!state.isLogin && isEmailInUse) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLogin = true,
                        password = "",
                        confirmPassword = "",
                        error = "Account already exists. Please sign in."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = it.localizedMessage ?: "Authentication failed"
                    )
                }
            }
        }
    }

    fun sendPasswordReset(email: String, onDone: (success: Boolean, message: String) -> Unit) {
        val trimmed = email.trim()
        if (trimmed.isBlank()) {
            onDone(false, "Please enter your email address")
            return
        }
        viewModelScope.launch {
            authRepository.sendPasswordResetEmail(trimmed)
                .onSuccess { onDone(true, "Reset link sent! (Check your spam folder)") }
                .onFailure { onDone(false, it.localizedMessage ?: "Failed to send reset email") }
        }
    }
}

data class AuthUiState(
    val isLogin: Boolean = true,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val username: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val passwordResetSent: Boolean = false
)
