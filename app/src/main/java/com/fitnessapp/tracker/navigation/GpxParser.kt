package com.fitnessapp.tracker.navigation

import com.fitnessapp.tracker.engine.PhysicsEngine
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

data class GpxPoint(
    val lat: Double,
    val lng: Double,
    val ele: Double = 0.0,
    val cumulativeDistMeters: Double = 0.0
)

data class GpxRoute(
    val name: String,
    val points: List<GpxPoint>,
    val totalDistanceMeters: Double,
    val elevationGainMeters: Double,
    val climbs: List<ClimbSegment> = emptyList()
)

object GpxParser {

    /**
     * Parses standard GPX 1.0/1.1 XML stream into a structured GpxRoute using DOM parser.
     */
    fun parse(inputStream: InputStream): GpxRoute {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(inputStream)
            doc.documentElement?.normalize()

            var routeName = "Imported Route"
            val nameNodes = doc.getElementsByTagName("name")
            if (nameNodes != null && nameNodes.length > 0) {
                val nText = nameNodes.item(0)?.textContent?.trim().orEmpty()
                if (nText.isNotBlank()) {
                    routeName = nText
                }
            }

            val rawPoints = mutableListOf<Triple<Double, Double, Double>>() // lat, lng, ele

            val tagNames = listOf("trkpt", "rtept", "wpt")
            for (tag in tagNames) {
                val pointNodes = doc.getElementsByTagName(tag)
                if (pointNodes != null && pointNodes.length > 0) {
                    for (i in 0 until pointNodes.length) {
                        val node = pointNodes.item(i)
                        if (node != null && node.nodeType == Node.ELEMENT_NODE) {
                            val elem = node as Element
                            val lat = elem.getAttribute("lat").toDoubleOrNull() ?: 0.0
                            val lon = elem.getAttribute("lon").toDoubleOrNull() ?: 0.0

                            var ele = 0.0
                            val eleNodes = elem.getElementsByTagName("ele")
                            if (eleNodes != null && eleNodes.length > 0) {
                                ele = eleNodes.item(0)?.textContent?.toDoubleOrNull() ?: 0.0
                            }

                            if (lat != 0.0 || lon != 0.0) {
                                rawPoints.add(Triple(lat, lon, ele))
                            }
                        }
                    }
                    if (rawPoints.isNotEmpty()) break
                }
            }

            if (rawPoints.isEmpty()) {
                return GpxRoute(name = routeName, points = emptyList(), totalDistanceMeters = 0.0, elevationGainMeters = 0.0)
            }

            var cumDist = 0.0
            var totalGain = 0.0
            val processedPoints = mutableListOf<GpxPoint>()

            for (i in rawPoints.indices) {
                val pt = rawPoints[i]
                if (i > 0) {
                    val prev = rawPoints[i - 1]
                    val dist = PhysicsEngine.haversineDistance(prev.first, prev.second, pt.first, pt.second)
                    cumDist += dist

                    val eleDelta = pt.third - prev.third
                    if (eleDelta > 0.0) {
                        totalGain += eleDelta
                    }
                }
                processedPoints.add(
                    GpxPoint(
                        lat = pt.first,
                        lng = pt.second,
                        ele = pt.third,
                        cumulativeDistMeters = cumDist
                    )
                )
            }

            val climbs = ClimbEngine.detectClimbs(processedPoints)

            GpxRoute(
                name = routeName,
                points = processedPoints,
                totalDistanceMeters = cumDist,
                elevationGainMeters = totalGain,
                climbs = climbs
            )
        } catch (e: Exception) {
            GpxRoute(name = "Imported Route", points = emptyList(), totalDistanceMeters = 0.0, elevationGainMeters = 0.0)
        }
    }
}
