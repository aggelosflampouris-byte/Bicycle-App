package com.fitnessapp.tracker.ui.segments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.tracker.data.local.dao.SegmentDao
import com.fitnessapp.tracker.data.local.entity.SegmentEffortEntity
import com.fitnessapp.tracker.data.local.entity.SegmentEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SegmentWithBest(
    val segment: SegmentEntity,
    val bestEffort: SegmentEffortEntity?,
    val totalAttempts: Int
)

@HiltViewModel
class SegmentsViewModel @Inject constructor(
    private val segmentDao: SegmentDao
) : ViewModel() {

    private val _selectedSegmentLeaderboard = MutableStateFlow<List<SegmentEffortEntity>>(emptyList())
    val selectedSegmentLeaderboard: StateFlow<List<SegmentEffortEntity>> = _selectedSegmentLeaderboard.asStateFlow()

    private val _activeSegmentId = MutableStateFlow<Long?>(null)
    val activeSegmentId: StateFlow<Long?> = _activeSegmentId.asStateFlow()

    val segmentsWithBest: Flow<List<SegmentWithBest>> = segmentDao.getAllSegments()
        .map { segments ->
            segments.map { seg ->
                val best = segmentDao.getBestEffortForSegment(seg.id)
                val count = segmentDao.getEffortCountForSegment(seg.id)
                SegmentWithBest(seg, best, count)
            }
        }

    fun selectSegment(segmentId: Long) {
        _activeSegmentId.value = segmentId
        viewModelScope.launch {
            segmentDao.getLeaderboardForSegment(segmentId).collect {
                _selectedSegmentLeaderboard.value = it
            }
        }
    }

    fun clearSelectedSegment() {
        _activeSegmentId.value = null
        _selectedSegmentLeaderboard.value = emptyList()
    }

    fun createSegment(
        name: String,
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        distanceMeters: Double,
        elevationGainMeters: Double = 0.0,
        activityType: String = "CYCLING"
    ) {
        if (name.isBlank() || distanceMeters <= 0) return
        val avgGrade = if (distanceMeters > 0) (elevationGainMeters / distanceMeters) * 100.0 else 0.0
        viewModelScope.launch {
            val segment = SegmentEntity(
                name = name.trim(),
                startLat = startLat,
                startLng = startLng,
                endLat = endLat,
                endLng = endLng,
                distanceMeters = distanceMeters,
                elevationGainMeters = elevationGainMeters,
                avgGradientPct = avgGrade,
                activityType = activityType
            )
            segmentDao.insertSegment(segment)
        }
    }

    fun deleteSegment(segment: SegmentEntity) {
        viewModelScope.launch {
            segmentDao.deleteSegment(segment)
            if (_activeSegmentId.value == segment.id) {
                clearSelectedSegment()
            }
        }
    }
}
