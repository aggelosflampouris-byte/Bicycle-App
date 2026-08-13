package com.fitnessapp.tracker.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.tracker.data.local.dao.WorkoutSessionDao
import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

enum class TimeFilter { DAILY, WEEKLY, MONTHLY }
enum class MetricFilter { DISTANCE, SPEED, CALORIES }
enum class ChartType { BAR, LINE, AREA }

data class ChartBarData(
    val label: String,
    val value: Float
)

data class AnalyticsUiState(
    val selectedFilter: TimeFilter = TimeFilter.WEEKLY,
    val selectedMetric: MetricFilter = MetricFilter.DISTANCE,
    val selectedChartType: ChartType = ChartType.BAR,
    val chartData: List<ChartBarData> = emptyList(),
    val totalDistanceKm: Double = 0.0,
    val distanceDiffPercent: Double? = null,
    val avgSpeedKmh: Double = 0.0,
    val speedDiffPercent: Double? = null,
    val totalCalories: Double = 0.0,
    val caloriesDiffPercent: Double? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val sessionDao: WorkoutSessionDao
) : ViewModel() {

    private val _filter = MutableStateFlow(TimeFilter.WEEKLY)
    private val _metric = MutableStateFlow(MetricFilter.DISTANCE)
    private val _chartType = MutableStateFlow(ChartType.BAR)
    private val _sessions = MutableStateFlow<List<WorkoutSessionEntity>>(emptyList())
    
    val uiState: StateFlow<AnalyticsUiState> = combine(_filter, _metric, _chartType, _sessions) { filter, metric, chartType, sessions ->
        processData(filter, metric, chartType, sessions)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AnalyticsUiState()
    )

    private val _activityType = MutableStateFlow("CYCLING")

    init {
        viewModelScope.launch {
            combine(sessionDao.getAllSessionsFlow(), _activityType) { allSessions, type ->
                allSessions.filter { it.activityType == type }
            }.collect { list ->
                _sessions.value = list
            }
        }
    }

    fun setActivityType(type: String) {
        _activityType.value = type
    }

    fun setFilter(filter: TimeFilter) {
        _filter.value = filter
    }

    fun setMetric(metric: MetricFilter) {
        _metric.value = metric
    }

    fun setChartType(chartType: ChartType) {
        _chartType.value = chartType
    }

    private fun processData(filter: TimeFilter, metric: MetricFilter, chartType: ChartType, sessions: List<WorkoutSessionEntity>): AnalyticsUiState {
        if (sessions.isEmpty()) return AnalyticsUiState(isLoading = false)

        val cal = Calendar.getInstance()
        val now = System.currentTimeMillis()
        
        // Boundaries
        val currentPeriodStart: Long
        val previousPeriodStart: Long

        when (filter) {
            TimeFilter.DAILY -> { // Last 7 days vs previous 7 days
                cal.timeInMillis = now
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                
                cal.add(Calendar.DAY_OF_YEAR, -6)
                currentPeriodStart = cal.timeInMillis
                
                cal.add(Calendar.DAY_OF_YEAR, -7)
                previousPeriodStart = cal.timeInMillis
            }
            TimeFilter.WEEKLY -> { // Last 4 weeks vs previous 4 weeks
                cal.timeInMillis = now
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                
                cal.add(Calendar.WEEK_OF_YEAR, -3)
                currentPeriodStart = cal.timeInMillis
                
                cal.add(Calendar.WEEK_OF_YEAR, -4)
                previousPeriodStart = cal.timeInMillis
            }
            TimeFilter.MONTHLY -> { // Last 6 months vs previous 6 months
                cal.timeInMillis = now
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                
                cal.add(Calendar.MONTH, -5)
                currentPeriodStart = cal.timeInMillis
                
                cal.add(Calendar.MONTH, -6)
                previousPeriodStart = cal.timeInMillis
            }
        }

        // Split into current vs previous periods for stats
        val currentPeriodSessions = sessions.filter { it.startTime >= currentPeriodStart }
        val previousPeriodSessions = sessions.filter { 
            it.startTime >= previousPeriodStart && it.startTime < currentPeriodStart 
        }

        // Calculate totals for current period
        val currentDist = currentPeriodSessions.sumOf { it.totalDistanceMeters } / 1000.0
        val currentCals = currentPeriodSessions.sumOf { it.caloriesBurned }
        val currentSpeed = if (currentPeriodSessions.isNotEmpty()) currentPeriodSessions.map { it.avgSpeedKmh }.average() else 0.0

        // Calculate totals for previous period
        val prevDist = previousPeriodSessions.sumOf { it.totalDistanceMeters } / 1000.0
        val prevCals = previousPeriodSessions.sumOf { it.caloriesBurned }
        val prevSpeed = if (previousPeriodSessions.isNotEmpty()) previousPeriodSessions.map { it.avgSpeedKmh }.average() else 0.0

        // Diff percentages
        val distDiff = calculateDiff(currentDist, prevDist)
        val speedDiff = calculateDiff(currentSpeed, prevSpeed)
        val calsDiff = calculateDiff(currentCals, prevCals)

        // Build chart data
        val chartData = buildChartData(filter, metric, currentPeriodSessions)

        return AnalyticsUiState(
            selectedFilter = filter,
            selectedMetric = metric,
            selectedChartType = chartType,
            chartData = chartData,
            totalDistanceKm = currentDist,
            distanceDiffPercent = distDiff,
            avgSpeedKmh = currentSpeed,
            speedDiffPercent = speedDiff,
            totalCalories = currentCals,
            caloriesDiffPercent = calsDiff,
            isLoading = false
        )
    }

    private fun calculateDiff(current: Double, previous: Double): Double? {
        if (previous == 0.0) return if (current > 0) 100.0 else null
        return ((current - previous) / previous) * 100.0
    }

    private fun buildChartData(filter: TimeFilter, metric: MetricFilter, sessions: List<WorkoutSessionEntity>): List<ChartBarData> {
        val cal = Calendar.getInstance()
        val dataMap = mutableMapOf<String, Double>()
        val countMap = mutableMapOf<String, Int>()
        val labelsInOrder = mutableListOf<String>()

        when (filter) {
            TimeFilter.DAILY -> {
                val format = java.text.SimpleDateFormat("EEE", Locale.getDefault())
                for (i in 6 downTo 0) {
                    cal.timeInMillis = System.currentTimeMillis()
                    cal.add(Calendar.DAY_OF_YEAR, -i)
                    val label = format.format(cal.time)
                    labelsInOrder.add(label)
                    dataMap[label] = 0.0
                    countMap[label] = 0
                }
                sessions.forEach {
                    val label = format.format(Date(it.startTime))
                    if (dataMap.containsKey(label)) {
                        val valueToAdd = when (metric) {
                            MetricFilter.DISTANCE -> it.totalDistanceMeters / 1000.0
                            MetricFilter.SPEED -> it.avgSpeedKmh
                            MetricFilter.CALORIES -> it.caloriesBurned
                        }
                        dataMap[label] = dataMap[label]!! + valueToAdd
                        countMap[label] = countMap[label]!! + 1
                    }
                }
            }
            TimeFilter.WEEKLY -> {
                val format = java.text.SimpleDateFormat("w", Locale.getDefault())
                for (i in 3 downTo 0) {
                    cal.timeInMillis = System.currentTimeMillis()
                    cal.add(Calendar.WEEK_OF_YEAR, -i)
                    val label = "W${format.format(cal.time)}"
                    labelsInOrder.add(label)
                    dataMap[label] = 0.0
                    countMap[label] = 0
                }
                sessions.forEach {
                    val label = "W${format.format(Date(it.startTime))}"
                    if (dataMap.containsKey(label)) {
                        val valueToAdd = when (metric) {
                            MetricFilter.DISTANCE -> it.totalDistanceMeters / 1000.0
                            MetricFilter.SPEED -> it.avgSpeedKmh
                            MetricFilter.CALORIES -> it.caloriesBurned
                        }
                        dataMap[label] = dataMap[label]!! + valueToAdd
                        countMap[label] = countMap[label]!! + 1
                    }
                }
            }
            TimeFilter.MONTHLY -> {
                val format = java.text.SimpleDateFormat("MMM", Locale.getDefault())
                for (i in 5 downTo 0) {
                    cal.timeInMillis = System.currentTimeMillis()
                    cal.add(Calendar.MONTH, -i)
                    val label = format.format(cal.time)
                    labelsInOrder.add(label)
                    dataMap[label] = 0.0
                    countMap[label] = 0
                }
                sessions.forEach {
                    val label = format.format(Date(it.startTime))
                    if (dataMap.containsKey(label)) {
                        val valueToAdd = when (metric) {
                            MetricFilter.DISTANCE -> it.totalDistanceMeters / 1000.0
                            MetricFilter.SPEED -> it.avgSpeedKmh
                            MetricFilter.CALORIES -> it.caloriesBurned
                        }
                        dataMap[label] = dataMap[label]!! + valueToAdd
                        countMap[label] = countMap[label]!! + 1
                    }
                }
            }
        }

        var weekIndex = 1
        return labelsInOrder.map { label ->
            var value = dataMap[label] ?: 0.0
            if (metric == MetricFilter.SPEED) {
                val count = countMap[label] ?: 0
                if (count > 0) value /= count
            }
            val finalLabel = if (filter == TimeFilter.WEEKLY) "Week ${weekIndex++}" else label
            ChartBarData(label = finalLabel, value = value.toFloat())
        }
    }
}
