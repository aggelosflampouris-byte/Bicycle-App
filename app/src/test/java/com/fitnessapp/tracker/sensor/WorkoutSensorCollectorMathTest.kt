package com.fitnessapp.tracker.sensor

import org.junit.Assert.*
import org.junit.Test

class WorkoutSensorCollectorMathTest {

    @Test
    fun `computeCadenceSpm estimates steady running cadence accurately and clamps sparse inputs`() {
        assertEquals(0.0, WorkoutSensorCollector.computeCadenceSpm(listOf(1000L, 1400L)), 0.01)

        // 170 spm -> step interval ~353 ms over 10 intervals = 3530 ms (11 timestamps)
        val steadyRunTimestamps = (0..10).map { it * 353L }
        val cadence = WorkoutSensorCollector.computeCadenceSpm(steadyRunTimestamps)

        assertTrue("Expected ~170 spm cadence, got $cadence", cadence in 168.0..172.0)
    }

    @Test
    fun `computeIntervalStabilityScore distinguishes metronomic form from irregular fatigue drift`() {
        // Too few steps returns null
        assertNull(WorkoutSensorCollector.computeIntervalStabilityScore(listOf(0L, 500L, 1000L, 1500L)))

        // Steady walking cadence (~500ms step intervals)
        val steadySteps = (0..11).map { it * 500L }
        val steadyScore = WorkoutSensorCollector.computeIntervalStabilityScore(steadySteps)
        assertNotNull(steadyScore)
        assertTrue("Steady strides should score high stability ($steadyScore)", steadyScore!! >= 95.0)

        // Irregular fatigued strides oscillating between 320ms and 880ms
        val erraticIntervals = listOf(320L, 860L, 340L, 880L, 310L, 850L, 330L, 870L)
        val erraticTimestamps = erraticIntervals.runningFold(0L) { acc, dt -> acc + dt }
        val erraticScore = WorkoutSensorCollector.computeIntervalStabilityScore(erraticTimestamps)

        assertNotNull(erraticScore)
        assertTrue(
            "Erratic intervals ($erraticScore) must score significantly lower than steady gait ($steadyScore)",
            erraticScore!! < 50.0 && erraticScore < steadyScore
        )
    }

    @Test
    fun `computeIntervalStabilityScore ignores brief stationary gaps longer than 2500ms`() {
        // 5 steady steps (4 intervals) + 5 second traffic pause + 3 more steady steps
        val firstPart = listOf(0L, 500L, 1000L, 1500L, 2000L)
        val secondPart = listOf(7500L, 8000L, 8500L)
        val score = WorkoutSensorCollector.computeIntervalStabilityScore(firstPart + secondPart)

        assertNotNull(score)
        assertTrue("Pause outlier > 2500ms should be ignored leaving high stability ($score)", score!! >= 95.0)
    }
}

