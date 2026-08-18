package com.fitnessapp.tracker.gamification

import com.fitnessapp.tracker.engine.PhysicsEngine
import com.fitnessapp.tracker.navigation.GpxPoint
import com.fitnessapp.tracker.navigation.GpxRoute
import com.fitnessapp.tracker.service.RoutePoint
import kotlin.math.abs
import kotlin.math.max

data class GhostPoint(
    val lat: Double,
    val lng: Double,
    val elapsedSeconds: Double,
    val cumulativeDistMeters: Double,
    val speedKmh: Double = 0.0
)

data class GhostPacerState(
    val isActive: Boolean = false,
    val ghostLat: Double = 0.0,
    val ghostLng: Double = 0.0,
    val timeDeltaSeconds: Double = 0.0, // > 0 = Ahead of Ghost (Faster), < 0 = Behind Ghost
    val distanceDeltaMeters: Double = 0.0, // > 0 = Ahead, < 0 = Behind
    val isAhead: Boolean = true,
    val ghostSpeedKmh: Double = 0.0,
    val totalGhostDurationSec: Double = 0.0
)

class GhostPacerEngine {

    private var ghostPoints: List<GhostPoint> = emptyList()

    val hasGhost: Boolean
        get() = ghostPoints.isNotEmpty()

    /**
     * Initializes Ghost timeline from a historical workout's RoutePoint list.
     */
    fun loadFromSessionRoute(points: List<RoutePoint>) {
        if (points.size < 2) {
            ghostPoints = emptyList()
            return
        }

        var cumDist = 0.0
        val timeline = mutableListOf<GhostPoint>()

        for (i in points.indices) {
            val pt = points[i]
            if (i > 0) {
                val prev = points[i - 1]
                val d = PhysicsEngine.haversineDistance(prev.lat, prev.lng, pt.lat, pt.lng)
                cumDist += d
            }
            val speed = PhysicsEngine.metersPerSecondToKmh(pt.speedMps)
            timeline.add(
                GhostPoint(
                    lat = pt.lat,
                    lng = pt.lng,
                    elapsedSeconds = pt.timestamp.toDouble(),
                    cumulativeDistMeters = cumDist,
                    speedKmh = speed
                )
            )
        }
        ghostPoints = timeline
    }

    /**
     * Initializes Ghost timeline from an imported GPX route using average pacing.
     */
    fun loadFromGpx(route: GpxRoute, targetAvgSpeedKmh: Double = 25.0) {
        if (route.points.size < 2) {
            ghostPoints = emptyList()
            return
        }

        val speedMps = targetAvgSpeedKmh / 3.6
        val timeline = mutableListOf<GhostPoint>()

        for (pt in route.points) {
            val elapsed = if (speedMps > 0) pt.cumulativeDistMeters / speedMps else 0.0
            timeline.add(
                GhostPoint(
                    lat = pt.lat,
                    lng = pt.lng,
                    elapsedSeconds = elapsed,
                    cumulativeDistMeters = pt.cumulativeDistMeters,
                    speedKmh = targetAvgSpeedKmh
                )
            )
        }
        ghostPoints = timeline
    }

    fun clear() {
        ghostPoints = emptyList()
    }

    /**
     * Evaluates live Ghost pacing against rider's current distance and elapsed time.
     */
    fun evaluate(currentDistMeters: Double, currentElapsedSec: Long): GhostPacerState {
        if (ghostPoints.size < 2) {
            return GhostPacerState(isActive = false)
        }

        val ghostDuration = ghostPoints.last().elapsedSeconds
        val ghostTotalDist = ghostPoints.last().cumulativeDistMeters

        // 1. Find Ghost position at current elapsed time
        val (ghostLat, ghostLng, ghostSpeed) = interpolatePositionAtTime(currentElapsedSec.toDouble())

        // 2. Find Ghost time at rider's current distance
        val ghostTimeAtCurrentDist = interpolateTimeAtDistance(currentDistMeters)

        // 3. Time Delta = Ghost Time - Current Time (e.g. Ghost took 120s, you took 100s -> +20s ahead)
        val timeDelta = ghostTimeAtCurrentDist - currentElapsedSec.toDouble()
        val isAhead = timeDelta >= 0.0

        // 4. Distance Delta = Rider Dist - Ghost Dist at Current Time
        val ghostDistAtCurrentTime = interpolateDistanceAtTime(currentElapsedSec.toDouble())
        val distDelta = currentDistMeters - ghostDistAtCurrentTime

        return GhostPacerState(
            isActive = true,
            ghostLat = ghostLat,
            ghostLng = ghostLng,
            timeDeltaSeconds = timeDelta,
            distanceDeltaMeters = distDelta,
            isAhead = isAhead,
            ghostSpeedKmh = ghostSpeed,
            totalGhostDurationSec = ghostDuration
        )
    }

