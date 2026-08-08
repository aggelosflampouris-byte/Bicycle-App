package com.example.smartcyclingtracker.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartcyclingtracker.data.local.dao.UserDao
import com.example.smartcyclingtracker.data.local.entity.UserEntity
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
    val isSaved: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userDao: UserDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState

    fun updateName(name: String) { _uiState.value = _uiState.value.copy(name = name) }
    fun updateGender(gender: String) { _uiState.value = _uiState.value.copy(gender = gender) }
    fun updateAge(age: String) { _uiState.value = _uiState.value.copy(age = age) }
    fun updateWeight(weight: String) { _uiState.value = _uiState.value.copy(weightKg = weight) }
    fun updateHeight(height: String) { _uiState.value = _uiState.value.copy(heightCm = height) }

    fun saveWithDefaults(onComplete: () -> Unit) {
        val state = _uiState.value
        val user = UserEntity(
            name = state.name.ifBlank { "Cyclist" },
            gender = state.gender,
            age = state.age.toIntOrNull() ?: 35,
            weightKg = state.weightKg.toFloatOrNull() ?: 75f,
            heightCm = state.heightCm.toFloatOrNull() ?: 175f
        )
        viewModelScope.launch {
            val existing = userDao.getUser()
            if (existing == null) {
                userDao.insertUser(user)
            } else {
                userDao.updateUser(user.copy(id = existing.id))
            }
            _uiState.value = _uiState.value.copy(isSaved = true)
            onComplete()
        }
    }

    fun skipWithDefaults(onComplete: () -> Unit) {
        viewModelScope.launch {
            val existing = userDao.getUser()
            if (existing == null) {
                userDao.insertUser(UserEntity()) // Insert defaults
            }
            onComplete()
        }
    }
}
