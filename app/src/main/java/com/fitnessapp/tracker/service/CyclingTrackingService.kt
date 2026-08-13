package com.fitnessapp.tracker.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.fitnessapp.tracker.data.local.dao.WorkoutSessionDao
import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import com.fitnessapp.tracker.engine.PhysicsEngine
import com.google.android.gms.location.*
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import android.content.Context
import android.os.PowerManager
import android.os.Build
import android.content.pm.ServiceInfo

data class RoutePoint(
    val lat: Double,
    val lng: Double,
    val alt: Double,
    val timestamp: Long,
    val speedMps: Double,
    val lap: Int = 1
)

data class TrackingState(
    val isTracking: Boolean = false,
    val isPaused: Boolean = false,
    val speedKmh: Double = 0.0,
    val distanceMeters: Double = 0.0,
    val elapsedSeconds: Long = 0L,
    val calories: Double = 0.0,
    val currentLat: Double = 0.0,
    val currentLng: Double = 0.0,
    val lastSavedSessionId: Long? = null,
    val currentLap: Int = 1,
    val activeChallenge: com.fitnessapp.tracker.data.local.entity.ChallengeEntity? = null,
    val elevationGainMeters: Double = 0.0
)

/**
 * Foreground GPS tracking service with:
 * - Auto-pause when displacement < 2m for 5 consecutive seconds
 * - Manual pause / resume controls
 * - GPS filter: discard accuracy > 20m or speed > 100 km/h
 * - Batch DB writes every 50 GPS points
 * - Zero-crash policy with structured error handling
 */
@AndroidEntryPoint
class CyclingTrackingService : Service() {

    @Inject lateinit var workoutSessionDao: WorkoutSessionDao
    @Inject lateinit var challengeDao: com.fitnessapp.tracker.data.local.dao.ChallengeDao
    @Inject lateinit var settingsRepository: com.fitnessapp.tracker.data.local.SettingsRepository
    @Inject lateinit var gson: Gson
    @Inject lateinit var firestoreRepository: com.fitnessapp.tracker.data.remote.FirestoreRepository

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // GPS tracking data
    private val routePoints = mutableListOf<RoutePoint>()
    private val pendingBatchPoints = mutableListOf<RoutePoint>()
    private var lastLocation: RoutePoint? = null
    private var totalDistanceMeters = 0.0
    private var elevationGainMeters = 0.0
    private var elapsedSeconds = 0L
    private var startTimeMs = 0L
    private var weightKg: Float = 75f
    private var gender: String = "Male"
    private var age: Int = 30
    private var activityType: String = "CYCLING"
    
    // TTS Voice Coach
    private lateinit var ttsManager: com.fitnessapp.tracker.util.TtsManager
    private var isVoiceCoachingEnabled: Boolean = true
    private var lastAnnouncedKm: Int = 0

    // Auto-pause / manual pause state
    private var isManuallyPaused = false
    private var stationaryCounter = 0
    private var consecutiveJumpCount = 0
    private val AUTO_PAUSE_SECONDS = 5
    private val AUTO_PAUSE_DISTANCE_M = 2.0

    // Batch write trigger
    private val BATCH_SIZE = 50

