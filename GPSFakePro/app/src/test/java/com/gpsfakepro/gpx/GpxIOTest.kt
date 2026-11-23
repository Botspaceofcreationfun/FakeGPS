package com.gpsfakepro.gpx

import com.gpsfakepro.data.RouteEntity
import com.gpsfakepro.data.RouteWaypointEntity
import com.gpsfakepro.data.RouteWithWaypoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

class GpxIOTest {

    private fun parse(xml: String): GpxIO.ImportResult {
        val parser: XmlPullParser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))
        return GpxIO.parse(parser)
    }

    @Test
    fun imports_track_waypoints_with_name_and_elevation() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
              <trk>
                <name>My Route</name>
                <trkseg>
                  <trkpt lat="51.500000" lon="-0.100000"><ele>10.5</ele></trkpt>
                  <trkpt lat="51.510000" lon="-0.110000"></trkpt>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()

        val result = parse(xml)
        assertTrue(result is GpxIO.ImportResult.Success)
        result as GpxIO.ImportResult.Success
        assertEquals("My Route", result.name)
        assertEquals(2, result.waypoints.size)
        assertEquals(51.5, result.waypoints[0].latitude, 1e-9)
        assertEquals(-0.1, result.waypoints[0].longitude, 1e-9)
        assertEquals(10.5, result.waypoints[0].altitude!!, 1e-9)
        assertEquals(null, result.waypoints[1].altitude)
    }

    @Test
    fun falls_back_to_wpt_when_no_track() {
        val xml = """
            <gpx version="1.1" creator="test">
              <wpt lat="1.0" lon="2.0"><name>Home</name></wpt>
              <wpt lat="3.0" lon="4.0"></wpt>
            </gpx>
        """.trimIndent()
        val result = parse(xml)
        assertTrue(result is GpxIO.ImportResult.Success)
        result as GpxIO.ImportResult.Success
        assertEquals(2, result.waypoints.size)
        assertEquals(null, result.name)
    }

    @Test
    fun rejects_out_of_range_coordinates_with_point_index() {
        val xml = """
            <gpx version="1.1" creator="test">
              <trk><trkseg>
                <trkpt lat="51.5" lon="-0.1"/>
                <trkpt lat="91.0" lon="0.0"/>
              </trkseg></trk>
            </gpx>
        """.trimIndent()
        val result = parse(xml)
        assertTrue(result is GpxIO.ImportResult.Failure)
        result as GpxIO.ImportResult.Failure
        assertEquals(GpxIO.ImportResult.Reason.INVALID_COORDS, result.reason)
        assertEquals(2, result.pointIndex)
    }

    @Test
    fun rejects_garbage_as_not_xml() {
        val result = parse("this is not xml at all")
        assertTrue(result is GpxIO.ImportResult.Failure)
        result as GpxIO.ImportResult.Failure
        assertEquals(GpxIO.ImportResult.Reason.NOT_XML, result.reason)
    }

    @Test
    fun empty_gpx_yields_empty_error() {
        val result = parse("<gpx version=\"1.1\"></gpx>")
        assertTrue(result is GpxIO.ImportResult.Failure)
        result as GpxIO.ImportResult.Failure
        assertEquals(GpxIO.ImportResult.Reason.EMPTY, result.reason)
    }

    @Test
    fun export_escapes_names_and_formats_coordinates() {
        val route = RouteWithWaypoints(
            route = RouteEntity(id = 1L, name = "A&B <demo> \"q\" 's'"),
            waypoints = listOf(
                RouteWaypointEntity(routeId = 1L, orderIndex = 0, latitude = 51.5, longitude = -0.1),
                RouteWaypointEntity(routeId = 1L, orderIndex = 1, latitude = 51.51, longitude = -0.11, altitude = 12.0)
            )
        )
        val xml = GpxIO.exportRoute(route)
        assertTrue(xml.contains("<name>A&amp;B &lt;demo&gt; &quot;q&quot; &apos;s&apos;</name>"))
        assertTrue(xml.contains("<trkpt lat=\"51.500000\" lon=\"-0.100000\">"))
        assertTrue(xml.contains("<ele>12.0</ele>"))
        assertTrue(xml.startsWith("<?xml version=\"1.0\""))
        assertTrue(xml.trimEnd().endsWith("</gpx>"))
    }
}
