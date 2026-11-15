package com.fitnessapp.tracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.tracker.data.local.dao.UserDao
import com.fitnessapp.tracker.data.local.dao.WorkoutSessionDao
import com.fitnessapp.tracker.data.local.entity.UserEntity
import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.fitnessapp.tracker.data.local.RoutineProgress
import com.fitnessapp.tracker.data.local.RoutineRepository
import com.fitnessapp.tracker.domain.usecase.GetDashboardStatsUseCase

data class DashboardUiState(
    val user: UserEntity = UserEntity(),
    val sessions: List<WorkoutSessionEntity> = emptyList(),
    val totalDistanceKm: Double = 0.0,
    val avgDistanceKm: Double = 0.0,
    val totalSessions: Int = 0,
    val totalCalories: Double = 0.0,
    val routineProgress: RoutineProgress? = null,
    val personalRecords: List<com.fitnessapp.tracker.data.local.entity.PersonalRecordEntity> = emptyList(),
    val twinState: com.fitnessapp.tracker.engine.DigitalTwinState? = null,
    val isRefreshingEnvironment: Boolean = false,
    val isLoading: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val userDao: UserDao,
    private val sessionDao: WorkoutSessionDao,
    private val personalRecordDao: com.fitnessapp.tracker.data.local.dao.PersonalRecordDao,
    private val settingsRepository: com.fitnessapp.tracker.data.local.SettingsRepository,
    private val routineRepository: RoutineRepository,
    private val getDashboardStatsUseCase: GetDashboardStatsUseCase,
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

            val personalRecordsFlow = _activityType.flatMapLatest { type ->
                personalRecordDao.getRecordsForActivityFlow(type)
            }
            val twinInputsFlow = combine(
                wellnessCheckInDao.getLatestCheckInFlow(),
                _environment,
                _isRefreshingEnvironment,
                _activityType
            ) { checkIn, env, isRefreshing, activityType ->
                listOf(checkIn, env, isRefreshing, activityType)
            }
            val bundledMetaFlow = combine(personalRecordsFlow, twinInputsFlow) { records, twinList ->
                Pair(records, twinList)
            }
            combine(
                userDao.getUserFlow().map { it ?: UserEntity() },
                sessionStatsFlow,
                sessionDao.getAllSessionsFlow(),
                routineProgressFlow,
                bundledMetaFlow
            ) { user, sessionStats, allSessions, routineProgress, bundledMeta ->
                val (records, twinList) = bundledMeta
                val latestCheckIn = twinList[0] as com.fitnessapp.tracker.data.local.entity.WellnessCheckInEntity?
                val env = twinList[1] as com.fitnessapp.tracker.data.remote.EnvironmentSnapshot
                val isRefreshingEnv = twinList[2] as Boolean
                val preferredSport = twinList[3] as String

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
                    twinState = twinState,
                    isRefreshingEnvironment = isRefreshingEnv,
                    isLoading = false
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
}
