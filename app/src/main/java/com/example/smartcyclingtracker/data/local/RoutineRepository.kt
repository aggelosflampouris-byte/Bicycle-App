package com.example.smartcyclingtracker.data.local

import com.example.smartcyclingtracker.data.local.dao.RoutineDao
import com.example.smartcyclingtracker.data.local.dao.WorkoutSessionDao
import com.example.smartcyclingtracker.data.local.entity.RoutineEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

data class RoutineProgress(
    val routine: RoutineEntity,
    val currentValue: Double,
    val isCompleted: Boolean
)

@Singleton
class RoutineRepository @Inject constructor(
    private val routineDao: RoutineDao,
    private val workoutSessionDao: WorkoutSessionDao
) {
    fun getRoutineProgressFlow(activityType: String): Flow<RoutineProgress?> {
        return routineDao.getRoutineFlow(activityType).flatMapLatest { routine ->
            if (routine == null) {
                flowOf(null)
            } else {
                val currentTime = System.currentTimeMillis()
                var activeRoutine = routine
                
                // Check if current period has ended
                if (currentTime > routine.currentPeriodEnd) {
                    activeRoutine = advanceRoutinePeriod(routine, currentTime)
                }
                
                workoutSessionDao.getAggregateSummaryForPeriodFlow(
                    startTime = activeRoutine.currentPeriodStart,
                    endTime = activeRoutine.currentPeriodEnd,
                    activityType = activeRoutine.activityType
                ).map { summary ->
                    val currentValue = when (activeRoutine.metric) {
                        "CALORIES" -> summary?.totalCals ?: 0.0
                        "DISTANCE" -> (summary?.totalDist ?: 0.0) / 1000.0 // meters to km
                        else -> 0.0
                    }
                    RoutineProgress(
                        routine = activeRoutine,
                        currentValue = currentValue,
                        isCompleted = currentValue >= activeRoutine.targetValue
                    )
                }
            }
        }
    }

    suspend fun saveRoutine(
        activityType: String,
        interval: String,
        metric: String,
        targetValue: Double,
        autoImprove: Boolean
    ) {
        val now = System.currentTimeMillis()
        val (start, end) = calculatePeriod(interval, now)
        val routine = RoutineEntity(
            activityType = activityType,
            interval = interval,
            metric = metric,
            targetValue = targetValue,
            autoImprove = autoImprove,
            currentPeriodStart = start,
            currentPeriodEnd = end
        )
        routineDao.saveRoutine(routine)
    }

    suspend fun deleteRoutine(activityType: String) {
        routineDao.clearRoutine(activityType)
    }

    suspend fun checkAndAdvanceRoutine(activityType: String) {
        val routine = routineDao.getRoutine(activityType) ?: return
        val now = System.currentTimeMillis()
        if (now > routine.currentPeriodEnd) {
            advanceRoutinePeriod(routine, now)
        }
    }

    private suspend fun advanceRoutinePeriod(routine: RoutineEntity, now: Long): RoutineEntity {
        // Calculate new period
        val (start, end) = calculatePeriod(routine.interval, now)
        
        // Check if goal was met in the PREVIOUS period
        val summary = workoutSessionDao.getAggregateSummaryForPeriod(
            routine.currentPeriodStart, routine.currentPeriodEnd, routine.activityType
        )
        
        val achievedValue = when (routine.metric) {
            "CALORIES" -> summary?.totalCals ?: 0.0
            "DISTANCE" -> (summary?.totalDist ?: 0.0) / 1000.0 // meters to km
            else -> 0.0
        }
        
        var newTarget = routine.targetValue
        if (routine.autoImprove && achievedValue >= routine.targetValue) {
            newTarget = routine.targetValue * (1.0 + routine.autoImprovePercentage)
        }
        
        val updatedRoutine = routine.copy(
            targetValue = newTarget,
            currentPeriodStart = start,
            currentPeriodEnd = end,
            lastCompletedPeriodEnd = routine.currentPeriodEnd
        )
        routineDao.saveRoutine(updatedRoutine)
        return updatedRoutine
    }

    suspend fun applyAutoImproveIfMet(routine: RoutineEntity, currentValue: Double) {
        if (routine.autoImprove && currentValue >= routine.targetValue) {
            // we met the goal!
            val newTarget = routine.targetValue * (1.0 + routine.autoImprovePercentage)
            routineDao.saveRoutine(routine.copy(targetValue = newTarget))
        }
    }

    suspend fun getAllRoutines(): List<RoutineEntity> {
        return routineDao.getAllRoutines()
    }

    companion object {
        fun calculatePeriod(interval: String, timeMillis: Long): Pair<Long, Long> {
            val cal = Calendar.getInstance()
            cal.timeInMillis = timeMillis
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            
            val start = cal.timeInMillis
            
            when (interval) {
                "DAILY" -> {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                "WEEKLY" -> {
                    cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                    val weekStart = cal.timeInMillis
                    cal.add(Calendar.WEEK_OF_YEAR, 1)
                    return Pair(weekStart, cal.timeInMillis - 1)
                }
                "MONTHLY" -> {
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    val monthStart = cal.timeInMillis
                    cal.add(Calendar.MONTH, 1)
                    return Pair(monthStart, cal.timeInMillis - 1)
                }
            }
            return Pair(start, cal.timeInMillis - 1)
        }
    }
}
