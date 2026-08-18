package com.fitnessapp.tracker.gamification

import com.fitnessapp.tracker.data.local.entity.SegmentEntity
import org.junit.Assert.*
import org.junit.Test

class SegmentEngineTest {

    @Test
    fun testLiveSegmentStartAndCompletion() {
        val engine = SegmentEngine()

        val segment = SegmentEntity(
            id = 1L,
            name = "Hill Climb Sprint",
            startLat = 37.9800,
            startLng = 23.7200,
            endLat = 37.9890,
            endLng = 23.7200,
            distanceMeters = 1000.0,
            elevationGainMeters = 40.0
        )

        // 1. Rider approaches start gate (< 25m)
        val startStatus = engine.evaluateLive(
            lat = 37.9801,
            lng = 23.7200,
            speedMps = 8.0,
            elapsedSec = 100L,
            totalDistM = 5000.0,
            availableSegments = listOf(segment)
        )

        assertTrue(startStatus.isNearStart)
        assertNotNull(startStatus.activeSegment)
        assertEquals("Hill Climb Sprint", startStatus.activeSegment?.name)

        // 2. Rider in the middle of segment
        val midStatus = engine.evaluateLive(
            lat = 37.9845,
            lng = 23.7200,
            speedMps = 9.0,
            elapsedSec = 140L,
            totalDistM = 5500.0,
            availableSegments = listOf(segment)
        )
        assertNotNull(midStatus.activeSegment)
        assertEquals(40L, midStatus.elapsedInSegmentSec)
        assertEquals(500.0, midStatus.distanceInSegmentM, 1.0)

        // 3. Rider hits the end gate
        val finishStatus = engine.evaluateLive(
            lat = 37.9890,
            lng = 23.7200,
            speedMps = 10.0,
            elapsedSec = 180L,
            totalDistM = 6000.0,
            availableSegments = listOf(segment)
        )

        assertNull("Active segment should be cleared upon finish", finishStatus.activeSegment)
        assertNotNull("Should have recorded completed effort", finishStatus.completedEffort)
        assertEquals(80L, finishStatus.completedEffort?.elapsedSeconds)
    }
}
