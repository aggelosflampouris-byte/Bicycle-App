package com.fitnessapp.tracker.ui.tracking

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.tracker.data.local.CoachLanguage
import com.fitnessapp.tracker.data.local.SettingsRepository
import com.fitnessapp.tracker.data.local.dao.UserDao
import com.fitnessapp.tracker.data.local.entity.UserEntity
import com.fitnessapp.tracker.data.remote.AiCoachRepository
import com.fitnessapp.tracker.service.OutdoorTrackingService
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
    private val aiCoachRepository: AiCoachRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val trackingState: StateFlow<TrackingState> = OutdoorTrackingService.trackingState
    val elapsedSeconds: StateFlow<Long> = OutdoorTrackingService.elapsedSecondsFlow
    val routePoints: StateFlow<List<RoutePoint>> = OutdoorTrackingService.routePointsFlow

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
            val activityType = state.activityType

            val distanceKm = "%.2f".format(state.distanceMeters / 1000.0)
            val speedKmh = "%.1f".format(state.speedKmh)
            val durationMins = state.elapsedSeconds / 60
            val elevationM = "%.0f".format(state.elevationGainMeters)
            val calories = "%.0f".format(state.calories)

            val telemetryLines = buildString {
                appendLine("[CURRENT LIVE WORKOUT TELEMETRY]")
                appendLine("- Activity: $activityType")
                appendLine("- Distance: ${distanceKm} km")
                appendLine("- Current Speed: ${speedKmh} km/h")
                appendLine("- Elapsed Time: ${durationMins} minutes")
                appendLine("- Elevation Gain: ${elevationM} meters")
                appendLine("- Energy Burned: ${calories} kcal")
                if (state.liveCadenceSpm > 0.0) {
                    appendLine("- Live Cadence: ${"%.0f".format(state.liveCadenceSpm)} spm")
                }
                state.gaitStabilityScore?.let {
                    appendLine("- Sensor Stability Score: ${"%.0f".format(it)}/100")
                }
                state.currentWeatherSummary?.let {
                    appendLine("- Outdoor Weather: $it")
                }
                state.liveRpeTag?.let {
                    appendLine("- Self-Reported Effort (RPE): $it/10")
                }
                appendLine()
                append("The user is currently mid-workout and asked you a quick question via voice. Provide a short, direct, motivational 1-2 sentence answer tailored to their live Digital Twin telemetry. Keep it under 35 words so it can be read easily via audio TTS.")
            }

            val inFlightTelemetry = telemetryLines

            val fullSystemPrompt = aiCoachRepository.buildSystemPrompt(
                user = cachedUser,
                session = null,
                activityType = activityType,
                persona = persona,
                language = language
            ) + "\n\n" + inFlightTelemetry

            var responseAccumulator = ""
            try {
                aiCoachRepository.streamChat(
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
        val intent = Intent(context, OutdoorTrackingService::class.java).apply {
            action = OutdoorTrackingService.ACTION_START
            putExtra(OutdoorTrackingService.EXTRA_WEIGHT, cachedUser.weightKg)
            putExtra(OutdoorTrackingService.EXTRA_GENDER, cachedUser.gender)
            putExtra(OutdoorTrackingService.EXTRA_AGE, cachedUser.age)
            putExtra(OutdoorTrackingService.EXTRA_ACTIVITY_TYPE, activityType)
        }
        context.startForegroundService(intent)
    }



    fun togglePause(context: Context) {
        val intent = Intent(context, OutdoorTrackingService::class.java).apply {
            action = OutdoorTrackingService.ACTION_TOGGLE_PAUSE
        }
        context.startService(intent)
    }

    fun markLap(context: Context) {
        val intent = Intent(context, OutdoorTrackingService::class.java).apply {
            action = OutdoorTrackingService.ACTION_LAP
        }
        context.startService(intent)
    }

    fun stopTracking(context: Context) {
        val intent = Intent(context, OutdoorTrackingService::class.java).apply {
            action = OutdoorTrackingService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun discardTracking(context: Context) {
        val intent = Intent(context, OutdoorTrackingService::class.java).apply {
            action = OutdoorTrackingService.ACTION_DISCARD
        }
        context.startService(intent)
    }

    val isCrashDetectionEnabled: StateFlow<Boolean> = settingsRepository.isCrashDetectionEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val emergencyContactName: StateFlow<String> = settingsRepository.emergencyContactName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val emergencyContactPhone: StateFlow<String> = settingsRepository.emergencyContactPhone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun loadGpxRoute(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val route = com.fitnessapp.tracker.navigation.GpxParser.parse(inputStream)
                    OutdoorTrackingService.setGpxRoute(route)
                }
            } catch (e: Exception) {
                android.util.Log.e("TrackingViewModel", "Error loading GPX file", e)
            }
        }
    }

    fun clearGpxRoute() {
        OutdoorTrackingService.setGpxRoute(null)
    }

    fun cancelSos() {
        OutdoorTrackingService.cancelCrashSos()
    }

    fun setLiveRpe(rpe: Int?) {
        OutdoorTrackingService.setLiveRpe(rpe)
    }

    fun dismissTwinNudge() {
        OutdoorTrackingService.dismissTwinNudge()
    }



    fun toggleVoiceCoaching() {
        viewModelScope.launch {
            val current = isVoiceCoachingEnabled.value
            settingsRepository.setVoiceCoachingEnabled(!current)
        }
    }

    fun clearLastSavedSessionId() {
        OutdoorTrackingService.clearLastSavedSessionId()
    }
}
