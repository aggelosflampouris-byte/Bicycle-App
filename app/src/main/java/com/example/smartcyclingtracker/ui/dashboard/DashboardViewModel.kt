package com.example.smartcyclingtracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartcyclingtracker.data.local.dao.UserDao
import com.example.smartcyclingtracker.data.local.dao.WorkoutSessionDao
import com.example.smartcyclingtracker.data.local.entity.UserEntity
import com.example.smartcyclingtracker.data.local.entity.WorkoutSessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val user: UserEntity = UserEntity(),
    val sessions: List<WorkoutSessionEntity> = emptyList(),
    val totalDistanceKm: Double = 0.0,
    val avgDistanceKm: Double = 0.0,
    val totalSessions: Int = 0,
    val totalCalories: Double = 0.0,
    val isLoading: Boolean = true
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val userDao: UserDao,
    private val sessionDao: WorkoutSessionDao,
    private val settingsRepository: com.example.smartcyclingtracker.data.local.SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _activityType = MutableStateFlow("CYCLING")

    init {
        loadData()
    }

    fun setActivityType(type: String) {
        _activityType.value = type
        viewModelScope.launch {
            settingsRepository.setActivityType(type)
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                userDao.getUserFlow().map { it ?: UserEntity() },
                sessionDao.getAllSessionsFlow(),
                _activityType
            ) { user, allSessions, activityType ->
                val sessions = allSessions.filter { it.activityType == activityType }
                val totalDist = sessions.sumOf { it.totalDistanceMeters } / 1000.0
                val avgDist = if (sessions.isNotEmpty()) totalDist / sessions.size else 0.0
                val totalCals = sessions.sumOf { it.caloriesBurned }

                DashboardUiState(
                    user = user,
                    sessions = sessions,
                    totalDistanceKm = totalDist,
                    avgDistanceKm = avgDist,
                    totalSessions = sessions.size,
                    totalCalories = totalCals,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            sessionDao.deleteSessionById(id)
        }
    }

    fun saveUser(user: UserEntity) {
        viewModelScope.launch {
            val existing = userDao.getUser()
            if (existing == null) {
                userDao.insertUser(user)
            } else {
                userDao.updateUser(user.copy(id = existing.id))
            }
        }
    }
}
