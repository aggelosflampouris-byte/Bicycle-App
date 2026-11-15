package com.fitnessapp.tracker.ui.tracking

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.tracker.data.local.AudioLanguage
import com.fitnessapp.tracker.data.local.SettingsRepository
import com.fitnessapp.tracker.data.local.dao.UserDao
import com.fitnessapp.tracker.data.local.entity.UserEntity
import com.fitnessapp.tracker.service.OutdoorTrackingService
import com.fitnessapp.tracker.service.RoutePoint
import com.fitnessapp.tracker.service.TrackingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val userDao: UserDao,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val trackingState: StateFlow<TrackingState> = OutdoorTrackingService.trackingState
    val elapsedSeconds: StateFlow<Long> = OutdoorTrackingService.elapsedSecondsFlow
    val routePoints: StateFlow<List<RoutePoint>> = OutdoorTrackingService.routePointsFlow

    val audioLanguage: StateFlow<AudioLanguage> = settingsRepository.audioLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AudioLanguage.AUTO)

    val isAudioCuesEnabled: StateFlow<Boolean> = settingsRepository.isAudioCuesEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private var cachedUser: UserEntity = UserEntity()

    init {
        viewModelScope.launch {
            cachedUser = userDao.getUser() ?: UserEntity()
        }
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



    fun toggleAudioCues() {
        viewModelScope.launch {
            val current = isAudioCuesEnabled.value
            settingsRepository.setAudioCuesEnabled(!current)
        }
    }

    fun clearLastSavedSessionId() {
        OutdoorTrackingService.clearLastSavedSessionId()
    }
}