    private fun interpolatePositionAtTime(targetSec: Double): Triple<Double, Double, Double> {
        if (targetSec <= ghostPoints.first().elapsedSeconds) {
            val p = ghostPoints.first()
            return Triple(p.lat, p.lng, p.speedKmh)
        }
        if (targetSec >= ghostPoints.last().elapsedSeconds) {
            val p = ghostPoints.last()
            return Triple(p.lat, p.lng, p.speedKmh)
        }

        for (i in 0 until ghostPoints.size - 1) {
            val p1 = ghostPoints[i]
            val p2 = ghostPoints[i + 1]

            if (targetSec >= p1.elapsedSeconds && targetSec <= p2.elapsedSeconds) {
                val span = p2.elapsedSeconds - p1.elapsedSeconds
                val fraction = if (span > 0) (targetSec - p1.elapsedSeconds) / span else 0.0
                val lat = p1.lat + fraction * (p2.lat - p1.lat)
                val lng = p1.lng + fraction * (p2.lng - p1.lng)
                val speed = p1.speedKmh + fraction * (p2.speedKmh - p1.speedKmh)
                return Triple(lat, lng, speed)
            }
        }
        val last = ghostPoints.last()
        return Triple(last.lat, last.lng, last.speedKmh)
    }

    private fun interpolateTimeAtDistance(targetDistM: Double): Double {
        if (targetDistM <= ghostPoints.first().cumulativeDistMeters) {
            return ghostPoints.first().elapsedSeconds
        }
        if (targetDistM >= ghostPoints.last().cumulativeDistMeters) {
            return ghostPoints.last().elapsedSeconds
        }

        for (i in 0 until ghostPoints.size - 1) {
            val p1 = ghostPoints[i]
            val p2 = ghostPoints[i + 1]

            if (targetDistM >= p1.cumulativeDistMeters && targetDistM <= p2.cumulativeDistMeters) {
                val span = p2.cumulativeDistMeters - p1.cumulativeDistMeters
                val fraction = if (span > 0) (targetDistM - p1.cumulativeDistMeters) / span else 0.0
                return p1.elapsedSeconds + fraction * (p2.elapsedSeconds - p1.elapsedSeconds)
            }
        }
        return ghostPoints.last().elapsedSeconds
    }

    private fun interpolateDistanceAtTime(targetSec: Double): Double {
        if (targetSec <= ghostPoints.first().elapsedSeconds) {
            return ghostPoints.first().cumulativeDistMeters
        }
        if (targetSec >= ghostPoints.last().elapsedSeconds) {
            return ghostPoints.last().cumulativeDistMeters
        }

        for (i in 0 until ghostPoints.size - 1) {
            val p1 = ghostPoints[i]
            val p2 = ghostPoints[i + 1]

            if (targetSec >= p1.elapsedSeconds && targetSec <= p2.elapsedSeconds) {
                val span = p2.elapsedSeconds - p1.elapsedSeconds
                val fraction = if (span > 0) (targetSec - p1.elapsedSeconds) / span else 0.0
                return p1.cumulativeDistMeters + fraction * (p2.cumulativeDistMeters - p1.cumulativeDistMeters)
            }
        }
        return ghostPoints.last().cumulativeDistMeters
    }
}
