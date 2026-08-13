package com.fitnessapp.tracker.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.tracker.data.local.dao.UserDao
import com.fitnessapp.tracker.data.local.entity.UserEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val name: String = "",
    val gender: String = "male",
    val age: String = "35",
    val weightKg: String = "75",
    val heightCm: String = "175",
    val isSaved: Boolean = false,
    val isLoading: Boolean = true,
    val hasProfile: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userDao: UserDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState

    init {
        // Pre-populate form with existing user data so edits don't reset to defaults
        viewModelScope.launch {
            val existing = userDao.getUser()
            if (existing != null) {
                _uiState.value = OnboardingUiState(
                    name = existing.name,
                    gender = existing.gender,
                    age = existing.age.toString(),
                    weightKg = existing.weightKg.toString(),
                    heightCm = existing.heightCm.toString(),
                    isSaved = false,
                    isLoading = false,
                    hasProfile = true
                )
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun updateName(name: String) { _uiState.value = _uiState.value.copy(name = name) }
    fun updateGender(gender: String) { _uiState.value = _uiState.value.copy(gender = gender) }
    fun updateAge(age: String) { _uiState.value = _uiState.value.copy(age = age) }
    fun updateWeight(weight: String) { _uiState.value = _uiState.value.copy(weightKg = weight) }
    fun updateHeight(height: String) { _uiState.value = _uiState.value.copy(heightCm = height) }

    fun saveWithDefaults(onComplete: () -> Unit) {
        val state = _uiState.value
        val existing = userDao // we'll do upsert — id=0 means auto-assign or match existing
        val user = UserEntity(
            name = state.name.ifBlank { "Cyclist" },
            gender = state.gender,
            age = state.age.toIntOrNull() ?: 35,
            weightKg = state.weightKg.toFloatOrNull() ?: 75f,
            heightCm = state.heightCm.toFloatOrNull() ?: 175f
        )
        viewModelScope.launch {
            // upsert atomically handles both first-time insert and subsequent updates
            val currentUser = userDao.getUser()
            if (currentUser != null) {
                userDao.upsertUser(user.copy(id = currentUser.id))
            } else {
                userDao.upsertUser(user)
            }
            _uiState.value = _uiState.value.copy(isSaved = true, hasProfile = true)
            onComplete()
        }
    }

    fun skipWithDefaults(onComplete: () -> Unit) {
        viewModelScope.launch {
            val existing = userDao.getUser()
            if (existing == null) {
                userDao.upsertUser(UserEntity()) // Insert with all defaults
            }
            onComplete()
        }
    }
}
