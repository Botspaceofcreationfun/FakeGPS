package com.gpsfakepro.gpx

import android.util.Xml
import com.gpsfakepro.data.RouteEntity
import com.gpsfakepro.data.RouteWithWaypoints
import com.gpsfakepro.data.SavedLocationEntity
import com.gpsfakepro.engine.RoutePoint
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import java.util.Locale

/**
 * GPX 1.1 import/export.
 *
 * Import reads `<trkpt>` (track) points first, falling back to `<rtept>` route
 * points, then `<wpt>` waypoints. Coordinates are validated; the file is rejected
 * with a specific error if any point is out of range or malformed.
 *
 * Export writes saved locations as `<wpt>` and routes as `<trk>` elements. All
 * names are XML-escaped; numbers use a decimal point regardless of locale.
 */
object GpxIO {

    sealed class ImportResult {
        data class Success(val name: String?, val waypoints: List<RoutePoint>) : ImportResult()

        enum class Reason { NOT_XML, PARSE_ERROR, EMPTY, INVALID_COORDS }

        data class Failure(
            val reason: Reason,
            val detail: String? = null,
            val pointIndex: Int? = null
        ) : ImportResult()
    }

    private const val GPX_NS = "http://www.topografix.com/GPX/1/1"
    private val POINT_ELEMENTS = setOf("wpt", "rtept", "trkpt")

