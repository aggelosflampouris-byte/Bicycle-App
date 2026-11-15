package com.fitnessapp.tracker.safety

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telephony.SmsManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.fitnessapp.tracker.util.DateFormats
import kotlin.math.sqrt

sealed class CrashState {
    object Idle : CrashState()
    object ImpactDetected : CrashState()
    data class Countdown(val secondsRemaining: Int) : CrashState()
    data class SosDispatched(val timestamp: Long, val contactPhone: String) : CrashState()
    object Cancelled : CrashState()
}

class CrashDetectionEngine(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : SensorEventListener {

    companion object {
        private const val TAG = "CrashDetectionEngine"
        const val IMPACT_G_THRESHOLD = 4.5
        const val IMMOBILITY_SPEED_THRESHOLD_KMH = 2.5
        const val COUNTDOWN_TOTAL_SECONDS = 30
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _crashState = MutableStateFlow<CrashState>(CrashState.Idle)
    val crashState: StateFlow<CrashState> = _crashState.asStateFlow()

    private var countdownJob: Job? = null
    private var impactEvaluationJob: Job? = null
    private var sirenJob: Job? = null
    private var resetJob: Job? = null
    private var toneGenerator: ToneGenerator? = null
    private var isMonitoring = false

    private var lastImpactTimeMs: Long = 0L
    private var currentSpeedKmh: Double = 0.0
    private var currentLat: Double = 0.0
    private var currentLng: Double = 0.0
    private var emergencyContactName: String = ""
    private var emergencyContactPhone: String = ""

    fun updateTelemetry(speedKmh: Double, lat: Double, lng: Double) {
        currentSpeedKmh = speedKmh
        currentLat = lat
        currentLng = lng

        // Check if moving again after impact to auto-cancel if rider continues riding
        if (_crashState.value is CrashState.Countdown && speedKmh > 8.0) {
            Log.d(TAG, "Rider resumed high speed ($speedKmh km/h), auto-cancelling crash alarm.")
            cancelSos()
        }
    }

    fun updateEmergencyContact(name: String, phone: String) {
        emergencyContactName = name
        emergencyContactPhone = phone
    }

    fun startMonitoring() {
        if (isMonitoring) return
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            isMonitoring = true
            Log.d(TAG, "Crash detection sensor monitoring started.")
        }
    }

    fun stopMonitoring() {
        if (!isMonitoring) return
        sensorManager?.unregisterListener(this)
        isMonitoring = false
        cancelSos()
        Log.d(TAG, "Crash detection sensor monitoring stopped.")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        // Ignore spikes once an impact evaluation, countdown, or SOS dispatch is already active
        if (_crashState.value !is CrashState.Idle) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val totalAccel = sqrt((x * x + y * y + z * z).toDouble())
        val gForce = totalAccel / 9.80665

        if (gForce >= IMPACT_G_THRESHOLD) {
            val now = System.currentTimeMillis()
            lastImpactTimeMs = now
            Log.w(TAG, "⚠️ High-G Impact Detected: ${"%.2f".format(gForce)}G! Checking immobility...")
            resetJob?.cancel()
            _crashState.value = CrashState.ImpactDetected

            // Monitor post-impact velocity with a single tracked evaluation job
            impactEvaluationJob?.cancel()
            impactEvaluationJob = scope.launch {
                delay(3000L) // Wait 3s to evaluate if user stopped immediately
                if (currentSpeedKmh <= IMMOBILITY_SPEED_THRESHOLD_KMH && _crashState.value is CrashState.ImpactDetected) {
                    triggerCountdown()
                } else if (_crashState.value is CrashState.ImpactDetected) {
                    // False positive (bump while maintaining speed)
                    _crashState.value = CrashState.Idle
                    Log.d(TAG, "User still moving at $currentSpeedKmh km/h. Impact was a false positive.")
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun triggerCountdown() {
        impactEvaluationJob?.cancel()
        resetJob?.cancel()
        sirenJob?.cancel()
        countdownJob?.cancel()
        _crashState.value = CrashState.Countdown(COUNTDOWN_TOTAL_SECONDS)
        startAlarmAudioAndVibration()

        countdownJob = scope.launch {
            for (sec in COUNTDOWN_TOTAL_SECONDS downTo 1) {
                if (!isActive || _crashState.value !is CrashState.Countdown) break
                _crashState.value = CrashState.Countdown(sec)
                playCountdownBeep(sec)
                delay(1000L)
            }

            if (isActive && _crashState.value is CrashState.Countdown) {
                dispatchSosBeacon()
            }
        }
    }

    fun cancelSos() {
        impactEvaluationJob?.cancel()
        countdownJob?.cancel()
        sirenJob?.cancel()
        resetJob?.cancel()
        stopAlarmAudioAndVibration()
        _crashState.value = CrashState.Cancelled
        resetJob = scope.launch {
            delay(1500L)
            if (_crashState.value is CrashState.Cancelled) {
                _crashState.value = CrashState.Idle
            }
        }
    }

    private fun dispatchSosBeacon() {
        _crashState.value = CrashState.SosDispatched(System.currentTimeMillis(), emergencyContactPhone)
        Log.e(TAG, "🚨 DISPATCHING EMERGENCY SOS BEACON to $emergencyContactPhone")

        val timeStr = DateFormats.formatTimeOfDay()
        val mapsUrl = "https://maps.google.com/?q=$currentLat,$currentLng"
        val message = "🚨 EMERGENCY: Smart Track detected a possible crash or fall at $timeStr. Coordinates: $currentLat, $currentLng. Live Map: $mapsUrl"

        if (emergencyContactPhone.isNotBlank()) {
            try {
                val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                smsManager.sendTextMessage(emergencyContactPhone, null, message, null, null)
                Log.d(TAG, "SOS SMS sent successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send SOS SMS: ${e.message}", e)
            }
        } else {
            Log.w(TAG, "No emergency contact phone number configured!")
        }

        // Trigger persistent continuous alarm siren so nearby people can locate the user
        startContinuousSiren()
    }

    private fun ensureToneGenerator(): ToneGenerator? {
        if (toneGenerator == null) {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        }
        return toneGenerator
    }

    private fun startAlarmAudioAndVibration() {
        try {
            ensureToneGenerator()
            triggerVibration(1000L)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start tone generator", e)
        }
    }

    private fun playCountdownBeep(secondsLeft: Int) {
        try {
            ensureToneGenerator()?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 300)
            triggerVibration(300L)
        } catch (e: Exception) {
            Log.w(TAG, "Tone playback error: ${e.message}")
        }
    }

    private fun startContinuousSiren() {
        sirenJob?.cancel()
        sirenJob = scope.launch {
            while (isActive && _crashState.value is CrashState.SosDispatched) {
                try {
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 800)
                    triggerVibration(600L)
                } catch (e: Exception) {
                    Log.w(TAG, "Siren playback error: ${e.message}")
                }
                delay(1200L)
            }
        }
    }

    private fun stopAlarmAudioAndVibration() {
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping tone generator", e)
        }
    }

    private fun triggerVibration(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val effect = VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                vibratorManager?.vibrate(CombinedVibration.createParallel(effect))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration trigger failed: ${e.message}")
        }
    }
}
