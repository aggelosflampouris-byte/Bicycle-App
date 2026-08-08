package com.example.smartcyclingtracker.ui.tracking

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartcyclingtracker.data.local.dao.UserDao
import com.example.smartcyclingtracker.data.local.entity.UserEntity
import com.example.smartcyclingtracker.service.CyclingTrackingService
import com.example.smartcyclingtracker.service.TrackingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val userDao: UserDao
) : ViewModel() {

    val trackingState: StateFlow<TrackingState> = CyclingTrackingService.trackingState

    private var cachedUser: UserEntity = UserEntity()

    init {
        viewModelScope.launch {
            cachedUser = userDao.getUser() ?: UserEntity()
        }
    }

    fun startTracking(context: Context) {
        val intent = Intent(context, CyclingTrackingService::class.java).apply {
            action = CyclingTrackingService.ACTION_START
            putExtra(CyclingTrackingService.EXTRA_WEIGHT, cachedUser.weightKg)
            putExtra(CyclingTrackingService.EXTRA_GENDER, cachedUser.gender)
            putExtra(CyclingTrackingService.EXTRA_AGE, cachedUser.age)
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
}