    /** App-side entry point: builds a parser and delegates to [parse]. */
    fun parse(input: InputStream): ImportResult {
        val parser = Xml.newPullParser()
        return try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(input, null)
            parse(parser)
        } catch (e: XmlPullParserException) {
            ImportResult.Failure(ImportResult.Reason.PARSE_ERROR, e.message)
        } catch (e: IOException) {
            ImportResult.Failure(ImportResult.Reason.PARSE_ERROR, e.message)
        }
    }

    /**
     * Core parsing logic over a raw [XmlPullParser] (injected so JVM unit tests
     * can drive it with a kxml2 parser).
     */
    fun parse(parser: XmlPullParser): ImportResult {
        val trkPoints = ArrayList<RoutePoint>()
        val rtePoints = ArrayList<RoutePoint>()
        val wptPoints = ArrayList<RoutePoint>()
        var firstTrackName: String? = null
        var firstRouteName: String? = null
        var rootName: String? = null

        val stack = ArrayDeque<String>()
        var collectingName = false
        var collectingEle = false
        var textBuffer = StringBuilder()
        var pendingLat: Double? = null
        var pendingLon: Double? = null
        var pendingEle: Double? = null
        var totalPointCount = 0

        try {
            if (parser.eventType != XmlPullParser.START_DOCUMENT) {
                return ImportResult.Failure(ImportResult.Reason.NOT_XML)
            }

            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name ?: ""
                        if (rootName == null) rootName = name
                        when {
                            name.equals("name", true) -> {
                                collectingName = true
                                textBuffer = StringBuilder()
                            }
                            name.equals("ele", true) -> {
                                collectingEle = true
                                textBuffer = StringBuilder()
                            }
                            POINT_ELEMENTS.any { it.equals(name, true) } -> {
                                pendingLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                                pendingLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                                pendingEle = null
                            }
                        }
                        stack.addLast(name)
                    }

                    XmlPullParser.TEXT -> {
                        if (collectingName || collectingEle) {
                            textBuffer.append(parser.text ?: "")
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        val name = parser.name ?: ""
                        when {
                            name.equals("name", true) && collectingName -> {
                                val value = textBuffer.toString().trim()
                                val parent = stack.getOrNull(stack.size - 2)
                                when {
                                    parent.equals("trk", true) && firstTrackName == null -> firstTrackName = value
                                    parent.equals("rte", true) && firstRouteName == null -> firstRouteName = value
                                }
                                collectingName = false
                            }
                            name.equals("ele", true) && collectingEle -> {
                                val value = textBuffer.toString().trim().toDoubleOrNull()
                                val parent = stack.getOrNull(stack.size - 2)
                                if (parent != null && POINT_ELEMENTS.any { it.equals(parent, true) }) {
                                    pendingEle = value
                                }
                                collectingEle = false
                            }
                            POINT_ELEMENTS.any { it.equals(name, true) } -> {
                                totalPointCount++
                                val lat = pendingLat
                                val lon = pendingLon
                                val point = if (lat != null && lon != null &&
                                    lat in -90.0..90.0 && lon in -180.0..180.0
                                ) {
                                    RoutePoint(latitude = lat, longitude = lon, altitude = pendingEle)
                                } else null
                                if (point == null) {
                                    return ImportResult.Failure(
                                        ImportResult.Reason.INVALID_COORDS,
                                        pointIndex = totalPointCount
                                    )
                                }
                                when {
                                    name.equals("trkpt", true) -> trkPoints.add(point)
                                    name.equals("rtept", true) -> rtePoints.add(point)
                                    else -> wptPoints.add(point)
                                }
                                pendingLat = null
                                pendingLon = null
                                pendingEle = null
                            }
                        }
                        stack.removeLastOrNull()
                    }
                }
                parser.next()
            }
        } catch (e: XmlPullParserException) {
            return ImportResult.Failure(ImportResult.Reason.PARSE_ERROR, e.message)
        } catch (e: IOException) {
            return ImportResult.Failure(ImportResult.Reason.PARSE_ERROR, e.message)
        }

        val points: List<RoutePoint> =
            trkPoints.takeIf { it.isNotEmpty() }?.let { it } ?:
            rtePoints.takeIf { it.isNotEmpty() }?.let { it } ?:
            wptPoints
        if (points.isEmpty()) {
            val looksLikeGpx = rootName != null && rootName.lowercase().endsWith("gpx")
            return ImportResult.Failure(
                if (looksLikeGpx) ImportResult.Reason.EMPTY else ImportResult.Reason.NOT_XML
            )
        }
        val name = firstTrackName ?: firstRouteName
        return ImportResult.Success(name, points)
    }

    // ---- Export -----------------------------------------------------------

    /** Exports saved locations and routes to a single GPX 1.1 document. */
    fun export(locations: List<SavedLocationEntity>, routes: List<RouteWithWaypoints>): String {
        val sb = StringBuilder(1024)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"GPS Fake Pro\" xmlns=\"").append(GPX_NS).append("\">\n")

        for (loc in locations) {
            sb.append("  <wpt lat=\"").append(fmt(loc.latitude)).append("\" lon=\"")
                .append(fmt(loc.longitude)).append("\">\n")
            sb.append("    <name>").append(escape(loc.name.trim())).append("</name>\n")
            loc.altitude?.let { sb.append("    <ele>").append(fmt1(it)).append("</ele>\n") }
            sb.append("  </wpt>\n")
        }

        for (route in routes) {
            sb.append("  <trk>\n")
            sb.append("    <name>").append(escape(route.route.name.trim())).append("</name>\n")
            sb.append("    <trkseg>\n")
            for (wp in route.waypoints) {
                sb.append("      <trkpt lat=\"").append(fmt(wp.latitude)).append("\" lon=\"")
                    .append(fmt(wp.longitude)).append("\">\n")
                wp.altitude?.let { sb.append("        <ele>").append(fmt1(it)).append("</ele>\n") }
                sb.append("      </trkpt>\n")
            }
            sb.append("    </trkseg>\n")
            sb.append("  </trk>\n")
        }

        sb.append("</gpx>\n")
        return sb.toString()
    }

    /** Exports a single route as a GPX document (used for per-route export). */
    fun exportRoute(route: RouteWithWaypoints): String =
        export(emptyList(), listOf(route))

    /** Escapes XML-significant characters in user-provided names. */
    fun escape(input: String): String = buildString(input.length) {
        for (c in input) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(c)
            }
        }
    }

    /** Lat/lon to 6 decimal places (~0.1 m resolution), always with a dot. */
    fun fmt(value: Double): String = String.format(Locale.US, "%.6f", value)

    fun fmt1(value: Double): String = String.format(Locale.US, "%.1f", value)

    /** Route name with control characters stripped (safety on export). */
    fun cleanRouteName(name: String): String =
        name.filter { it.code >= 32 && it.code != 127 }.trim().ifEmpty { "Route" }
}
