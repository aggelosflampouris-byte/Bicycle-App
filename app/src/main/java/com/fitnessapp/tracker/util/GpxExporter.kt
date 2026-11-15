package com.fitnessapp.tracker.util

import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import com.fitnessapp.tracker.service.RoutePoint

object GpxExporter {
    /**
     * Converts a session and its route points into a standard GPX 1.1 XML string.
     * GPX format is widely supported by Strava, Garmin, and other fitness platforms.
     */
    fun generateGpx(session: WorkoutSessionEntity, routePoints: List<RoutePoint>): String {
        val sb = java.lang.StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"Smart Track\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("  <trk>\n")
        sb.append("    <name>Smart Track ${session.activityType} Session</name>\n")
        
        // Group points by lap to support multiple track segments (trkseg)
        val laps = routePoints.groupBy { it.lap }
        
        laps.toSortedMap().forEach { (_, points) ->
            sb.append("    <trkseg>\n")
            for (point in points) {
                sb.append("      <trkpt lat=\"${point.lat}\" lon=\"${point.lng}\">\n")
                sb.append("        <ele>${point.alt}</ele>\n")
                val timeString = DateFormats.formatIsoUtcSeconds(point.timestamp)
                sb.append("        <time>${timeString}</time>\n")
                sb.append("      </trkpt>\n")
            }
            sb.append("    </trkseg>\n")
        }
        
        sb.append("  </trk>\n")
        sb.append("</gpx>")
        
        return sb.toString()
    }
}
