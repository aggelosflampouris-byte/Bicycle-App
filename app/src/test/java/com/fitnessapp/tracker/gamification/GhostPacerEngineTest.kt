package com.fitnessapp.tracker.gamification

import com.fitnessapp.tracker.service.RoutePoint
import org.junit.Assert.*
import org.junit.Test

class GhostPacerEngineTest {

    @Test
    fun testGhostPacerAheadWhenRiderIsFaster() {
        val engine = GhostPacerEngine()

        // 1000m reference route where ghost took 200 seconds (5 m/s = 18 km/h)
        val ghostRoute = listOf(
            RoutePoint(lat = 37.9800, lng = 23.7200, alt = 10.0, timestamp = 0L, speedMps = 5.0),
            RoutePoint(lat = 37.9845, lng = 23.7200, alt = 10.0, timestamp = 100L, speedMps = 5.0),
            RoutePoint(lat = 37.9890, lng = 23.7200, alt = 10.0, timestamp = 200L, speedMps = 5.0)
        )
        engine.loadFromSessionRoute(ghostRoute)
        assertTrue(engine.hasGhost)

        // Current rider has covered 500m in only 70 seconds (Ghost took 100s for 500m)
        val state = engine.evaluate(currentDistMeters = 500.0, currentElapsedSec = 70L)

        assertTrue(state.isActive)
        assertTrue("Rider should be ahead", state.isAhead)
        assertTrue("Time delta should be positive (~ +30s)", state.timeDeltaSeconds > 20.0)
        assertTrue("Distance delta should be positive", state.distanceDeltaMeters > 0.0)
    }

    @Test
    fun testGhostPacerBehindWhenRiderIsSlower() {
        val engine = GhostPacerEngine()

        val ghostRoute = listOf(
            RoutePoint(lat = 37.9800, lng = 23.7200, alt = 10.0, timestamp = 0L, speedMps = 10.0),
            RoutePoint(lat = 37.9890, lng = 23.7200, alt = 10.0, timestamp = 100L, speedMps = 10.0)
        )
        engine.loadFromSessionRoute(ghostRoute)

        // Rider has only covered 200m at 60s (Ghost covered ~600m at 60s)
        val state = engine.evaluate(currentDistMeters = 200.0, currentElapsedSec = 60L)

        assertTrue(state.isActive)
        assertFalse("Rider should be behind", state.isAhead)
        assertTrue("Time delta should be negative", state.timeDeltaSeconds < 0.0)
        assertTrue("Distance delta should be negative", state.distanceDeltaMeters < 0.0)
    }
}
