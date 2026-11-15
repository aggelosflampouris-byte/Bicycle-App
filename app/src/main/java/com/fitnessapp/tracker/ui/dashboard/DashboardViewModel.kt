package com.fitnessapp.tracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.tracker.data.local.dao.UserDao
import com.fitnessapp.tracker.data.local.dao.WorkoutSessionDao
import com.fitnessapp.tracker.data.local.entity.UserEntity
import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import com.fitnessapp.tracker.data.local.entity.TrainingPlanEntity
import com.fitnessapp.tracker.data.local.dao.TrainingPlanDao
import com.fitnessapp.tracker.data.remote.AiCoachRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.fitnessapp.tracker.data.local.RoutineProgress
import com.fitnessapp.tracker.data.local.RoutineRepository
import com.fitnessapp.tracker.domain.usecase.GetDashboardStatsUseCase
import com.fitnessapp.tracker.domain.usecase.GetRecoveryAdviceUseCase

data class DashboardUiState(
    val user: UserEntity = UserEntity(),
    val sessions: List<WorkoutSessionEntity> = emptyList(),
    val totalDistanceKm: Double = 0.0,
    val avgDistanceKm: Double = 0.0,
    val totalSessions: Int = 0,
    val totalCalories: Double = 0.0,
    val routineProgress: RoutineProgress? = null,
    val personalRecords: List<com.fitnessapp.tracker.data.local.entity.PersonalRecordEntity> = emptyList(),
    val trainingPlan: TrainingPlanEntity? = null,
    val recoveryAdvice: com.fitnessapp.tracker.engine.RecoveryAdvice? = null,
    val twinState: com.fitnessapp.tracker.engine.DigitalTwinState? = null,
    val isRefreshingEnvironment: Boolean = false,
    val isLoading: Boolean = false,
    val isGeneratingPlan: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val userDao: UserDao,
    private val sessionDao: WorkoutSessionDao,
    private val personalRecordDao: com.fitnessapp.tracker.data.local.dao.PersonalRecordDao,
    private val trainingPlanDao: TrainingPlanDao,
    private val aiCoachRepository: AiCoachRepository,
    private val settingsRepository: com.fitnessapp.tracker.data.local.SettingsRepository,
    private val routineRepository: RoutineRepository,
    private val getDashboardStatsUseCase: GetDashboardStatsUseCase,
    private val getRecoveryAdviceUseCase: GetRecoveryAdviceUseCase,
    private val gson: com.google.gson.Gson,
    private val wellnessCheckInDao: com.fitnessapp.tracker.data.local.dao.WellnessCheckInDao,
    private val environmentContextRepository: com.fitnessapp.tracker.data.remote.EnvironmentContextRepository,
    private val digitalTwinEngine: com.fitnessapp.tracker.engine.DigitalTwinEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _activityType = MutableStateFlow("CYCLING")
    private val _environment = MutableStateFlow(com.fitnessapp.tracker.data.remote.EnvironmentSnapshot())
    private val _isRefreshingEnvironment = MutableStateFlow(false)

    init {
        loadData()
        refreshEnvironment(forceRefresh = false)
        viewModelScope.launch {
            routineRepository.checkAndAdvanceRoutine(_activityType.value)
        }
    }

    fun onEvent(event: DashboardUiEvent) {
        when (event) {
            is DashboardUiEvent.SetActivityType -> setActivityType(event.type)
            is DashboardUiEvent.DeleteSession -> deleteSession(event.sessionId)
            is DashboardUiEvent.RefreshEnvironment -> refreshEnvironment(forceRefresh = true)
            is DashboardUiEvent.SubmitWellnessCheckIn -> submitWellnessCheckIn(event)
            is DashboardUiEvent.GenerateTrainingPlan -> generateTrainingPlan(event.goalPrompt)
            is DashboardUiEvent.ToggleDailyPlanCompleted -> toggleDailyPlanCompleted(event.day)
            is DashboardUiEvent.SaveRoutine -> saveRoutine(event.interval, event.metric, event.targetValue, event.autoImprove)
            is DashboardUiEvent.DeleteRoutine -> deleteRoutine()
        }
    }

    private fun setActivityType(type: String, force: Boolean = false) {
        viewModelScope.launch {
            _activityType.value = type
            settingsRepository.setActivityType(type)
        }
    }

    private fun refreshEnvironment(forceRefresh: Boolean) {
        viewModelScope.launch {
            _isRefreshingEnvironment.value = true
            try {
                _environment.value = environmentContextRepository.getEnvironmentContext(
                    forceRefresh = forceRefresh
                )
            } finally {
                _isRefreshingEnvironment.value = false
            }
        }
    }

    private fun submitWellnessCheckIn(event: DashboardUiEvent.SubmitWellnessCheckIn) {
        viewModelScope.launch {
            wellnessCheckInDao.insertCheckIn(
                com.fitnessapp.tracker.data.local.entity.WellnessCheckInEntity(
                    timestampMs = System.currentTimeMillis(),
                    type = event.type,
                    sleepQuality = event.sleepQuality.coerceIn(1, 5),
                    energyLevel = event.energyLevel.coerceIn(1, 5),
                    muscleSoreness = event.muscleSoreness.coerceIn(1, 5),
                    stressLevel = event.stressLevel.coerceIn(1, 5),
                    hydrationScore = event.hydrationScore.coerceIn(1, 5),
                    notes = event.notes.trim()
                )
            )
        }
    }

    private fun saveRoutine(interval: com.fitnessapp.tracker.data.local.entity.RoutineInterval, metric: com.fitnessapp.tracker.data.local.entity.RoutineMetric, targetValue: Double, autoImprove: Boolean) {
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
    
    private fun deleteRoutine() {
        viewModelScope.launch {
            routineRepository.deleteRoutine(_activityType.value)
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            val routineProgressFlow = _activityType.flatMapLatest { type ->
                routineRepository.getRoutineProgressFlow(type)
            }
            val sessionStatsFlow = _activityType.flatMapLatest { type -> 
                getDashboardStatsUseCase(type)
            }

            val coachingAndRecordsFlow = combine(
                trainingPlanDao.getPlanFlow(),
                _activityType.flatMapLatest { type ->
                    personalRecordDao.getRecordsForActivityFlow(type)
                }
            ) { plan, records ->
                Pair(plan, records)
            }
            val twinInputsFlow = combine(
                wellnessCheckInDao.getLatestCheckInFlow(),
                _environment,
                _isRefreshingEnvironment,
                _activityType
            ) { checkIn, env, isRefreshing, activityType ->
                listOf(checkIn, env, isRefreshing, activityType)
            }
            val bundledMetaFlow = combine(coachingAndRecordsFlow, twinInputsFlow) { coachingAndRecords, twinList ->
                Pair(coachingAndRecords, twinList)
            }
            combine(
                userDao.getUserFlow().map { it ?: UserEntity() },
                sessionStatsFlow,
                sessionDao.getAllSessionsFlow(),
                routineProgressFlow,
                bundledMetaFlow
            ) { user, sessionStats, allSessions, routineProgress, bundledMeta ->
                val (coachingAndRecords, twinList) = bundledMeta
                val (plan, records) = coachingAndRecords
                val latestCheckIn = twinList[0] as com.fitnessapp.tracker.data.local.entity.WellnessCheckInEntity?
                val env = twinList[1] as com.fitnessapp.tracker.data.remote.EnvironmentSnapshot
                val isRefreshingEnv = twinList[2] as Boolean
                val preferredSport = twinList[3] as String

                // Whole-body recovery must consider every recorded activity, not just the selected dashboard tab.
                val recovery = getRecoveryAdviceUseCase(allSessions, user)
                val twinState = digitalTwinEngine.computeTwinState(
                    sessions = allSessions,
                    user = user,
                    latestCheckIn = latestCheckIn,
                    environment = env,
                    preferredActivityType = preferredSport
                )

                DashboardUiState(
                    user = user,
                    sessions = sessionStats.sessions,
                    totalDistanceKm = sessionStats.totalDistanceKm,
                    avgDistanceKm = sessionStats.avgDistanceKm,
                    totalSessions = sessionStats.sessions.size,
                    totalCalories = sessionStats.totalCalories,
                    routineProgress = routineProgress,
                    personalRecords = records,
                    trainingPlan = plan,
                    recoveryAdvice = recovery,
                    twinState = twinState,
                    isRefreshingEnvironment = isRefreshingEnv,
                    isLoading = false,
                    isGeneratingPlan = _uiState.value.isGeneratingPlan
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun deleteSession(id: Long) {
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

    private fun generateTrainingPlan(goal: String = "Balanced Endurance") {
        if (_uiState.value.isGeneratingPlan) return
        _uiState.value = _uiState.value.copy(isGeneratingPlan = true)
        
        viewModelScope.launch {
            val user = userDao.getUser() ?: UserEntity()
            val persona = settingsRepository.coachPersona.first()
            // Provide the last 7 days of sessions for context
            val oneWeekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            val recentSessions = sessionDao.getAllSessionsFlow().first().filter { it.startTime >= oneWeekAgo }
            
            val dailyPlans = aiCoachRepository.generateWeeklyPlan(user, recentSessions, goal, persona)
            if (dailyPlans != null) {
                val json = gson.toJson(dailyPlans)
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

    private fun toggleDailyPlanCompleted(day: String) {
        viewModelScope.launch {
            val currentPlan = _uiState.value.trainingPlan ?: return@launch
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<com.fitnessapp.tracker.data.local.entity.DailyPlan>>() {}.type
                val plans: MutableList<com.fitnessapp.tracker.data.local.entity.DailyPlan> = gson.fromJson(currentPlan.planJson, type)
                
                val index = plans.indexOfFirst { it.day == day }
                if (index != -1) {
                    val plan = plans[index]
                    plans[index] = plan.copy(isCompleted = !plan.isCompleted)
                    val newJson = gson.toJson(plans)
                    val newPlan = currentPlan.copy(planJson = newJson)
                    trainingPlanDao.insertPlan(newPlan)
                }
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Error toggling daily plan completion for day: $day", e)
            }
        }
    }
}
