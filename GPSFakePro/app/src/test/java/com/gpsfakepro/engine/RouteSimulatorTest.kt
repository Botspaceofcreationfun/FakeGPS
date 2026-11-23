package com.gpsfakepro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RouteSimulatorTest {

    private val a = RoutePoint(51.5, -0.1)
    private val b = RoutePoint(51.51, -0.11) // ~1.26 km away

    @Test
    fun validates_waypoint_count() {
        try {
            RouteSimulator(listOf(a), 5.0, 0, false)
            fail("expected RouteValidationException")
        } catch (expected: RouteSimulator.RouteValidationException) {
            assertTrue(expected.message.orEmpty().contains("2"))
        }
    }

    @Test
    fun rejects_invalid_coordinates() {
        try {
            RouteSimulator(listOf(RoutePoint(91.0, 0.0), b), 5.0, 0, false)
            fail("expected RouteValidationException")
        } catch (expected: RouteSimulator.RouteValidationException) {
            assertTrue(expected.message.orEmpty().contains("invalid"))
        }
    }

    @Test
    fun drops_duplicate_zero_length_waypoints() {
        val sim = RouteSimulator(listOf(a, RoutePoint(a.latitude, a.longitude), b), 5.0, 0, false)
        assertEquals(2, sim.waypoints.size)
    }

    @Test
    fun short_route_is_rejected() {
        try {
            RouteSimulator(
                listOf(a, RoutePoint(a.latitude + 1e-9, a.longitude)), 5.0, 0, false
            )
            fail("expected RouteValidationException")
        } catch (expected: RouteSimulator.RouteValidationException) {
            // OK
        }
    }

    @Test
    fun advances_along_segment_with_plausible_speed_and_bearing() {
        val sim = RouteSimulator(listOf(a, b), 10.0, 0, false)
        val step = sim.advance(1000) // 10 m at 10 m/s
        assertEquals(10.0, step.speedMps, 1e-9)
        assertFalse(step.completed)
        assertFalse(step.paused)
        // position should move towards b
        val dist0 = GeoMath.distanceMeters(LatLng(a.latitude, a.longitude), step.position)
        assertTrue("moved $dist0 m", dist0 in 9.5..10.5)
        // b is north-west of a (lat up, lon down) → bearing ~315°, adjusted by
        // the cos(lat) longitude scaling it comes out around 328°.
        assertTrue("bearing ${step.bearingDeg}", step.bearingDeg in 300.0..340.0)
    }

    @Test
    fun pauses_at_waypoint_then_resumes() {
        val mid = RoutePoint(51.505, -0.105)
        val sim = RouteSimulator(listOf(a, mid, b), 10.0, 5000, true)
        val firstLeg = GeoMath.distanceMeters(
            LatLng(a.latitude, a.longitude), LatLng(mid.latitude, mid.longitude)
        )
        // A few ms past the intermediate waypoint.
        val arrivalMs = (firstLeg / 10.0 * 1000.0).toLong() + 10

        var step = sim.advance(arrivalMs)
        assertTrue("should have paused after arrival", step.paused)
        assertTrue("pause ~5000, got ${step.remainingPauseMs}", step.remainingPauseMs in 4900..5000)
        assertEquals(0.0, step.speedMps, 1e-9)
        // during pause the position is exactly the waypoint
        assertEquals(mid.latitude, step.position.latitude, 1e-9)
        assertEquals(mid.longitude, step.position.longitude, 1e-9)

        step = sim.advance(1000)
        assertTrue(step.paused)
        assertTrue("pause ~4000, got ${step.remainingPauseMs}", step.remainingPauseMs in 3900..4900)

        step = sim.advance(4000)
        assertFalse("pause should be finished", step.paused)
        assertEquals(0L, step.remainingPauseMs)
    }

    @Test
    fun completes_at_last_waypoint_and_keeps_reporting_it() {
        val sim = RouteSimulator(listOf(a, b), 10.0, 0, false)
        sim.advance(10_000_000)
        assertTrue(sim.isCompleted)
        var step = sim.advance(1000)
        assertTrue(step.completed)
        assertEquals(b.latitude, step.position.latitude, 1e-9)
        assertEquals(b.longitude, step.position.longitude, 1e-9)
        assertEquals(0.0, step.speedMps, 1e-9)
        assertTrue(step.progress.fraction >= 1f)
    }

    @Test
    fun restart_resets_progress() {
        val sim = RouteSimulator(listOf(a, b), 10.0, 0, false)
        sim.advance(1000)
        assertTrue(sim.currentProgress().distanceTraveledM > 0.0)
        sim.restart()
        assertEquals(0.0, sim.currentProgress().distanceTraveledM, 1e-9)
        assertFalse(sim.isCompleted)
    }

    @Test
    fun zero_speed_is_stationary() {
        val sim = RouteSimulator(listOf(a, b), 0.0, 0, false)
        val step = sim.advance(5000)
        assertEquals(0.0, step.speedMps, 1e-9)
        assertEquals(a.latitude, step.position.latitude, 1e-9)
        assertEquals(a.longitude, step.position.longitude, 1e-9)
    }

    @Test
    fun progress_fraction_monotonic() {
        val sim = RouteSimulator(listOf(a, b), 5.0, 0, false)
        var prev = -1f
        repeat(20) {
            val step = sim.advance(1000)
            assertTrue("fraction must increase: ${step.progress.fraction} vs $prev",
                step.progress.fraction >= prev)
            prev = step.progress.fraction
        }
    }
}
