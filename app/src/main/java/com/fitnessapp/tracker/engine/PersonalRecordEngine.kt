package com.fitnessapp.tracker.engine

import android.util.Log
import com.fitnessapp.tracker.data.local.dao.PersonalRecordDao
import com.fitnessapp.tracker.data.local.entity.PersonalRecordEntity
import com.fitnessapp.tracker.data.local.entity.PersonalRecordType
import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import com.fitnessapp.tracker.service.RoutePoint
import javax.inject.Inject
import javax.inject.Singleton

data class PersonalRecordAchievement(
    val recordType: PersonalRecordType,
    val activityType: String,
    val newValue: Double,
    val previousValue: Double?,
    val formattedValue: String,
    val isFirstTime: Boolean
)

@Singleton
class PersonalRecordEngine @Inject constructor(
    private val personalRecordDao: PersonalRecordDao
) {
    companion object {
        private const val TAG = "PersonalRecordEngine"
    }

    /**
     * Evaluates a completed workout session and its GPS route points against all-time
     * personal records for that activity type. Saves newly set PRs and returns achievements.
     */
    suspend fun evaluateAndSaveRecords(
        session: WorkoutSessionEntity,
        routePoints: List<RoutePoint>
    ): List<PersonalRecordAchievement> {
        val activity = session.activityType
        val existingRecords = personalRecordDao.getRecordsForActivity(activity).associateBy { it.recordType }
        val achievements = mutableListOf<PersonalRecordAchievement>()
        val recordsToUpsert = mutableListOf<PersonalRecordEntity>()

        // ── 1. Whole-Session Metric Candidates ──────────────────────────────
        val sessionCandidates = mutableMapOf<PersonalRecordType, Double>()

        if (session.totalDistanceMeters > 500.0) {
            sessionCandidates[PersonalRecordType.LONGEST_DISTANCE] = session.totalDistanceMeters
        }
        if (session.durationSeconds > 60) {
            sessionCandidates[PersonalRecordType.LONGEST_DURATION] = session.durationSeconds.toDouble()
        }
        if (session.elevationGainMeters > 10.0) {
            sessionCandidates[PersonalRecordType.MAX_ELEVATION_GAIN] = session.elevationGainMeters
        }
        if (session.totalDistanceMeters >= 2000.0 && session.avgSpeedKmh > 0.0) {
            sessionCandidates[PersonalRecordType.MAX_AVG_SPEED] = session.avgSpeedKmh
        }
        if (session.caloriesBurned > 50.0) {
            sessionCandidates[PersonalRecordType.MAX_CALORIES] = session.caloriesBurned
        }

        // ── 2. Rolling Split Analysis (1km, 5km, 10km, 20km, 50km) ──────────
        val splitTargets = mapOf(
            PersonalRecordType.FASTEST_1KM to 1000.0,
            PersonalRecordType.FASTEST_5KM to 5000.0,
            PersonalRecordType.FASTEST_10KM to 10000.0,
            PersonalRecordType.FASTEST_20KM to 20000.0,
            PersonalRecordType.FASTEST_50KM to 50000.0
        )

        if (routePoints.size >= 2 && session.totalDistanceMeters >= 1000.0) {
            val cumDistances = computeCumulativeDistances(routePoints)
            val totalTrackDist = cumDistances.lastOrNull() ?: 0.0

            for ((recordType, targetMeters) in splitTargets) {
                if (totalTrackDist >= targetMeters) {
                    val bestSplitSeconds = findBestSplitTimeSeconds(routePoints, cumDistances, targetMeters)
                    if (bestSplitSeconds != null && bestSplitSeconds > 0.0) {
                        sessionCandidates[recordType] = bestSplitSeconds
                    }
                }
            }
        }

        // ── 3. Compare Candidates with Existing Records ─────────────────────
        for ((type, candidateValue) in sessionCandidates) {
            val existing = existingRecords[type]
            val isRecordBroken: Boolean

            if (type.isTimeBased && type != PersonalRecordType.LONGEST_DURATION) {
                // For fastest split records: smaller time is better!
                isRecordBroken = existing == null || candidateValue < existing.value
            } else {
                // For longest distance, duration, elevation, speed, calories: higher is better!
                isRecordBroken = existing == null || candidateValue > existing.value
            }

            if (isRecordBroken) {
                val newRecord = PersonalRecordEntity(
                    recordType = type,
                    activityType = activity,
                    value = candidateValue,
                    sessionId = session.id,
                    achievedAt = session.endTime.takeIf { it > 0 } ?: System.currentTimeMillis()
                )
                recordsToUpsert.add(newRecord)

                val formatted = formatRecordValue(type, candidateValue)
                achievements.add(
                    PersonalRecordAchievement(
                        recordType = type,
                        activityType = activity,
                        newValue = candidateValue,
                        previousValue = existing?.value,
                        formattedValue = formatted,
                        isFirstTime = (existing == null)
                    )
                )
                Log.d(TAG, "🏆 New Personal Record: ${type.displayName} -> $formatted ($activity)")
            }
        }

        // Persist new records to Room
        if (recordsToUpsert.isNotEmpty()) {
            personalRecordDao.insertOrUpdateRecords(recordsToUpsert)
        }

        return achievements
    }

    private fun computeCumulativeDistances(points: List<RoutePoint>): DoubleArray {
        val cum = DoubleArray(points.size)
        cum[0] = 0.0
        for (i in 1 until points.size) {
            val d = PhysicsEngine.haversineDistance(
                points[i - 1].lat, points[i - 1].lng,
                points[i].lat, points[i].lng
            )
            cum[i] = cum[i - 1] + d
        }
        return cum
    }

    private fun findBestSplitTimeSeconds(
        points: List<RoutePoint>,
        cumDist: DoubleArray,
        targetDistMeters: Double
    ): Double? {
        var minDuration: Double? = null
        var right = 0

        for (left in points.indices) {
            while (right < points.size && (cumDist[right] - cumDist[left]) < targetDistMeters) {
                right++
            }
            if (right >= points.size) break

            val spanDist = cumDist[right] - cumDist[left]
            val spanTime = (points[right].timestamp - points[left].timestamp) / 1000.0

            if (spanDist > 0 && spanTime > 0) {
                // Exact linear interpolation for the target distance
                val exactTime = spanTime * (targetDistMeters / spanDist)
                if (minDuration == null || exactTime < minDuration) {
                    minDuration = exactTime
                }
            }
        }
        return minDuration
    }

    fun formatRecordValue(type: PersonalRecordType, value: Double): String {
        return when (type) {
            PersonalRecordType.FASTEST_1KM,
            PersonalRecordType.FASTEST_5KM,
            PersonalRecordType.FASTEST_10KM,
            PersonalRecordType.FASTEST_20KM,
            PersonalRecordType.FASTEST_50KM -> formatDuration(value.toLong())

            PersonalRecordType.LONGEST_DURATION -> formatDuration(value.toLong())
            PersonalRecordType.LONGEST_DISTANCE -> "%.2f km".format(value / 1000.0)
            PersonalRecordType.MAX_ELEVATION_GAIN -> "+%.0f m".format(value)
            PersonalRecordType.MAX_AVG_SPEED -> "%.1f km/h".format(value)
            PersonalRecordType.MAX_CALORIES -> "%.0f kcal".format(value)
        }
    }

    private fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%dh %02dm %02ds".format(hours, minutes, seconds)
        } else {
            "%dm %02ds".format(minutes, seconds)
        }
    }
}
