package com.fitnessapp.tracker.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

data class LiveSensorTelemetry(
    val cadenceSpm: Double = 0.0,
    val avgCadenceSpm: Double? = null,
    val gaitStabilityScore: Double? = null,
    val barometricElevationDeltaM: Double? = null,
    val ambientLux: Float? = null,
    val isLowLight: Boolean = false,
    val usingHardwareStepSensor: Boolean = false
)

/**
 * Collects phone hardware sensors (step detector/accelerometer cadence & stability,
 * barometer altitude delta, and ambient light) during outdoor workouts without blocking
 * when optional sensors or permissions are unavailable.
 */
class WorkoutSensorCollector(
    private val context: Context
) : SensorEventListener {

    companion object {
        private const val TAG = "WorkoutSensorCollector"
        private const val CADENCE_WINDOW_MS = 18_000L
        private const val MIN_STEP_INTERVAL_MS = 260L
        private const val LOW_LIGHT_THRESHOLD_LUX = 35f
        private const val ACCEL_PEAK_THRESHOLD_WALK = 2.0f
        private const val ACCEL_PEAK_THRESHOLD_JOG = 3.2f
        private const val ACCEL_PEAK_THRESHOLD_CYCLE = 2.6f

        internal fun computeCadenceSpm(timestampsMs: Collection<Long>): Double {
            if (timestampsMs.size < 3) return 0.0
            val first = timestampsMs.first()
            val last = timestampsMs.last()
            val spanSec = ((last - first).coerceAtLeast(1000L)) / 1000.0
            return (((timestampsMs.size - 1) / spanSec) * 60.0).coerceIn(0.0, 240.0)
        }

        internal fun computeIntervalStabilityScore(timestampsMs: Collection<Long>): Double? {
            if (timestampsMs.size < 5) return null
            val intervals = ArrayList<Double>(timestampsMs.size - 1)
            val iter = timestampsMs.iterator()
            var prev = iter.next()
            while (iter.hasNext()) {
                val curr = iter.next()
                val dt = (curr - prev).toDouble()
                if (dt in 220.0..2500.0) intervals.add(dt)
                prev = curr
            }
            if (intervals.size < 4) return null

            val mean = intervals.average()
            if (mean <= 0.0) return null
            val variance = intervals.sumOf { (it - mean) * (it - mean) } / intervals.size
            val cv = sqrt(variance) / mean
            // Lower inter-stride/bounce variability maps cleanly to higher stability (0..100).
            return ((1.0 - (cv / 0.45).coerceIn(0.0, 1.0)) * 100.0).coerceIn(10.0, 100.0)
        }
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val stepDetector = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val linearAccel = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val rawAccel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val barometer = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

    private val _telemetry = MutableStateFlow(LiveSensorTelemetry())
    val telemetry: StateFlow<LiveSensorTelemetry> = _telemetry.asStateFlow()

    private var isMonitoring = false
    private var currentActivityType = "CYCLING"
    private var useHardwareSteps = false

    private val eventTimestampsMs = ArrayDeque<Long>()
    private var sessionStartMs = 0L
    private var totalStepsOrPeaks = 0
    private var lastAccelPeakMs = 0L
    private var smoothedAccelMag = 0f
    private var baselinePressureHpa: Float? = null
    private var smoothedLux: Float? = null

    fun startMonitoring(activityType: String) {
        if (isMonitoring) return
        currentActivityType = activityType
        resetState()

        val hasStepPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

        useHardwareSteps = activityType != "CYCLING" && stepDetector != null && hasStepPermission
        if (useHardwareSteps && stepDetector != null) {
            sensorManager?.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_UI)
        }

        // Linear acceleration stays useful for stability scoring & accelerometer cadence fallback.
        val motionSensor = linearAccel ?: rawAccel
        motionSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        barometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        lightSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        _telemetry.value = _telemetry.value.copy(usingHardwareStepSensor = useHardwareSteps)
        isMonitoring = true
        Log.d(TAG, "Started sensor collector for $activityType (hwSteps=$useHardwareSteps)")
    }

    fun stopMonitoring() {
        if (!isMonitoring) return
        isMonitoring = false
        sensorManager?.unregisterListener(this)
    }

    private fun resetState() {
        sessionStartMs = System.currentTimeMillis()
        totalStepsOrPeaks = 0
        lastAccelPeakMs = 0L
        smoothedAccelMag = 0f
        baselinePressureHpa = null
        smoothedLux = null
        eventTimestampsMs.clear()
        _telemetry.value = LiveSensorTelemetry()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isMonitoring) return
        val nowMs = System.currentTimeMillis()

        when (event.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> {
                if (useHardwareSteps) {
                    recordCadenceEvent(nowMs)
                }
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val mag = sqrt(
                    event.values[0] * event.values[0] +
                        event.values[1] * event.values[1] +
                        event.values[2] * event.values[2]
                )
                handleMotionSample(mag, nowMs)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val totalMag = sqrt(
                    event.values[0] * event.values[0] +
                        event.values[1] * event.values[1] +
                        event.values[2] * event.values[2]
                )
                val dynamicMag = kotlin.math.abs(totalMag - SensorManager.GRAVITY_EARTH)
                handleMotionSample(dynamicMag, nowMs)
            }
            Sensor.TYPE_PRESSURE -> {
                val pressure = event.values[0]
                if (pressure > 300f) {
                    val base = baselinePressureHpa ?: pressure.also { baselinePressureHpa = it }
                    val baseAlt = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, base)
                    val curAlt = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure)
                    _telemetry.value = _telemetry.value.copy(
                        barometricElevationDeltaM = (curAlt - baseAlt).toDouble()
                    )
                }
            }
            Sensor.TYPE_LIGHT -> {
                val lux = event.values[0].coerceAtLeast(0f)
                val nextLux = smoothedLux?.let { it * 0.7f + lux * 0.3f } ?: lux
                smoothedLux = nextLux
                _telemetry.value = _telemetry.value.copy(
                    ambientLux = nextLux,
                    isLowLight = nextLux < LOW_LIGHT_THRESHOLD_LUX
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    private fun handleMotionSample(dynamicMag: Float, nowMs: Long) {
        smoothedAccelMag = smoothedAccelMag * 0.65f + dynamicMag * 0.35f
        if (useHardwareSteps) return

        val threshold = when (currentActivityType) {
            "WALKING" -> ACCEL_PEAK_THRESHOLD_WALK
            "JOGGING" -> ACCEL_PEAK_THRESHOLD_JOG
            else -> ACCEL_PEAK_THRESHOLD_CYCLE
        }

        if (smoothedAccelMag >= threshold && (nowMs - lastAccelPeakMs) >= MIN_STEP_INTERVAL_MS) {
            lastAccelPeakMs = nowMs
            recordCadenceEvent(nowMs)
        }
    }

    private fun recordCadenceEvent(nowMs: Long) {
        totalStepsOrPeaks++
        eventTimestampsMs.addLast(nowMs)
        while (eventTimestampsMs.isNotEmpty() && nowMs - eventTimestampsMs.first() > CADENCE_WINDOW_MS) {
            eventTimestampsMs.removeFirst()
        }

        val currentCadence = computeCadenceSpm(eventTimestampsMs)
        val stability = computeIntervalStabilityScore(eventTimestampsMs)
        val elapsedMinutes = ((nowMs - sessionStartMs).coerceAtLeast(15_000L)) / 60_000.0
        val avgCadence = (totalStepsOrPeaks / elapsedMinutes).coerceIn(0.0, 240.0)

        _telemetry.value = _telemetry.value.copy(
            cadenceSpm = currentCadence,
            avgCadenceSpm = avgCadence,
            gaitStabilityScore = stability ?: _telemetry.value.gaitStabilityScore
        )
    }
}

