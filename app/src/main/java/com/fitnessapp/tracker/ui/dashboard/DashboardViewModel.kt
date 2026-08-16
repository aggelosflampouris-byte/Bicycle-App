package com.fitnessapp.tracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.tracker.data.local.dao.UserDao
import com.fitnessapp.tracker.data.local.dao.WorkoutSessionDao
import com.fitnessapp.tracker.data.local.entity.UserEntity
import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import com.fitnessapp.tracker.data.local.entity.ChallengeEntity
import com.fitnessapp.tracker.data.local.entity.ChallengeStatus
import com.fitnessapp.tracker.data.local.entity.TrainingPlanEntity
import com.fitnessapp.tracker.data.local.dao.ChallengeDao
import com.fitnessapp.tracker.data.local.dao.TrainingPlanDao
import com.fitnessapp.tracker.data.remote.GeminiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.fitnessapp.tracker.data.local.RoutineProgress
import com.fitnessapp.tracker.data.local.RoutineRepository

data class DashboardUiState(
    val user: UserEntity = UserEntity(),
    val sessions: List<WorkoutSessionEntity> = emptyList(),
    val totalDistanceKm: Double = 0.0,
    val avgDistanceKm: Double = 0.0,
    val totalSessions: Int = 0,
    val totalCalories: Double = 0.0,
    val routineProgress: RoutineProgress? = null,
    val latestChallenge: ChallengeEntity? = null,
    val completedChallenges: List<ChallengeEntity> = emptyList(),
    val personalRecords: List<com.fitnessapp.tracker.data.local.entity.PersonalRecordEntity> = emptyList(),
    val trainingPlan: TrainingPlanEntity? = null,
    val showNewChallengeDialog: Boolean = false,
    val isLoading: Boolean = true,
    val isGeneratingPlan: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val userDao: UserDao,
    private val sessionDao: WorkoutSessionDao,
    private val challengeDao: ChallengeDao,
    private val personalRecordDao: com.fitnessapp.tracker.data.local.dao.PersonalRecordDao,
    private val trainingPlanDao: TrainingPlanDao,
    private val geminiRepository: GeminiRepository,
    private val settingsRepository: com.fitnessapp.tracker.data.local.SettingsRepository,
    private val routineRepository: RoutineRepository,
    private val challengeGenerator: com.fitnessapp.tracker.engine.ChallengeGenerator
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _activityType = MutableStateFlow("CYCLING")
    private val _dismissedChallengeId = MutableStateFlow<Long?>(null)

    init {
        loadData()
        viewModelScope.launch {
            routineRepository.checkAndAdvanceRoutine(_activityType.value)
            checkAndGenerateChallenge()
        }
    }

    suspend fun checkAndGenerateChallenge() {
        val enabled = settingsRepository.challengesEnabled.first()
        if (!enabled) return
        val active = challengeDao.getActiveChallenge()
        if (active != null) return
        val latest = challengeDao.getLatestChallenge()
        val oneDayMs = 24 * 60 * 60 * 1000L
        if (latest == null ||
            (latest.status == ChallengeStatus.PENDING && System.currentTimeMillis() - latest.createdAt >= oneDayMs) ||
            ((latest.status == ChallengeStatus.COMPLETED || latest.status == ChallengeStatus.CANCELLED || latest.status == ChallengeStatus.DENIED) &&
             System.currentTimeMillis() - (latest.completedAt ?: latest.createdAt) >= oneDayMs)) {
            val newChallenge = challengeGenerator.generateNewChallenge()
            challengeDao.insertChallenge(newChallenge)
        }
    }

    fun generateNewChallenge() {
        viewModelScope.launch {
            val enabled = settingsRepository.challengesEnabled.first()
            if (!enabled) {
                settingsRepository.setChallengesEnabled(true)
            }
            val newChallenge = challengeGenerator.generateNewChallenge()
            challengeDao.insertChallenge(newChallenge)
        }
    }

    fun setActivityType(type: String, force: Boolean = false) {
        viewModelScope.launch {
            val activeChallenge = challengeDao.getActiveChallenge()
            if (!force && activeChallenge != null &&
                (activeChallenge.status == ChallengeStatus.ACCEPTED ||
                 activeChallenge.status == ChallengeStatus.ACTIVE) &&
                activeChallenge.activityType != type) {
                // Block activity change if challenge for another activity is active
                return@launch
            }
            _activityType.value = type
            settingsRepository.setActivityType(type)
        }
    }

    fun cancelChallenge(challenge: ChallengeEntity) {
        viewModelScope.launch {
            challengeDao.updateChallenge(
                challenge.copy(status = ChallengeStatus.CANCELLED)
            )
        }
    }
    
    fun saveRoutine(interval: com.fitnessapp.tracker.data.local.entity.RoutineInterval, metric: com.fitnessapp.tracker.data.local.entity.RoutineMetric, targetValue: Double, autoImprove: Boolean) {
        viewModelScope.launch {
            routineRepository.saveRoutine(
                activityType = _activityType.value,
                interval = interval,
                metric = metric,
                targetValue = targetValue,
                autoImprove = autoImprove
            )
        }
    }
    
    fun deleteRoutine() {
        viewModelScope.launch {
            routineRepository.deleteRoutine(_activityType.value)
        }
    }

    private data class SessionStats(
        val sessions: List<WorkoutSessionEntity>,
        val totalDistanceKm: Double,
        val avgDistanceKm: Double,
        val totalCalories: Double
    )

    private data class ChallengeState(
        val latestChallenge: ChallengeEntity?,
        val completedChallenges: List<ChallengeEntity>,
        val showNewChallengeDialog: Boolean
    )

    private fun loadData() {
        viewModelScope.launch {
            val routineProgressFlow = _activityType.flatMapLatest { type ->
                routineRepository.getRoutineProgressFlow(type)
            }
            val sessionStatsFlow = combine(
                sessionDao.getAllSessionsFlow(),
                _activityType
            ) { allSessions, activityType ->
                val sessions = allSessions.filter { it.activityType == activityType }
                val totalDist = sessions.sumOf { it.totalDistanceMeters } / 1000.0
                val avgDist = if (sessions.isNotEmpty()) totalDist / sessions.size else 0.0
                val totalCals = sessions.sumOf { it.caloriesBurned }
                SessionStats(
                    sessions = sessions,
                    totalDistanceKm = totalDist,
                    avgDistanceKm = avgDist,
                    totalCalories = totalCals
                )
            }
            val challengeStateFlow = combine(
                challengeDao.getLatestChallengeFlow(),
                challengeDao.getCompletedChallengesFlow(),
                _dismissedChallengeId
            ) { latestChallenge, completedChallenges, dismissedId ->
                val showDialog = latestChallenge != null &&
                    latestChallenge.status == ChallengeStatus.PENDING &&
                    latestChallenge.id != dismissedId
                ChallengeState(
                    latestChallenge = latestChallenge,
                    completedChallenges = completedChallenges,
                    showNewChallengeDialog = showDialog
                )
            }
            val coachingStateFlow = combine(
                challengeStateFlow,
                trainingPlanDao.getPlanFlow()
            ) { challengeState, plan ->
                Pair(challengeState, plan)
            }
            val personalRecordsFlow = _activityType.flatMapLatest { type ->
                personalRecordDao.getRecordsForActivityFlow(type)
            }
            combine(
                userDao.getUserFlow().map { it ?: UserEntity() },
                sessionStatsFlow,
                routineProgressFlow,
                coachingStateFlow,
                personalRecordsFlow
            ) { user, sessionStats, routineProgress, coachingState, records ->
                val (challengeState, plan) = coachingState
                DashboardUiState(
                    user = user,
                    sessions = sessionStats.sessions,
                    totalDistanceKm = sessionStats.totalDistanceKm,
                    avgDistanceKm = sessionStats.avgDistanceKm,
                    totalSessions = sessionStats.sessions.size,
                    totalCalories = sessionStats.totalCalories,
                    routineProgress = routineProgress,
                    latestChallenge = challengeState.latestChallenge,
                    completedChallenges = challengeState.completedChallenges,
                    personalRecords = records,
                    trainingPlan = plan,
                    showNewChallengeDialog = challengeState.showNewChallengeDialog,
                    isLoading = false,
                    isGeneratingPlan = _uiState.value.isGeneratingPlan
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
        _dismissedChallengeId.value = challenge.id
        viewModelScope.launch {
            val status = if (accepted) ChallengeStatus.ACCEPTED else ChallengeStatus.DENIED
            challengeDao.updateChallenge(challenge.copy(status = status))
            if (accepted) {
                setActivityType(challenge.activityType)
            }
        }
    }

    fun dismissNewChallengeDialog() {
        _uiState.value.latestChallenge?.let {
            _dismissedChallengeId.value = it.id
        }
        _uiState.value = _uiState.value.copy(showNewChallengeDialog = false)
    }

    fun generateTrainingPlan(goal: String = "Balanced Endurance") {
        if (_uiState.value.isGeneratingPlan) return
        _uiState.value = _uiState.value.copy(isGeneratingPlan = true)
        
        viewModelScope.launch {
            val user = userDao.getUser() ?: UserEntity()
            val persona = settingsRepository.coachPersona.first()
            // Provide the last 7 days of sessions for context
            val oneWeekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            val recentSessions = sessionDao.getAllSessionsFlow().first().filter { it.startTime >= oneWeekAgo }
            
            val dailyPlans = geminiRepository.generateWeeklyPlan(user, recentSessions, goal, persona)
            if (dailyPlans != null) {
                val json = com.google.gson.Gson().toJson(dailyPlans)
                val entity = TrainingPlanEntity(
                    id = 1,
                    generatedAtMs = System.currentTimeMillis(),
                    planJson = json
                )
                trainingPlanDao.insertPlan(entity)
            }
            _uiState.value = _uiState.value.copy(isGeneratingPlan = false)
        }
    }

    fun toggleDailyPlanCompleted(day: String) {
        viewModelScope.launch {
            val currentPlan = _uiState.value.trainingPlan ?: return@launch
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<com.fitnessapp.tracker.data.local.entity.DailyPlan>>() {}.type
                val plans: MutableList<com.fitnessapp.tracker.data.local.entity.DailyPlan> = com.google.gson.Gson().fromJson(currentPlan.planJson, type)
                
                val index = plans.indexOfFirst { it.day == day }
                if (index != -1) {
                    val plan = plans[index]
                    plans[index] = plan.copy(isCompleted = !plan.isCompleted)
                    val newJson = com.google.gson.Gson().toJson(plans)
                    val newPlan = currentPlan.copy(planJson = newJson)
                    trainingPlanDao.insertPlan(newPlan)
                }
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Error toggling daily plan completion for day: $day", e)
            }
        }
    }
}
