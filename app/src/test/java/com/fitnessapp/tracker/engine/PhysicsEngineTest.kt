package com.fitnessapp.tracker.engine

import com.fitnessapp.tracker.data.local.entity.UserEntity
import org.junit.Assert.*
import org.junit.Test

class PhysicsEngineTest {

    @Test
    fun testWattsPerKgCalculationForRealisticCyclingSpeeds() {
        val user = UserEntity(weightKg = 75f, age = 30)

        // Moderate cycling (18 km/h, MET 8.0) yields ~2.15 W/kg
        val moderateWatts = PhysicsEngine.calculateWattsPerKg(18.0, user, "CYCLING")
        assertTrue("Moderate Watts/kg ($moderateWatts) should be in realistic range", moderateWatts in 1.8..2.5)

        // Racing / fast cycling (32 km/h, MET 16.0) yields ~4.29 W/kg
        val fastWatts = PhysicsEngine.calculateWattsPerKg(32.0, user, "CYCLING")
        assertTrue("Fast Watts/kg ($fastWatts) should be in realistic range", fastWatts in 3.5..4.8)

        // Stopped (0 km/h) yields 0 W/kg
        val zeroWatts = PhysicsEngine.calculateWattsPerKg(0.0, user, "CYCLING")
        assertEquals(0.0, zeroWatts, 0.001)

        // Ensure it is NEVER pegged at the 10.0 W/kg ceiling for normal rides
        assertNotEquals(10.0, moderateWatts, 0.001)
        assertNotEquals(10.0, fastWatts, 0.001)
    }

    @Test
    fun testCaloriesCalculationForRealisticDuration() {
        val user = UserEntity(weightKg = 70f, age = 28)

        // 1 hour at 18 km/h (MET 8.0) -> 8.0 * 70 * 1.0 = 560 kcal
        val calories = PhysicsEngine.calculateCalories(user, 3600L, 18.0, "CYCLING")
        assertEquals(560.0, calories, 1.0)
    }
}
