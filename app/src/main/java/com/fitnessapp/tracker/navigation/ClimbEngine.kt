package com.fitnessapp.tracker.navigation

import kotlin.math.max

data class ClimbSegment(
    val id: Int,
    val startDistanceMeters: Double,
    val endDistanceMeters: Double,
    val startElevationMeters: Double,
    val endElevationMeters: Double,
    val elevationGainMeters: Double,
    val lengthMeters: Double,
    val avgGradientPct: Double,
    val maxGradientPct: Double,
    val category: String // Cat 4, Cat 3, Cat 2, Cat 1, HC
)

data class LiveClimbInfo(
    val climb: ClimbSegment,
    val isAscending: Boolean,
    val distanceToStartMeters: Double,
    val distanceToSummitMeters: Double,
    val elevationRemainingMeters: Double,
    val progressPct: Int
)

object ClimbEngine {

    private const val MIN_CLIMB_LENGTH_METERS = 250.0
    private const val MIN_AVG_GRADIENT_PCT = 3.0

    /**
     * Scans a list of GPX points and detects distinct categorized hill climbs.
     */
    fun detectClimbs(points: List<GpxPoint>): List<ClimbSegment> {
        if (points.size < 5) return emptyList()

        val climbs = mutableListOf<ClimbSegment>()
        var climbId = 1

        var inClimb = false
        var startIndex = 0

        for (i in 1 until points.size) {
            val distDelta = points[i].cumulativeDistMeters - points[startIndex].cumulativeDistMeters
            val eleDelta = points[i].ele - points[startIndex].ele

            if (distDelta >= 100.0) {
                val currentGrade = if (distDelta > 0) (eleDelta / distDelta) * 100.0 else 0.0

                if (!inClimb && currentGrade >= MIN_AVG_GRADIENT_PCT) {
                    inClimb = true
                    startIndex = i - 1
                } else if (inClimb) {
                    val stepDist = points[i].cumulativeDistMeters - points[i - 1].cumulativeDistMeters
                    val stepEle = points[i].ele - points[i - 1].ele
                    val stepGrade = if (stepDist > 0) (stepEle / stepDist) * 100.0 else 0.0

                    // End climb if descending or flat for over 150m
                    if (stepGrade < 0.5) {
                        val climbDist = points[i - 1].cumulativeDistMeters - points[startIndex].cumulativeDistMeters
                        val climbGain = points[i - 1].ele - points[startIndex].ele
                        val avgGrade = if (climbDist > 0) (climbGain / climbDist) * 100.0 else 0.0

                        if (climbDist >= MIN_CLIMB_LENGTH_METERS && avgGrade >= MIN_AVG_GRADIENT_PCT && climbGain >= 15.0) {
                            climbs.add(
                                createClimbSegment(
                                    id = climbId++,
                                    points = points,
                                    startIdx = startIndex,
                                    endIdx = i - 1,
                                    length = climbDist,
                                    gain = climbGain,
                                    avgGrade = avgGrade
                                )
                            )
                        }
                        inClimb = false
                        startIndex = i
                    }
                }
            }
        }

        // Finalize last climb if ended at the summit
        if (inClimb) {
            val lastIdx = points.size - 1
            val climbDist = points[lastIdx].cumulativeDistMeters - points[startIndex].cumulativeDistMeters
            val climbGain = points[lastIdx].ele - points[startIndex].ele
            val avgGrade = if (climbDist > 0) (climbGain / climbDist) * 100.0 else 0.0

            if (climbDist >= MIN_CLIMB_LENGTH_METERS && avgGrade >= MIN_AVG_GRADIENT_PCT && climbGain >= 15.0) {
                climbs.add(
                    createClimbSegment(
                        id = climbId,
                        points = points,
                        startIdx = startIndex,
                        endIdx = lastIdx,
                        length = climbDist,
                        gain = climbGain,
                        avgGrade = avgGrade
                    )
                )
            }
        }

        return climbs
    }

    private fun createClimbSegment(
        id: Int,
        points: List<GpxPoint>,
        startIdx: Int,
        endIdx: Int,
        length: Double,
        gain: Double,
        avgGrade: Double
    ): ClimbSegment {
        var maxGrade = avgGrade
        for (j in startIdx until endIdx) {
            val d = points[j + 1].cumulativeDistMeters - points[j].cumulativeDistMeters
            val e = points[j + 1].ele - points[j].ele
            if (d > 10.0) {
                val g = (e / d) * 100.0
                if (g > maxGrade) maxGrade = g
            }
        }

        val category = calculateCategory(length, avgGrade, gain)

        return ClimbSegment(
            id = id,
            startDistanceMeters = points[startIdx].cumulativeDistMeters,
            endDistanceMeters = points[endIdx].cumulativeDistMeters,
            startElevationMeters = points[startIdx].ele,
            endElevationMeters = points[endIdx].ele,
            elevationGainMeters = gain,
            lengthMeters = length,
            avgGradientPct = avgGrade,
            maxGradientPct = maxGrade.coerceAtMost(35.0),
            category = category
        )
    }

    /**
     * Determines standard cycling climb category (Cat 4, Cat 3, Cat 2, Cat 1, HC).
     */
    fun calculateCategory(lengthMeters: Double, avgGradientPct: Double, elevationGainMeters: Double): String {
        // Standard score = Length (m) * Gradient (%)
        val score = lengthMeters * (avgGradientPct / 100.0) * elevationGainMeters

        return when {
            score >= 64000.0 || (elevationGainMeters >= 800.0 && avgGradientPct >= 7.0) -> "HC"
            score >= 32000.0 || elevationGainMeters >= 500.0 -> "Cat 1"
            score >= 16000.0 || elevationGainMeters >= 300.0 -> "Cat 2"
            score >= 8000.0 || elevationGainMeters >= 150.0  -> "Cat 3"
            else -> "Cat 4"
        }
    }

    /**
     * Real-time ClimbPro evaluator: finds active or upcoming climb for the rider.
     */
    fun evaluateLiveClimb(climbs: List<ClimbSegment>, currentDistanceMeters: Double): LiveClimbInfo? {
        if (climbs.isEmpty()) return null

        for (climb in climbs) {
            // Case 1: Rider is actively ascending this climb
            if (currentDistanceMeters >= climb.startDistanceMeters && currentDistanceMeters <= climb.endDistanceMeters) {
                val distIntoClimb = currentDistanceMeters - climb.startDistanceMeters
                val distToSummit = max(0.0, climb.endDistanceMeters - currentDistanceMeters)
                val progressFraction = if (climb.lengthMeters > 0) (distIntoClimb / climb.lengthMeters).coerceIn(0.0, 1.0) else 1.0
                val eleRemaining = max(0.0, climb.elevationGainMeters * (1.0 - progressFraction))

                return LiveClimbInfo(
                    climb = climb,
                    isAscending = true,
                    distanceToStartMeters = 0.0,
                    distanceToSummitMeters = distToSummit,
                    elevationRemainingMeters = eleRemaining,
                    progressPct = (progressFraction * 100.0).toInt()
                )
            }

            // Case 2: Rider is approaching a climb within 500 meters
            val distToStart = climb.startDistanceMeters - currentDistanceMeters
            if (distToStart in 0.0..500.0) {
                return LiveClimbInfo(
                    climb = climb,
                    isAscending = false,
                    distanceToStartMeters = distToStart,
                    distanceToSummitMeters = climb.lengthMeters,
                    elevationRemainingMeters = climb.elevationGainMeters,
                    progressPct = 0
                )
            }
        }

        return null
    }
}
