package com.fitnessapp.tracker.navigation

import org.junit.Assert.*
import org.junit.Test

class ClimbEngineTest {

    @Test
    fun testDetectClimbsAndCategorization() {
        // Construct a synthetic 1000m climb with 6% average gradient (60m gain)
        val points = mutableListOf<GpxPoint>()
        for (i in 0..10) {
            val dist = i * 100.0
            val ele = 100.0 + (i * 6.0) // 6% slope
            points.add(GpxPoint(lat = 38.0 + (i * 0.001), lng = 23.7 + (i * 0.001), ele = ele, cumulativeDistMeters = dist))
        }

        val climbs = ClimbEngine.detectClimbs(points)
        assertFalse("Should detect at least one climb", climbs.isEmpty())

        val climb = climbs.first()
        assertEquals(6.0, climb.avgGradientPct, 0.5)
        assertEquals(60.0, climb.elevationGainMeters, 1.0)
        assertEquals("Cat 4", climb.category)
    }

    @Test
    fun testLiveClimbEvaluator() {
        val climb = ClimbSegment(
            id = 1,
            startDistanceMeters = 1000.0,
            endDistanceMeters = 2000.0,
            startElevationMeters = 100.0,
            endElevationMeters = 180.0,
            elevationGainMeters = 80.0,
            lengthMeters = 1000.0,
            avgGradientPct = 8.0,
            maxGradientPct = 12.0,
            category = "Cat 3"
        )

        // Approaching climb at 800m (200m before start)
        val approaching = ClimbEngine.evaluateLiveClimb(listOf(climb), 800.0)
        assertNotNull(approaching)
        assertFalse(approaching!!.isAscending)
        assertEquals(200.0, approaching.distanceToStartMeters, 0.1)

        // Halfway through climb at 1500m
        val ascending = ClimbEngine.evaluateLiveClimb(listOf(climb), 1500.0)
        assertNotNull(ascending)
        assertTrue(ascending!!.isAscending)
        assertEquals(500.0, ascending.distanceToSummitMeters, 0.1)
        assertEquals(50, ascending.progressPct)
        assertEquals(40.0, ascending.elevationRemainingMeters, 1.0)
    }
}
