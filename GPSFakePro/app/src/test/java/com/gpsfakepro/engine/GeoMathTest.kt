package com.gpsfakepro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {

    @Test
    fun distance_london_to_paris_is_about_344km() {
        val london = LatLng(51.5074, -0.1278)
        val paris = LatLng(48.8566, 2.3522)
        val d = GeoMath.distanceMeters(london, paris)
        assertTrue("got $d", d in 340_000.0..348_000.0)
    }

    @Test
    fun distance_one_degree_of_longitude_at_equator_is_about_111km() {
        val d = GeoMath.distanceMeters(LatLng(0.0, 0.0), LatLng(0.0, 1.0))
        assertTrue("got $d", d in 111_100.0..111_300.0)
    }

    @Test
    fun distance_same_point_is_zero() {
        assertEquals(0.0, GeoMath.distanceMeters(LatLng(12.0, 34.0), LatLng(12.0, 34.0)), 0.001)
    }

    @Test
    fun bearing_east_is_90_north_is_0() {
        assertEquals(90.0, GeoMath.initialBearingDeg(LatLng(0.0, 0.0), LatLng(0.0, 1.0)), 0.5)
        assertEquals(0.0, GeoMath.initialBearingDeg(LatLng(0.0, 0.0), LatLng(1.0, 0.0)), 0.5)
    }

    @Test
    fun normalize_bearing_wraps() {
        assertEquals(350.0, GeoMath.normalizeBearing(-10.0), 1e-9)
        assertEquals(10.0, GeoMath.normalizeBearing(370.0), 1e-9)
        assertEquals(180.0, GeoMath.normalizeBearing(180.0), 1e-9)
    }

    @Test
    fun interpolate_midpoint() {
        val mid = GeoMath.interpolate(LatLng(10.0, 10.0), LatLng(12.0, 14.0), 0.5)
        assertEquals(11.0, mid.latitude, 1e-9)
        assertEquals(12.0, mid.longitude, 1e-9)
    }

    @Test
    fun interpolate_unwraps_antimeridian() {
        val mid = GeoMath.interpolate(LatLng(0.0, 179.0), LatLng(0.0, -179.0), 0.5)
        assertEquals(180.0, mid.longitude, 1e-6)
    }
}
