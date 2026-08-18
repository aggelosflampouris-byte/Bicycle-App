package com.fitnessapp.tracker.navigation

import com.fitnessapp.tracker.engine.PhysicsEngine
import kotlin.math.*

data class OffRouteStatus(
    val isOffRoute: Boolean,
    val distanceToRouteMeters: Double,
    val alertMessage: String?
)

object OffRouteDetector {

    const val OFF_ROUTE_THRESHOLD_METERS = 50.0

    /**
     * Calculates the minimum perpendicular distance from the rider's current location
     * to the nearest segment of the GPX route polyline.
     */
    fun checkOffRoute(
        currentLat: Double,
        currentLng: Double,
        routePoints: List<GpxPoint>
    ): OffRouteStatus {
        if (routePoints.size < 2) {
            return OffRouteStatus(isOffRoute = false, distanceToRouteMeters = 0.0, alertMessage = null)
        }

        var minDistanceMeters = Double.MAX_VALUE

        for (i in 0 until routePoints.size - 1) {
            val p1 = routePoints[i]
            val p2 = routePoints[i + 1]

            val dist = distanceToSegment(
                pLat = currentLat, pLng = currentLng,
                aLat = p1.lat, aLng = p1.lng,
                bLat = p2.lat, bLng = p2.lng
            )

            if (dist < minDistanceMeters) {
                minDistanceMeters = dist
            }
        }

        val isOff = minDistanceMeters >= OFF_ROUTE_THRESHOLD_METERS
        val message = if (isOff) {
            "Off route by ${minDistanceMeters.roundToInt()}m. Make a U-turn when safe."
        } else null

        return OffRouteStatus(
            isOffRoute = isOff,
            distanceToRouteMeters = minDistanceMeters,
            alertMessage = message
        )
    }

    /**
     * Calculates perpendicular distance from point P to line segment AB in meters.
     */
    private fun distanceToSegment(
        pLat: Double, pLng: Double,
        aLat: Double, aLng: Double,
        bLat: Double, bLng: Double
    ): Double {
        // Convert to Cartesian approximation using equirectangular projection centered around pLat
        val latRad = Math.toRadians(pLat)
        val cosLat = cos(latRad)

        val px = Math.toRadians(pLng) * cosLat
        val py = Math.toRadians(pLat)
        val ax = Math.toRadians(aLng) * cosLat
        val ay = Math.toRadians(aLat)
        val bx = Math.toRadians(bLng) * cosLat
        val by = Math.toRadians(bLat)

        val abx = bx - ax
        val aby = by - ay
        val apx = px - ax
        val apy = py - ay

        val abLenSq = abx * abx + aby * aby
        if (abLenSq <= 0.0) {
            return PhysicsEngine.haversineDistance(pLat, pLng, aLat, aLng)
        }

        // Projection fraction t
        val t = ((apx * abx + apy * aby) / abLenSq).coerceIn(0.0, 1.0)

        // Closest point on segment
        val closestX = ax + t * abx
        val closestY = ay + t * aby

        val closestLat = Math.toDegrees(closestY)
        val closestLng = Math.toDegrees(closestX / cosLat)

        return PhysicsEngine.haversineDistance(pLat, pLng, closestLat, closestLng)
    }
}
