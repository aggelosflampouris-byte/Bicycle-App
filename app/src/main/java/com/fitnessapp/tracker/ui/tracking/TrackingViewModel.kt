package com.fitnessapp.tracker.ui.tracking

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.tracker.data.local.CoachLanguage
import com.fitnessapp.tracker.data.local.SettingsRepository
import com.fitnessapp.tracker.data.local.dao.UserDao
import com.fitnessapp.tracker.data.local.entity.UserEntity
import com.fitnessapp.tracker.data.remote.GeminiRepository
import com.fitnessapp.tracker.service.CyclingTrackingService
import com.fitnessapp.tracker.service.RoutePoint
import com.fitnessapp.tracker.service.TrackingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InFlightVoiceQueryState(
    val query: String? = null,
    val response: String? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val userDao: UserDao,
    private val geminiRepository: GeminiRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val trackingState: StateFlow<TrackingState> = CyclingTrackingService.trackingState
    val elapsedSeconds: StateFlow<Long> = CyclingTrackingService.elapsedSecondsFlow
    val routePoints: StateFlow<List<RoutePoint>> = CyclingTrackingService.routePointsFlow

    val coachLanguage: StateFlow<CoachLanguage> = settingsRepository.coachLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CoachLanguage.AUTO)

    val isVoiceCoachingEnabled: StateFlow<Boolean> = settingsRepository.isVoiceCoachingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _inFlightState = MutableStateFlow(InFlightVoiceQueryState())
    val inFlightState: StateFlow<InFlightVoiceQueryState> = _inFlightState.asStateFlow()

    private var cachedUser: UserEntity = UserEntity()

    init {
        viewModelScope.launch {
            cachedUser = userDao.getUser() ?: UserEntity()
        }
    }

    fun askInFlightCoach(spokenQuery: String) {
        if (spokenQuery.isBlank()) return
        viewModelScope.launch {
            _inFlightState.value = InFlightVoiceQueryState(query = spokenQuery, isLoading = true)
            val state = trackingState.value
            val persona = settingsRepository.coachPersona.first()
            val language = settingsRepository.coachLanguage.first()
            val activityType = settingsRepository.activityType.first()

            val distanceKm = "%.2f".format(state.distanceMeters / 1000.0)
            val speedKmh = "%.1f".format(state.speedKmh)
            val durationMins = state.elapsedSeconds / 60
            val elevationM = "%.0f".format(state.elevationGainMeters)
            val calories = "%.0f".format(state.calories)

            val inFlightTelemetry = """
                [CURRENT LIVE RIDE TELEMETRY]
                - Activity: $activityType
                - Distance: ${distanceKm} km
                - Current Speed: ${speedKmh} km/h
                - Elapsed Time: ${durationMins} minutes
                - Elevation Gain: ${elevationM} meters
                - Energy Burned: ${calories} kcal
                
                The user is currently mid-workout and asked you a quick question via voice.
                Provide a short, direct, motivational 1-2 sentence answer tailored to their live telemetry. Keep it under 35 words so it can be read easily via audio TTS.
            """.trimIndent()

            val fullSystemPrompt = geminiRepository.buildSystemPrompt(
                user = cachedUser,
                session = null,
                activityType = activityType,
                persona = persona,
                language = language
            ) + "\n\n" + inFlightTelemetry

            var responseAccumulator = ""
            try {
                geminiRepository.streamChat(
                    userMessage = spokenQuery,
                    systemPrompt = fullSystemPrompt,
                    history = emptyList()
                ).collect { chunk ->
                    responseAccumulator += chunk
                }
                val finalReply = responseAccumulator.ifBlank { "Keep up the great rhythm! Looking strong." }
                _inFlightState.value = InFlightVoiceQueryState(query = spokenQuery, response = finalReply, isLoading = false)
            } catch (e: Exception) {
                _inFlightState.value = InFlightVoiceQueryState(query = spokenQuery, response = "Keep pushing forward!", isLoading = false)
            }
        }
    }

    fun dismissInFlightResponse() {
        _inFlightState.value = InFlightVoiceQueryState()
    }

    fun startTracking(context: Context, activityType: String) {
        val intent = Intent(context, CyclingTrackingService::class.java).apply {
            action = CyclingTrackingService.ACTION_START
            putExtra(CyclingTrackingService.EXTRA_WEIGHT, cachedUser.weightKg)
            putExtra(CyclingTrackingService.EXTRA_GENDER, cachedUser.gender)
            putExtra(CyclingTrackingService.EXTRA_AGE, cachedUser.age)
            putExtra(CyclingTrackingService.EXTRA_ACTIVITY_TYPE, activityType)
        }
        context.startForegroundService(intent)
    }

    fun pauseTracking(context: Context) {
        val intent = Intent(context, CyclingTrackingService::class.java).apply {
            action = CyclingTrackingService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    fun resumeTracking(context: Context) {
        val intent = Intent(context, CyclingTrackingService::class.java).apply {
            action = CyclingTrackingService.ACTION_RESUME
        }
        context.startService(intent)
    }

    fun togglePause(context: Context) {
        val intent = Intent(context, CyclingTrackingService::class.java).apply {
            action = CyclingTrackingService.ACTION_TOGGLE_PAUSE
        }
        context.startService(intent)
    }

    fun markLap(context: Context) {
        val intent = Intent(context, CyclingTrackingService::class.java).apply {
            action = CyclingTrackingService.ACTION_LAP
        }
        context.startService(intent)
    }

    fun stopTracking(context: Context) {
        val intent = Intent(context, CyclingTrackingService::class.java).apply {
            action = CyclingTrackingService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun discardTracking(context: Context) {
        val intent = Intent(context, CyclingTrackingService::class.java).apply {
            action = CyclingTrackingService.ACTION_DISCARD
        }
        context.startService(intent)
    }

    fun clearLastSavedSessionId() {
        CyclingTrackingService.clearLastSavedSessionId()
    }
}
