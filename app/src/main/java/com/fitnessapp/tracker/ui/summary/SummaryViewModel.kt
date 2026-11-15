package com.fitnessapp.tracker.ui.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.tracker.data.local.SettingsRepository
import com.fitnessapp.tracker.data.local.dao.PersonalRecordDao
import com.fitnessapp.tracker.data.local.dao.TwinSnapshotDao
import com.fitnessapp.tracker.data.local.dao.UserDao
import com.fitnessapp.tracker.data.local.dao.WellnessCheckInDao
import com.fitnessapp.tracker.data.local.dao.WorkoutSessionDao
import com.fitnessapp.tracker.data.local.entity.CheckInType
import com.fitnessapp.tracker.data.local.entity.UserEntity
import com.fitnessapp.tracker.data.local.entity.WellnessCheckInEntity
import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import com.fitnessapp.tracker.data.remote.AiCoachRepository
import com.fitnessapp.tracker.data.remote.EnvironmentContextRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.fitnessapp.tracker.engine.DigitalTwinEngine
import com.fitnessapp.tracker.engine.PersonalRecordAchievement
import com.fitnessapp.tracker.engine.PersonalRecordEngine
import com.fitnessapp.tracker.engine.RecoveryAdvice
import com.fitnessapp.tracker.engine.RecoveryEngine
import com.google.gson.Gson

data class SummaryUiState(
    val session: WorkoutSessionEntity? = null,
    val user: UserEntity = UserEntity(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isGeneratingDebrief: Boolean = false,
    val tacticalDebrief: String? = null,
    val newAchievements: List<PersonalRecordAchievement> = emptyList(),
    val recoveryAdvice: RecoveryAdvice? = null,
    val twinStrain: Double? = null,
    val isSavingFeedback: Boolean = false,
    val feedbackSaved: Boolean = false
)

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val sessionDao: WorkoutSessionDao,
    private val userDao: UserDao,
    private val personalRecordDao: PersonalRecordDao,
    private val twinSnapshotDao: TwinSnapshotDao,
    private val wellnessCheckInDao: WellnessCheckInDao,
    private val aiCoachRepository: AiCoachRepository,
    private val environmentContextRepository: EnvironmentContextRepository,
    private val settingsRepository: SettingsRepository,
    private val routeCryptoManager: com.fitnessapp.tracker.util.RouteCryptoManager,
    private val personalRecordEngine: PersonalRecordEngine,
    private val recoveryEngine: RecoveryEngine,
    private val digitalTwinEngine: DigitalTwinEngine,
    private val gson: Gson,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sessionId: Long = savedStateHandle.get<Long>("sessionId") ?: -1L

    private val _uiState = MutableStateFlow(SummaryUiState())
    val uiState: StateFlow<SummaryUiState> = _uiState

    init {
        loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            val user = userDao.getUser() ?: UserEntity()
            val rawSession = if (sessionId > 0) {
                sessionDao.getSessionById(sessionId)
            } else {
                // Load most recent session (after a workout completes)
                sessionDao.getRecentSessions(1).firstOrNull()
            }
            val session = rawSession?.let {
                it.copy(routePointsJson = routeCryptoManager.decryptRoute(it.routePointsJson))
            }

            // Read PRs already evaluated and persisted on save so viewing summaries is deterministic.
            val prAchievements = if (session != null) {
                personalRecordDao.getRecordsForActivity(session.activityType)
                    .filter { it.sessionId == session.id }
                    .map { record ->
                        PersonalRecordAchievement(
                            recordType = record.recordType,
                            activityType = record.activityType,
                            newValue = record.value,
                            previousValue = null,
                            formattedValue = personalRecordEngine.formatRecordValue(record.recordType, record.value),
                            isFirstTime = false
                        )
                    }
            } else emptyList()

            // Whole-body recovery must incorporate recent workouts across all activity types.
            val recentSessions = if (session != null) {
                sessionDao.getRecentSessions(15)
            } else emptyList()

            val recoveryAdvice = if (session != null) {
                recoveryEngine.computeRecoveryAdvice(
                    targetSession = session,
                    recentSessions = recentSessions,
                    user = user
                )
            } else null

            val sessionStrain = session?.let {
                digitalTwinEngine.calculateSessionTwinStrain(it, user.weightKg)
            }

            _uiState.value = SummaryUiState(
                session = session,
                user = user,
                isLoading = false,
                error = if (session == null) "No session found" else null,
                newAchievements = prAchievements,
                recoveryAdvice = recoveryAdvice,
                twinStrain = sessionStrain,
                feedbackSaved = session?.rpe != null || session?.wellnessFeel != null
            )
        }
    }

    fun savePostWorkoutFeedback(rpe: Int, wellnessFeel: Int, notes: String) {
        val session = _uiState.value.session ?: return
        if (_uiState.value.isSavingFeedback) return
        _uiState.value = _uiState.value.copy(isSavingFeedback = true)
        viewModelScope.launch {
            val cleanRpe = rpe.coerceIn(1, 10)
            val cleanFeel = wellnessFeel.coerceIn(1, 5)
            val cleanNotes = notes.trim()
            sessionDao.updatePostWorkoutFeedback(session.id, cleanRpe, cleanFeel, cleanNotes)

            // Record a post-workout subjective check-in so the Twin updates recovery & readiness immediately.
            val checkIn = WellnessCheckInEntity(
                timestampMs = System.currentTimeMillis(),
                type = CheckInType.POST_WORKOUT,
                sleepQuality = 3,
                energyLevel = cleanFeel,
                muscleSoreness = ((cleanRpe + 1) / 2).coerceIn(1, 5),
                stressLevel = (6 - cleanFeel).coerceIn(1, 5),
                hydrationScore = 3,
                notes = cleanNotes
            )
            wellnessCheckInDao.insertCheckIn(checkIn)

            val updatedSession = session.copy(
                rpe = cleanRpe,
                wellnessFeel = cleanFeel,
                perceivedNotes = cleanNotes
            )
            val recent = sessionDao.getRecentSessions(40)
            val env = environmentContextRepository.getEnvironmentContext()
            val twinState = digitalTwinEngine.computeTwinState(
                sessions = recent,
                user = _uiState.value.user,
                latestCheckIn = checkIn,
                environment = env,
                preferredActivityType = updatedSession.activityType
            )
            twinSnapshotDao.upsertSnapshot(digitalTwinEngine.toSnapshotEntity(twinState))

            val updatedStrain = digitalTwinEngine.calculateSessionTwinStrain(updatedSession, _uiState.value.user.weightKg)
            _uiState.value = _uiState.value.copy(
                session = updatedSession,
                twinStrain = updatedStrain,
                isSavingFeedback = false,
                feedbackSaved = true
            )
        }
    }

    fun generateTacticalDebrief(lapSummaries: List<LapSummary>?) {
        val currentSession = _uiState.value.session ?: return
        if (_uiState.value.isGeneratingDebrief) return

        _uiState.value = _uiState.value.copy(isGeneratingDebrief = true)
        viewModelScope.launch {
            val persona = settingsRepository.coachPersona.first()
            val language = settingsRepository.coachLanguage.first()
            val debrief = aiCoachRepository.generateTacticalDebrief(
                user = _uiState.value.user,
                session = currentSession,
                lapSummaries = lapSummaries,
                persona = persona,
                language = language
            )
            _uiState.value = _uiState.value.copy(
                isGeneratingDebrief = false,
                tacticalDebrief = debrief ?: "Could not generate tactical debrief. Please verify your connection or API key in Settings."
            )
        }
    }
}
