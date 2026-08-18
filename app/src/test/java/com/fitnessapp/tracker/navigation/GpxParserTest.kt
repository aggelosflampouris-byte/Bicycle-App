package com.fitnessapp.tracker.navigation

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class GpxParserTest {

    @Test
    fun testParseValidGpxXml() {
        val gpxXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="SmartTrack">
                <trk>
                    <name>Mountain Pass Loop</name>
                    <trkseg>
                        <trkpt lat="37.9838" lon="23.7275">
                            <ele>120.0</ele>
                        </trkpt>
                        <trkpt lat="37.9850" lon="23.7290">
                            <ele>135.0</ele>
                        </trkpt>
                        <trkpt lat="37.9870" lon="23.7310">
                            <ele>160.0</ele>
                        </trkpt>
                    </trkseg>
                </trk>
            </gpx>
        """.trimIndent()

        val inputStream = ByteArrayInputStream(gpxXml.toByteArray(Charsets.UTF_8))
        val route = GpxParser.parse(inputStream)

        assertNotNull(route)
        assertEquals("Mountain Pass Loop", route.name)
        assertEquals(3, route.points.size)
        assertTrue("Total distance should be greater than 0", route.totalDistanceMeters > 0)
        assertEquals(40.0, route.elevationGainMeters, 0.1) // 135-120 (15) + 160-135 (25) = 40m
    }

    @Test
    fun testParseEmptyGpxHandlesGracefully() {
        val emptyGpx = "<gpx></gpx>"
        val inputStream = ByteArrayInputStream(emptyGpx.toByteArray(Charsets.UTF_8))
        val route = GpxParser.parse(inputStream)

        assertNotNull(route)
        assertTrue(route.points.isEmpty())
        assertEquals(0.0, route.totalDistanceMeters, 0.001)
    }
}