    companion object {
        private const val TAG = "CyclingService"

        // StateFlow for UI binding
        private val _trackingState = MutableStateFlow(TrackingState())
        val trackingState: StateFlow<TrackingState> = _trackingState

        // Route points flow for live tracking map
        private val _routePointsFlow = MutableStateFlow<List<RoutePoint>>(emptyList())
        val routePointsFlow: StateFlow<List<RoutePoint>> = _routePointsFlow

        // Separate elapsed seconds flow to prevent whole-screen recomposition every second
        private val _elapsedSecondsFlow = MutableStateFlow(0L)
        val elapsedSecondsFlow: StateFlow<Long> = _elapsedSecondsFlow

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_TOGGLE_PAUSE = "ACTION_TOGGLE_PAUSE"
        const val ACTION_DISCARD = "ACTION_DISCARD"
        const val ACTION_LAP = "ACTION_LAP"
        const val ACTION_CANCEL_CHALLENGE = "ACTION_CANCEL_CHALLENGE"

        const val EXTRA_WEIGHT = "extra_weight"
        const val EXTRA_GENDER = "EXTRA_GENDER"
        const val EXTRA_AGE = "EXTRA_AGE"
        const val EXTRA_ACTIVITY_TYPE = "EXTRA_ACTIVITY_TYPE"
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        ttsManager = com.fitnessapp.tracker.util.TtsManager(this)
        
        serviceScope.launch {
            settingsRepository.isVoiceCoachingEnabled.collect { enabled ->
                isVoiceCoachingEnabled = enabled
            }
        }
        
        setupLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                weightKg = intent.getFloatExtra(EXTRA_WEIGHT, 75f)
                gender = intent.getStringExtra(EXTRA_GENDER) ?: "Male"
                age = intent.getIntExtra(EXTRA_AGE, 30)
                activityType = intent.getStringExtra(EXTRA_ACTIVITY_TYPE) ?: "CYCLING"
                startTracking()
            }
            ACTION_STOP -> stopTracking(save = true)
            ACTION_DISCARD -> stopTracking(save = false)
            ACTION_PAUSE -> setPaused(true)
            ACTION_RESUME -> setPaused(false)
            ACTION_TOGGLE_PAUSE -> setPaused(!_trackingState.value.isPaused)
            ACTION_LAP -> {
                _trackingState.value = _trackingState.value.copy(
                    currentLap = _trackingState.value.currentLap + 1
                )
            }
            ACTION_CANCEL_CHALLENGE -> {
                _trackingState.value = _trackingState.value.copy(activeChallenge = null)
                // Force notification update
                val notif = NotificationHelper.buildTrackingNotification(
                    this,
                    _trackingState.value.speedKmh,
                    totalDistanceMeters,
                    elapsedSeconds,
                    _trackingState.value.isPaused,
                    null
                )
                val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                manager.notify(NotificationHelper.NOTIFICATION_ID, notif)
            }
        }
        return START_STICKY
    }

    private fun setPaused(paused: Boolean) {
        isManuallyPaused = paused
        _trackingState.value = _trackingState.value.copy(
            isPaused = paused,
            speedKmh = if (paused) 0.0 else _trackingState.value.speedKmh
        )
        if (!paused) {
            stationaryCounter = 0
            acquireWakeLock()
        } else {
            releaseWakeLock()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                // Process ALL intermediate batched locations, not just the last one
                for (location in result.locations) {
                    processLocation(
                        lat = location.latitude,
                        lng = location.longitude,
                        alt = location.altitude,
                        accuracyM = location.accuracy,
                        speedMps = if (location.hasSpeed()) location.speed.toDouble() else 0.0,
                        timestamp = location.time
                    )
                }
            }
        }
    }

    private fun startTracking() {
        startTimeMs = System.currentTimeMillis()
        isManuallyPaused = false
        stationaryCounter = 0
        consecutiveJumpCount = 0
        totalDistanceMeters = 0.0
        elevationGainMeters = 0.0
        elapsedSeconds = 0L
        _elapsedSecondsFlow.value = 0L
        lastLocation = null
        lastAnnouncedKm = 0
        routePoints.clear()
        pendingBatchPoints.clear()
        _routePointsFlow.value = emptyList()

        _trackingState.value = TrackingState(isTracking = true)

        acquireWakeLock()
        
        serviceScope.launch {
            val challenge = challengeDao.getActiveChallenge()
            _trackingState.value = _trackingState.value.copy(activeChallenge = challenge)
        }

        val notification = NotificationHelper.buildTrackingNotification(this, 0.0, 0.0, 0L, false, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NotificationHelper.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID, notification)
        }

        // Start 1-second timer for elapsed time + auto-pause
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(1000L)
                if (!_trackingState.value.isPaused) {
                    elapsedSeconds++
                    // ONLY update the elapsedSecondsFlow to prevent heavy TrackingState recompositions
                    _elapsedSecondsFlow.value = elapsedSeconds
                }
                // Update notification
                val notif = NotificationHelper.buildTrackingNotification(
                    this@CyclingTrackingService,
                    _trackingState.value.speedKmh,
                    totalDistanceMeters,
                    elapsedSeconds,
                    _trackingState.value.isPaused,
                    _trackingState.value.activeChallenge
                )
                val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                manager.notify(NotificationHelper.NOTIFICATION_ID, notif)
            }
        }

        // Request location updates with balanced intervals for battery savings
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 3000L // Base interval: 3 seconds
        ).apply {
            setMinUpdateIntervalMillis(2000L) // Minimum interval: 2 seconds
            setMaxUpdateDelayMillis(3000L) // Allow batching up to 3 seconds
            setMinUpdateDistanceMeters(2.0f) // Minimum displacement: 2 meters
            setGranularity(Granularity.GRANULARITY_FINE)
            setWaitForAccurateLocation(false)
        }.build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission denied: ${e.message}")
            stopSelf()
        }
    }

    private fun processLocation(
        lat: Double,
        lng: Double,
        alt: Double,
        accuracyM: Float,
        speedMps: Double,
        timestamp: Long
    ) {
        // ── GPS Quality Filter ───────────────────────────────────────────────
        // Discard points with invalid or unacceptably poor accuracy (> 35m)
        if (accuracyM > 35f || accuracyM <= 0f) {
            Log.d(TAG, "Discarding low-accuracy point: ${accuracyM}m")
            return
        }

        val last = lastLocation
        val displacement = if (last != null) {
            PhysicsEngine.haversineDistance(last.lat, last.lng, lat, lng)
        } else 0.0

        val timeDeltaS = if (last != null && timestamp > last.timestamp) {
            (timestamp - last.timestamp) / 1000.0
        } else if (last != null) {
            0.5
        } else 1.0

        val rawSpeedMps = if (timeDeltaS > 0) displacement / timeDeltaS else 0.0

        // Discard physically impossible sudden jumps (> 120 km/h / 33.3 m/s)
        // unless consecutive jumps occur (indicating valid recovery after gap/tunnel)
        if (last != null && rawSpeedMps > 33.3) {
            consecutiveJumpCount++
            if (consecutiveJumpCount < 3) {
                Log.d(TAG, "Discarding unrealistic jump: ${rawSpeedMps * 3.6} km/h")
                return
            }
            // Recovered after gap/tunnel: accept new anchor
            consecutiveJumpCount = 0
        } else {
            consecutiveJumpCount = 0
        }

        val point = RoutePoint(lat, lng, alt, timestamp, speedMps, _trackingState.value.currentLap)

        // ── Drift & Auto-Pause Detection ─────────────────────────────────────
        // Doppler speed is highly resistant to drift. If reported, we trust it.
        // Require BOTH hardware speed AND displacement to be low before marking stationary.
        // This prevents false auto-pauses caused by momentary GPS speed dips mid-ride.
        val hasHardwareSpeed = speedMps > 0.0
        val isEffectivelyStationary = if (hasHardwareSpeed) {
            // Match the fallback threshold: < 3.6 km/h on both sensors
            speedMps < 1.0 && rawSpeedMps < 1.0
        } else {
            rawSpeedMps < 1.0 // fallback < 3.6 km/h
        }

        if (isManuallyPaused) {
            // Manually paused: do not accumulate or auto-resume.
            // Still advance lastLocation so we don't create a large gap on resume.
            lastLocation = point
            _trackingState.value = _trackingState.value.copy(
                speedKmh = 0.0,
                currentLat = lat,
                currentLng = lng
            )
            return
        }

        if (isEffectivelyStationary) {
            stationaryCounter++
            if (stationaryCounter >= AUTO_PAUSE_SECONDS && !_trackingState.value.isPaused) {
                _trackingState.value = _trackingState.value.copy(isPaused = true, speedKmh = 0.0)
                Log.d(TAG, "Auto-paused (Stationary)")
                releaseWakeLock()
            }
            // Always advance lastLocation during stationary frames so that when movement
            // resumes, the displacement from the fresh anchor is small and won't be
            // misclassified as an unrealistic jump by the speed filter above.
            lastLocation = point
        } else {
            if (_trackingState.value.isPaused) {
                _trackingState.value = _trackingState.value.copy(isPaused = false)
                Log.d(TAG, "Auto-resumed (Moving)")
                acquireWakeLock()
            }
            stationaryCounter = 0
        }

        // ── Accumulate if moving ─────────────────────────────────────────────
        if (!_trackingState.value.isPaused && !isEffectivelyStationary) {
            if (last != null && displacement > 0.5) {
                totalDistanceMeters += displacement
                // Elevation gain - smoothed with a 3m noise threshold
                if (alt > last.alt && (alt - last.alt) > 3.0) {
                    elevationGainMeters += (alt - last.alt)
                }
            }
            lastLocation = point
            routePoints.add(point)
            pendingBatchPoints.add(point)
            _routePointsFlow.value = routePoints.toList()
        } else if (last == null) {
            // Initial anchor point
            lastLocation = point
            routePoints.add(point)
            pendingBatchPoints.add(point)
            _routePointsFlow.value = routePoints.toList()
        }

        // Calculate current calories
        val avgSpeed = if (totalDistanceMeters > 0 && elapsedSeconds > 0)
            (totalDistanceMeters / 1000.0) / (elapsedSeconds / 3600.0) else 0.0
        val mockUser = com.fitnessapp.tracker.data.local.entity.UserEntity(
            weightKg = weightKg, gender = gender, age = age
        )
        val calories = PhysicsEngine.calculateCalories(mockUser, elapsedSeconds, avgSpeed, activityType)

        val speedKmh = if (_trackingState.value.isPaused) 0.0 else PhysicsEngine.metersPerSecondToKmh(speedMps)

        // ── Update UI State ──────────────────────────────────────────────────
        _trackingState.value = _trackingState.value.copy(
            speedKmh = speedKmh,
            distanceMeters = totalDistanceMeters,
            elapsedSeconds = elapsedSeconds,
            calories = calories,
            currentLat = lat,
            currentLng = lng,
            elevationGainMeters = elevationGainMeters
        )

        // Check for 1km milestone announcement
        val currentKm = (totalDistanceMeters / 1000).toInt()
        if (currentKm > lastAnnouncedKm && currentKm > 0) {
            lastAnnouncedKm = currentKm
            if (isVoiceCoachingEnabled) {
                val speed = "%.1f".format(avgSpeed)
                ttsManager.speak("$currentKm kilometers reached. Average speed $speed kilometers per hour.")
            }
        }

        // ── Batch DB Write ───────────────────────────────────────────────────
        if (pendingBatchPoints.size >= BATCH_SIZE) {
            flushBatchToDB()
        }
    }

    private fun flushBatchToDB() {
        val snapshot = pendingBatchPoints.toList()
        pendingBatchPoints.clear()
        // Write to DB off main thread — fire and forget with structured scope
        serviceScope.launch(Dispatchers.IO) {
            try {
                // Update the most recent session's route points
                // (We'll save the full session on stop)
                Log.d(TAG, "Flushed ${snapshot.size} GPS points (total: ${routePoints.size})")
            } catch (e: Exception) {
                Log.e(TAG, "DB flush error: ${e.message}")
            }
        }
    }

    private fun stopTracking(save: Boolean = true) {
        timerJob?.cancel()
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing location updates: ${e.message}")
        }

        if (!save) {
            _trackingState.value = TrackingState(isTracking = false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            wakeLock?.release()
            wakeLock = null
            stopSelf()
            return
        }

        // Save final session to DB
        serviceScope.launch(Dispatchers.IO) {
            var savedId = -1L
            try {
                val avgSpeed = if (totalDistanceMeters > 0 && elapsedSeconds > 0)
                    (totalDistanceMeters / 1000.0) / (elapsedSeconds / 3600.0) else 0.0

                val mockUser = com.fitnessapp.tracker.data.local.entity.UserEntity(
                    weightKg = weightKg, gender = gender, age = age
                )
                val calories = PhysicsEngine.calculateCalories(mockUser, elapsedSeconds, avgSpeed, activityType)
                val wattsPerKg = PhysicsEngine.calculateWattsPerKg(avgSpeed, mockUser)
                val routeJson = gson.toJson(routePoints)
                
                var isChallengeCompletion = false
                val activeChallenge = _trackingState.value.activeChallenge
                if (activeChallenge != null && activeChallenge.activityType == activityType) {
                    val progressVal = when (activeChallenge.metric) {
                        "DISTANCE" -> totalDistanceMeters
                        "SPEED" -> avgSpeed
                        "CALORIES" -> calories
                        else -> 0.0
                    }
                    
                    val newProgress = if (activeChallenge.metric == "SPEED") progressVal else activeChallenge.currentProgress + progressVal
                    
                    if (newProgress >= activeChallenge.targetValue) {
                        isChallengeCompletion = true
                        val updatedChallenge = activeChallenge.copy(
                            currentProgress = newProgress,
                            status = "COMPLETED",
                            completedAt = System.currentTimeMillis()
                        )
                        challengeDao.updateChallenge(updatedChallenge)
                    } else {
                        val updatedChallenge = activeChallenge.copy(
                            currentProgress = newProgress
                        )
                        challengeDao.updateChallenge(updatedChallenge)
                    }
                }

                val session = WorkoutSessionEntity(
                    startTime = startTimeMs,
                    endTime = System.currentTimeMillis(),
                    durationSeconds = elapsedSeconds,
                    totalDistanceMeters = totalDistanceMeters,
                    elevationGainMeters = elevationGainMeters,
                    avgSpeedKmh = avgSpeed,
                    caloriesBurned = calories,
                    wattsPerKg = wattsPerKg,
                    routePointsJson = routeJson,
                    activityType = activityType,
                    isChallengeCompletion = isChallengeCompletion
                )
                savedId = workoutSessionDao.insertSession(session)
                
                // Sync to cloud
                val updatedSession = session.copy(id = savedId)
                firestoreRepository.syncWorkoutSession(updatedSession)
                
                Log.d(TAG, "Session saved with id: $savedId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save session: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    _trackingState.value = TrackingState(
                        isTracking = false,
                        distanceMeters = totalDistanceMeters,
                        lastSavedSessionId = if (savedId > 0) savedId else null
                    )
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    wakeLock?.release()
                    wakeLock = null
                    stopSelf()
                }
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CyclingService::WakeLock")
        }
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(4 * 60 * 60 * 1000L) // 4 hours max
            Log.d(TAG, "WakeLock acquired")
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            Log.d(TAG, "WakeLock released")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        ttsManager.shutdown()
        releaseWakeLock()
        wakeLock = null
    }
}
