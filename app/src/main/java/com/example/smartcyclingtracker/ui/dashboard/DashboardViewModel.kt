package com.example.smartcyclingtracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartcyclingtracker.data.local.dao.UserDao
import com.example.smartcyclingtracker.data.local.dao.WorkoutSessionDao
import com.example.smartcyclingtracker.data.local.entity.UserEntity
import com.example.smartcyclingtracker.data.local.entity.WorkoutSessionEntity
import com.example.smartcyclingtracker.data.local.entity.ChallengeEntity
import com.example.smartcyclingtracker.data.local.dao.ChallengeDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.example.smartcyclingtracker.data.local.RoutineProgress
import com.example.smartcyclingtracker.data.local.RoutineRepository

data class DashboardUiState(
    val user: UserEntity = UserEntity(),
    val sessions: List<WorkoutSessionEntity> = emptyList(),
    val totalDistanceKm: Double = 0.0,
    val avgDistanceKm: Double = 0.0,
    val totalSessions: Int = 0,
    val totalCalories: Double = 0.0,
    val routineProgress: RoutineProgress? = null,
    val latestChallenge: ChallengeEntity? = null,
    val showNewChallengeDialog: Boolean = false,
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val userDao: UserDao,
    private val sessionDao: WorkoutSessionDao,
    private val challengeDao: ChallengeDao,
    private val settingsRepository: com.example.smartcyclingtracker.data.local.SettingsRepository,
    private val routineRepository: RoutineRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _activityType = MutableStateFlow("CYCLING")

    init {
        loadData()
        viewModelScope.launch {
            routineRepository.checkAndAdvanceRoutine(_activityType.value)
        }
    }

    fun setActivityType(type: String) {
        _activityType.value = type
        viewModelScope.launch {
            settingsRepository.setActivityType(type)
        }
    }
    
    fun saveRoutine(interval: String, metric: String, targetValue: Double, autoImprove: Boolean) {
        viewModelScope.launch {
            routineRepository.saveRoutine(_activityType.value, interval, metric, targetValue, autoImprove)
        }
    }
    
    fun deleteRoutine() {
        viewModelScope.launch {
            routineRepository.deleteRoutine(_activityType.value)
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            val routineProgressFlow = _activityType.flatMapLatest { type ->
                routineRepository.getRoutineProgressFlow(type)
            }
            // Nest two type-safe combines to avoid unchecked Array<Any?> casts
            val sessionsAndType = combine(
                sessionDao.getAllSessionsFlow(),
                _activityType
            ) { allSessions, activityType ->
                Pair(allSessions, activityType)
            }
            combine(
                userDao.getUserFlow().map { it ?: UserEntity() },
                sessionsAndType,
                routineProgressFlow,
                challengeDao.getLatestChallengeFlow()
            ) { user, (allSessions, activityType), routineProgress, latestChallenge ->
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
                    routineProgress = routineProgress,
                    latestChallenge = latestChallenge,
                    // Show in-app dialog when a PENDING challenge was generated within the last 2 minutes
                    showNewChallengeDialog = latestChallenge != null &&
                        latestChallenge.status == "PENDING" &&
                        (System.currentTimeMillis() - latestChallenge.createdAt) < 2 * 60 * 1000L,
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

    fun respondToChallenge(challenge: ChallengeEntity, accepted: Boolean) {
        viewModelScope.launch {
            val status = if (accepted) com.example.smartcyclingtracker.data.local.entity.ChallengeStatus.ACCEPTED.name else com.example.smartcyclingtracker.data.local.entity.ChallengeStatus.DENIED.name
            challengeDao.updateChallenge(challenge.copy(status = status))
            if (accepted) {
                setActivityType(challenge.activityType)
            }
        }
    }

    fun dismissNewChallengeDialog() {
        _uiState.value = _uiState.value.copy(showNewChallengeDialog = false)
    }
}
