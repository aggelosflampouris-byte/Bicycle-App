package com.fitnessapp.tracker.ui.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.tracker.data.local.SettingsRepository
import com.fitnessapp.tracker.data.local.dao.UserDao
import com.fitnessapp.tracker.data.local.dao.WorkoutSessionDao
import com.fitnessapp.tracker.data.local.entity.UserEntity
import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import com.fitnessapp.tracker.data.remote.GeminiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.fitnessapp.tracker.engine.PersonalRecordAchievement
import com.fitnessapp.tracker.engine.PersonalRecordEngine
import com.fitnessapp.tracker.engine.RecoveryAdvice
import com.fitnessapp.tracker.engine.RecoveryEngine
import com.fitnessapp.tracker.service.RoutePoint
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class SummaryUiState(
    val session: WorkoutSessionEntity? = null,
    val user: UserEntity = UserEntity(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isGeneratingDebrief: Boolean = false,
    val tacticalDebrief: String? = null,
    val newAchievements: List<PersonalRecordAchievement> = emptyList(),
    val recoveryAdvice: RecoveryAdvice? = null
)

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val sessionDao: WorkoutSessionDao,
    private val userDao: UserDao,
    private val geminiRepository: GeminiRepository,
    private val settingsRepository: SettingsRepository,
    private val routeCryptoManager: com.fitnessapp.tracker.util.RouteCryptoManager,
    private val personalRecordEngine: PersonalRecordEngine,
    private val recoveryEngine: RecoveryEngine,
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

            val routePoints: List<RoutePoint> = try {
                if (!session?.routePointsJson.isNullOrBlank() && session?.routePointsJson != "[]") {
                    val listType = object : TypeToken<List<RoutePoint>>() {}.type
                    gson.fromJson(session?.routePointsJson, listType) ?: emptyList()
                } else emptyList()
            } catch (e: Exception) {
                emptyList()
            }

            val prAchievements = if (session != null && sessionId <= 0) {
                personalRecordEngine.evaluateAndSaveRecords(session, routePoints)
            } else emptyList()

            val recentSessions = if (session != null) {
                sessionDao.getRecentSessionsByType(session.activityType, 15)
            } else emptyList()

            val recoveryAdvice = if (session != null) {
                recoveryEngine.computeRecoveryAdvice(
                    targetSession = session,
                    recentSessions = recentSessions,
                    user = user
                )
            } else null

            _uiState.value = SummaryUiState(
                session = session,
                user = user,
                isLoading = false,
                error = if (session == null) "No session found" else null,
                newAchievements = prAchievements,
                recoveryAdvice = recoveryAdvice
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
            val debrief = geminiRepository.generateTacticalDebrief(
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
