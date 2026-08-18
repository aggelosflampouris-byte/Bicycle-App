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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
        if (_crashState.value is CrashState.Countdown || _crashState.value is CrashState.SosDispatched) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val totalAccel = sqrt((x * x + y * y + z * z).toDouble())
        val gForce = totalAccel / 9.80665

        if (gForce >= IMPACT_G_THRESHOLD) {
            val now = System.currentTimeMillis()
            lastImpactTimeMs = now
            Log.w(TAG, "⚠️ High-G Impact Detected: ${"%.2f".format(gForce)}G! Checking immobility...")
            _crashState.value = CrashState.ImpactDetected

            // Monitor post-impact velocity
            scope.launch {
                delay(3000L) // Wait 3s to evaluate if rider stopped immediately
                if (currentSpeedKmh <= IMMOBILITY_SPEED_THRESHOLD_KMH && _crashState.value is CrashState.ImpactDetected) {
                    triggerCountdown()
                } else if (_crashState.value is CrashState.ImpactDetected) {
                    // False positive (bump on road while maintaining speed)
                    _crashState.value = CrashState.Idle
                    Log.d(TAG, "Rider still moving at $currentSpeedKmh km/h. Impact was a road bump.")
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun triggerCountdown() {
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
        countdownJob?.cancel()
        stopAlarmAudioAndVibration()
        _crashState.value = CrashState.Cancelled
        scope.launch {
            delay(1500L)
            _crashState.value = CrashState.Idle
        }
    }

    private fun dispatchSosBeacon() {
        stopAlarmAudioAndVibration()
        _crashState.value = CrashState.SosDispatched(System.currentTimeMillis(), emergencyContactPhone)
        Log.e(TAG, "🚨 DISPATCHING EMERGENCY SOS BEACON to $emergencyContactPhone")

        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val mapsUrl = "https://maps.google.com/?q=$currentLat,$currentLng"
        val message = "🚨 EMERGENCY: Smart Track detected a bicycle crash at $timeStr. Coordinates: $currentLat, $currentLng. Live Map: $mapsUrl"

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

        // Trigger persistent continuous alarm siren so nearby people can locate the rider
        startContinuousSiren()
    }

    private fun startAlarmAudioAndVibration() {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            triggerVibration(1000L)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start tone generator", e)
        }
    }

    private fun playCountdownBeep(secondsLeft: Int) {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 300)
            triggerVibration(300L)
        } catch (e: Exception) {
            Log.w(TAG, "Tone playback error: ${e.message}")
        }
    }

    private fun startContinuousSiren() {
        scope.launch {
            while (isActive && _crashState.value is CrashState.SosDispatched) {
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 800)
                triggerVibration(600L)
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(durationMs)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration trigger failed: ${e.message}")
        }
    }
}
