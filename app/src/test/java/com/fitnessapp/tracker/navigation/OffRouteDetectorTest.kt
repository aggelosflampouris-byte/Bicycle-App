package com.fitnessapp.tracker.navigation

import org.junit.Assert.*
import org.junit.Test

class OffRouteDetectorTest {

    @Test
    fun testOnRouteWithinThreshold() {
        val routePoints = listOf(
            GpxPoint(lat = 37.9800, lng = 23.7200),
            GpxPoint(lat = 37.9850, lng = 23.7200),
            GpxPoint(lat = 37.9900, lng = 23.7200)
        )

        // Point is right on the line segment
        val status = OffRouteDetector.checkOffRoute(
            currentLat = 37.9825,
            currentLng = 23.7200,
            routePoints = routePoints
        )

        assertFalse("Should not be off route", status.isOffRoute)
        assertTrue(status.distanceToRouteMeters < 5.0)
    }

    @Test
    fun testOffRouteBeyond50Meters() {
        val routePoints = listOf(
            GpxPoint(lat = 37.9800, lng = 23.7200),
            GpxPoint(lat = 37.9850, lng = 23.7200)
        )

        // Point is ~200 meters east
        val status = OffRouteDetector.checkOffRoute(
            currentLat = 37.9825,
            currentLng = 23.7230,
            routePoints = routePoints
        )

        assertTrue("Should be off route", status.isOffRoute)
        assertTrue("Distance should exceed 50m", status.distanceToRouteMeters >= 50.0)
        assertNotNull(status.alertMessage)
    }
}
