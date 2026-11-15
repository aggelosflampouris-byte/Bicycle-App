package com.fitnessapp.tracker.ui.twin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.tracker.data.local.SettingsRepository
import com.fitnessapp.tracker.data.local.dao.TwinSnapshotDao
import com.fitnessapp.tracker.data.local.dao.UserDao
import com.fitnessapp.tracker.data.local.dao.WellnessCheckInDao
import com.fitnessapp.tracker.data.local.dao.WorkoutSessionDao
import com.fitnessapp.tracker.data.local.entity.CheckInType
import com.fitnessapp.tracker.data.local.entity.TwinSnapshotEntity
import com.fitnessapp.tracker.data.local.entity.UserEntity
import com.fitnessapp.tracker.data.local.entity.WellnessCheckInEntity
import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import com.fitnessapp.tracker.data.remote.EnvironmentContextRepository
import com.fitnessapp.tracker.data.remote.EnvironmentSnapshot
import com.fitnessapp.tracker.engine.DigitalTwinEngine
import com.fitnessapp.tracker.engine.DigitalTwinState
import com.fitnessapp.tracker.engine.SimulationOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SimulationInput(
    val activityType: String = "CYCLING",
    val durationMinutes: Int = 45,
    val targetRpe: Int = 6,
    val elevationGainM: Double = 150.0
)

data class TwinUiState(
    val twinState: DigitalTwinState = DigitalTwinState(),
    val simInput: SimulationInput = SimulationInput(),
    val simulationOutcome: SimulationOutcome? = null,
    val recentSnapshots: List<TwinSnapshotEntity> = emptyList(),
    val isRefreshingWeather: Boolean = false,
    val checkInSavedAtMs: Long? = null
)

private data class TwinBaseInputs(
    val user: UserEntity,
    val sessions: List<WorkoutSessionEntity>,
    val latestCheckIn: WellnessCheckInEntity?,
    val preferredActivity: String,
    val snapshots: List<TwinSnapshotEntity>
)

@HiltViewModel
class TwinViewModel @Inject constructor(
    private val userDao: UserDao,
    private val workoutSessionDao: WorkoutSessionDao,
    private val wellnessCheckInDao: WellnessCheckInDao,
    private val twinSnapshotDao: TwinSnapshotDao,
    private val settingsRepository: SettingsRepository,
    private val environmentContextRepository: EnvironmentContextRepository,
    private val digitalTwinEngine: DigitalTwinEngine
) : ViewModel() {

    private val _environment = MutableStateFlow(EnvironmentSnapshot())
    private val _simInput = MutableStateFlow(SimulationInput())
    private val _isRefreshingWeather = MutableStateFlow(false)
    private val _checkInSavedAtMs = MutableStateFlow<Long?>(null)

    private val baseInputsFlow = combine(
        userDao.getUserFlow(),
        workoutSessionDao.getAllSessionsFlow(),
        wellnessCheckInDao.getLatestCheckInFlow(),
        settingsRepository.activityType,
        twinSnapshotDao.getRecentSnapshotsFlow(14)
    ) { user, sessions, checkIn, activityType, snapshots ->
        TwinBaseInputs(
            user = user ?: UserEntity(),
            sessions = sessions,
            latestCheckIn = checkIn,
            preferredActivity = activityType,
            snapshots = snapshots
        )
    }

    val uiState: StateFlow<TwinUiState> = combine(
        baseInputsFlow,
        _environment,
        _simInput,
        _isRefreshingWeather,
        _checkInSavedAtMs
    ) { base, env, simInput, isRefreshing, savedAt ->
        val twinState = digitalTwinEngine.computeTwinState(
            sessions = base.sessions,
            user = base.user,
            latestCheckIn = base.latestCheckIn,
            environment = env,
            preferredActivityType = base.preferredActivity
        )
        val simOutcome = digitalTwinEngine.simulateWorkout(
            state = twinState,
            user = base.user,
            activityType = simInput.activityType,
            durationMinutes = simInput.durationMinutes,
            targetRpe = simInput.targetRpe,
            elevationGainM = simInput.elevationGainM
        )
        TwinUiState(
            twinState = twinState,
            simInput = simInput,
            simulationOutcome = simOutcome,
            recentSnapshots = base.snapshots,
            isRefreshingWeather = isRefreshing,
            checkInSavedAtMs = savedAt
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TwinUiState()
    )

    init {
        viewModelScope.launch {
            val preferred = settingsRepository.activityType.first()
            _simInput.value = _simInput.value.copy(activityType = preferred)
        }
        refreshEnvironment(forceRefresh = false)
    }

    fun refreshEnvironment(forceRefresh: Boolean = true) {
        viewModelScope.launch {
            _isRefreshingWeather.value = true
            try {
                _environment.value = environmentContextRepository.getEnvironmentContext(
                    forceRefresh = forceRefresh
                )
                persistCurrentSnapshot()
            } finally {
                _isRefreshingWeather.value = false
            }
        }
    }

    fun submitCheckIn(
        sleepQuality: Int,
        energyLevel: Int,
        muscleSoreness: Int,
        stressLevel: Int,
        hydrationScore: Int,
        notes: String = "",
        type: CheckInType = CheckInType.DAILY
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            wellnessCheckInDao.insertCheckIn(
                WellnessCheckInEntity(
                    timestampMs = now,
                    type = type,
                    sleepQuality = sleepQuality.coerceIn(1, 5),
                    energyLevel = energyLevel.coerceIn(1, 5),
                    muscleSoreness = muscleSoreness.coerceIn(1, 5),
                    stressLevel = stressLevel.coerceIn(1, 5),
                    hydrationScore = hydrationScore.coerceIn(1, 5),
                    notes = notes.trim()
                )
            )
            _checkInSavedAtMs.value = now
            persistCurrentSnapshot()
        }
    }

    fun updateSimulation(
        activityType: String = _simInput.value.activityType,
        durationMinutes: Int = _simInput.value.durationMinutes,
        targetRpe: Int = _simInput.value.targetRpe,
        elevationGainM: Double = _simInput.value.elevationGainM
    ) {
        _simInput.value = SimulationInput(
            activityType = activityType,
            durationMinutes = durationMinutes.coerceIn(15, 180),
            targetRpe = targetRpe.coerceIn(2, 10),
            elevationGainM = elevationGainM.coerceIn(0.0, 1500.0)
        )
    }

    private suspend fun persistCurrentSnapshot() {
        val snapshot = digitalTwinEngine.toSnapshotEntity(uiState.value.twinState)
        twinSnapshotDao.upsertSnapshot(snapshot)
    }
}
