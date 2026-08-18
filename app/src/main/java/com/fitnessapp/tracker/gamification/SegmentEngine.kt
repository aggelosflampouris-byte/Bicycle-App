package com.fitnessapp.tracker.gamification

import com.fitnessapp.tracker.data.local.dao.SegmentDao
import com.fitnessapp.tracker.data.local.entity.SegmentEffortEntity
import com.fitnessapp.tracker.data.local.entity.SegmentEntity
import com.fitnessapp.tracker.engine.PhysicsEngine
import com.fitnessapp.tracker.service.RoutePoint
import kotlin.math.max

data class LiveSegmentStatus(
    val activeSegment: SegmentEntity? = null,
    val elapsedInSegmentSec: Long = 0L,
    val distanceInSegmentM: Double = 0.0,
    val isNearStart: Boolean = false,
    val completedEffort: SegmentEffortEntity? = null,
    val isNewPr: Boolean = false
)

class SegmentEngine {

    companion object {
        const val GATE_RADIUS_METERS = 35.0
    }

    private var activeSegment: SegmentEntity? = null
    private var segmentStartElapsedSec: Long = 0L
    private var segmentStartDistM: Double = 0.0
    private var segmentMaxSpeedMps: Double = 0.0

    /**
     * Evaluates live rider GPS point against bookmarked segments.
     */
    fun evaluateLive(
        lat: Double,
        lng: Double,
        speedMps: Double,
        elapsedSec: Long,
        totalDistM: Double,
        availableSegments: List<SegmentEntity>
    ): LiveSegmentStatus {
        if (availableSegments.isEmpty()) {
            return LiveSegmentStatus()
        }

        // Case 1: Rider is inside an active segment sprint
        val currentActive = activeSegment
        if (currentActive != null) {
            if (speedMps > segmentMaxSpeedMps) {
                segmentMaxSpeedMps = speedMps
            }

            val distToEnd = PhysicsEngine.haversineDistance(lat, lng, currentActive.endLat, currentActive.endLng)
            val elapsedInSegment = max(1L, elapsedSec - segmentStartElapsedSec)
            val distInSegment = max(0.0, totalDistM - segmentStartDistM)

            // Check if rider completed the segment gate
            if (distToEnd <= GATE_RADIUS_METERS && distInSegment >= (currentActive.distanceMeters * 0.7)) {
                val avgSpeedKmh = if (elapsedInSegment > 0) (distInSegment / 1000.0) / (elapsedInSegment / 3600.0) else 0.0
                val effort = SegmentEffortEntity(
                    segmentId = currentActive.id,
                    sessionId = 0L,
                    elapsedSeconds = elapsedInSegment,
                    avgSpeedKmh = avgSpeedKmh,
                    maxSpeedKmh = PhysicsEngine.metersPerSecondToKmh(segmentMaxSpeedMps),
                    dateMs = System.currentTimeMillis()
                )

                // Reset active segment
                activeSegment = null

                return LiveSegmentStatus(
                    activeSegment = null,
                    completedEffort = effort
                )
            }

            // Check if rider strayed too far (aborted segment)
            if (distInSegment > currentActive.distanceMeters * 2.0) {
                activeSegment = null
                return LiveSegmentStatus()
            }

            return LiveSegmentStatus(
                activeSegment = currentActive,
                elapsedInSegmentSec = elapsedInSegment,
                distanceInSegmentM = distInSegment
            )
        }

        // Case 2: Check if rider is entering the start gate of any segment
        for (segment in availableSegments) {
            val distToStart = PhysicsEngine.haversineDistance(lat, lng, segment.startLat, segment.startLng)
            if (distToStart <= GATE_RADIUS_METERS) {
                activeSegment = segment
                segmentStartElapsedSec = elapsedSec
                segmentStartDistM = totalDistM
                segmentMaxSpeedMps = speedMps

                return LiveSegmentStatus(
                    activeSegment = segment,
                    isNearStart = true
                )
            }
        }

        return LiveSegmentStatus()
    }

    /**
     * Evaluates a completed workout's route against all segments to record efforts and PRs.
     */
    suspend fun evaluateSession(
        sessionId: Long,
        routePoints: List<RoutePoint>,
        segments: List<SegmentEntity>,
        segmentDao: SegmentDao
    ): List<SegmentEffortEntity> {
        if (routePoints.size < 5 || segments.isEmpty()) return emptyList()

        val recordedEfforts = mutableListOf<SegmentEffortEntity>()

        for (segment in segments) {
            var startIndex = -1
            var endIndex = -1

            for (i in routePoints.indices) {
                val pt = routePoints[i]
                val distStart = PhysicsEngine.haversineDistance(pt.lat, pt.lng, segment.startLat, segment.startLng)
                if (startIndex == -1 && distStart <= GATE_RADIUS_METERS) {
                    startIndex = i
                } else if (startIndex != -1) {
                    val distEnd = PhysicsEngine.haversineDistance(pt.lat, pt.lng, segment.endLat, segment.endLng)
                    if (distEnd <= GATE_RADIUS_METERS && i > startIndex + 2) {
                        endIndex = i
                        break
                    }
                }
            }

            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                val startPt = routePoints[startIndex]
                val endPt = routePoints[endIndex]
                val durationSec = max(1L, (endPt.timestamp - startPt.timestamp) / 1000L)

                var maxSpeedMps = 0.0
                var segmentDistM = 0.0
                for (j in startIndex until endIndex) {
                    val d = PhysicsEngine.haversineDistance(routePoints[j].lat, routePoints[j].lng, routePoints[j + 1].lat, routePoints[j + 1].lng)
                    segmentDistM += d
                    if (routePoints[j].speedMps > maxSpeedMps) maxSpeedMps = routePoints[j].speedMps
                }

                val avgSpeedKmh = if (durationSec > 0) (segmentDistM / 1000.0) / (durationSec / 3600.0) else 0.0
                val bestEffort = segmentDao.getBestEffortForSegment(segment.id)
                val isPr = bestEffort == null || durationSec < bestEffort.elapsedSeconds

                val effort = SegmentEffortEntity(
                    segmentId = segment.id,
                    sessionId = sessionId,
                    elapsedSeconds = durationSec,
                    avgSpeedKmh = avgSpeedKmh,
                    maxSpeedKmh = PhysicsEngine.metersPerSecondToKmh(maxSpeedMps),
                    dateMs = System.currentTimeMillis(),
                    isPr = isPr
                )
                segmentDao.insertEffort(effort)
                recordedEfforts.add(effort)
            }
        }

        return recordedEfforts
    }
}
