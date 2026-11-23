package com.gpsfakepro.engine

/**
 * Advances a simulation along a straight-line route between waypoints.
 *
 * Semantics:
 *  - Waypoints closer than [MIN_SEGMENT_M] to their predecessor are dropped
 *    (zero-length / duplicate segments).
 *  - Movement is straight-line interpolation between consecutive waypoints.
 *  - If pause-at-waypoints is enabled, the simulation waits [pauseMs] at each
 *    intermediate waypoint before continuing (reported speed 0 while paused).
 *  - When the last waypoint is reached the simulation reports `completed`, and
 *    keeps reporting the final position until restarted or stopped.
 *  - Timestamps are not produced here; the service stamps each pushed location.
 *
 * The class is pure Kotlin (no Android imports) and unit-tested on the JVM.
 */
class RouteSimulator(
    rawWaypoints: List<RoutePoint>,
    initialSpeedMps: Double,
    pauseMs: Long,
    pauseEnabled: Boolean
) {

    class RouteValidationException(message: String) : Exception(message)

    companion object {
        const val MIN_SEGMENT_M = 1.0
        const val MIN_WAYPOINTS = 2

        fun validate(rawWaypoints: List<RoutePoint>): List<RoutePoint> {
            if (rawWaypoints.size < MIN_WAYPOINTS) {
                throw RouteValidationException(
                    "need at least $MIN_WAYPOINTS waypoints, got ${rawWaypoints.size}"
                )
            }
            val cleaned = ArrayList<RoutePoint>(rawWaypoints.size)
            for ((i, p) in rawWaypoints.withIndex()) {
                if (!p.isValid) {
                    throw RouteValidationException("waypoint ${i + 1} has invalid coordinates")
                }
                if (cleaned.isNotEmpty() &&
                    GeoMath.distanceMeters(
                        LatLng(cleaned.last().latitude, cleaned.last().longitude),
                        LatLng(p.latitude, p.longitude)
                    ) < MIN_SEGMENT_M
                ) {
                    continue // zero-length segment: drop the duplicate
                }
                cleaned.add(p)
            }
            if (cleaned.size < MIN_WAYPOINTS) {
                throw RouteValidationException("route is too short (points are too close together)")
            }
            return cleaned
        }
    }

    /** Effective waypoints after duplicate/zero-length filtering. */
    val waypoints: List<RoutePoint> = validate(rawWaypoints)

    private val segments = waypoints.size - 1
    private val segLengths: DoubleArray = DoubleArray(segments)
    val totalDistanceM: Double = computeSegmentLengths()

    /** Movement speed, m/s. May be changed live. */
    var speedMps: Double = initialSpeedMps.coerceIn(0.0, 50.0)

    /** Pause behaviour, may be changed live. */
    var pauseMs: Long = pauseMs.coerceIn(0L, 3_600_000L)
    var pauseEnabled: Boolean = pauseEnabled

    private var segIndex = 0
    private var distIntoSeg = 0.0
    private var traveledM = 0.0
    private var paused = false
    private var pauseRemainingMs = 0L
    private var completed = false

    private fun computeSegmentLengths(): Double {
        var total = 0.0
        for (i in 0 until segments) {
            val len = GeoMath.distanceMeters(
                LatLng(waypoints[i].latitude, waypoints[i].longitude),
                LatLng(waypoints[i + 1].latitude, waypoints[i + 1].longitude)
            )
            segLengths[i] = len
            total += len
        }
        return total
    }

    fun restart() {
        segIndex = 0
        distIntoSeg = 0.0
        traveledM = 0.0
        paused = false
        pauseRemainingMs = 0L
        completed = false
    }

    val isCompleted: Boolean get() = completed

    fun currentProgress(): RouteProgress = buildProgress()

    /**
     * Advance the simulation by [deltaMs] milliseconds. Returns one step; a tick
     * that crosses several segments or finishes a pause collapses into the final
     * observable state of that tick.
     */
    fun advance(deltaMs: Long): RouteStep {
        val delta = deltaMs.coerceAtLeast(0L)
        var remaining = delta.toDouble()

        while (remaining > 0.0 && !completed) {
            if (paused) {
                if (pauseRemainingMs > remaining) {
                    pauseRemainingMs -= remaining.toLong()
                    return buildStep(waypoint = waypoints[segIndex], paused = true)
                }
                remaining -= pauseRemainingMs
                pauseRemainingMs = 0L
                paused = false
                continue
            }

            if (speedMps <= 0.0) {
                // Stationary route: report current position with zero speed.
                return buildStep(waypoint = null, paused = false)
            }

            val segLen = segLengths[segIndex]
            val segRemaining = segLen - distIntoSeg
            val maxDist = speedMps * remaining / 1000.0

            if (maxDist < segRemaining) {
                distIntoSeg += maxDist
                traveledM += maxDist
                return buildStep(waypoint = null, paused = false)
            }

            // Finish the current segment.
            traveledM += segRemaining
            val consumedMs = segRemaining * 1000.0 / speedMps
            remaining -= consumedMs
            segIndex++
            distIntoSeg = 0.0

            if (segIndex == segments) {
                completed = true
                return buildStep(waypoint = null, paused = false)
            }

            // Arrived at intermediate waypoint → optional pause.
            if (pauseEnabled && pauseMs > 0L) {
                paused = true
                pauseRemainingMs = pauseMs
                if (pauseRemainingMs > remaining) {
                    pauseRemainingMs -= remaining.toLong()
                    return buildStep(waypoint = waypoints[segIndex], paused = true)
                }
                remaining -= pauseRemainingMs
                pauseRemainingMs = 0L
                paused = false
            }
        }

        return buildStep(waypoint = null, paused = false)
    }

    private fun buildStep(waypoint: RoutePoint?, paused: Boolean): RouteStep {
        val progress = buildProgress()
        val pos: LatLng
        val alt: Double?
        val speed: Double
        val bearing: Double

        if (completed) {
            pos = LatLng(waypoints.last().latitude, waypoints.last().longitude)
            alt = waypoints.last().altitude
            speed = 0.0
            bearing = if (segments > 0) {
                GeoMath.initialBearingDeg(
                    LatLng(waypoints[segments - 1].latitude, waypoints[segments - 1].longitude),
                    LatLng(waypoints[segments].latitude, waypoints[segments].longitude)
                )
            } else 0.0
        } else if (waypoint != null || paused) {
            val p = waypoint ?: waypoints[segIndex]
            pos = LatLng(p.latitude, p.longitude)
            alt = p.altitude
            speed = 0.0
            bearing = if (segIndex < segments) {
                GeoMath.initialBearingDeg(
                    LatLng(waypoints[segIndex].latitude, waypoints[segIndex].longitude),
                    LatLng(waypoints[segIndex + 1].latitude, waypoints[segIndex + 1].longitude)
                )
            } else 0.0
        } else {
            val a = waypoints[segIndex]
            val b = waypoints[segIndex + 1]
            val f = if (segLengths[segIndex] > 0.0) distIntoSeg / segLengths[segIndex] else 0.0
            pos = GeoMath.interpolate(
                LatLng(a.latitude, a.longitude),
                LatLng(b.latitude, b.longitude),
                f
            )
            alt = if (a.altitude != null && b.altitude != null) {
                a.altitude + (b.altitude - a.altitude) * f
            } else null
            speed = speedMps
            bearing = GeoMath.initialBearingDeg(
                LatLng(a.latitude, a.longitude),
                LatLng(b.latitude, b.longitude)
            )
        }

        return RouteStep(
            position = pos,
            altitudeM = alt,
            speedMps = speed,
            bearingDeg = bearing,
            paused = paused,
            remainingPauseMs = pauseRemainingMs,
            completed = completed,
            progress = progress
        )
    }

    private fun buildProgress(): RouteProgress {
        val fraction = if (totalDistanceM > 0.0) {
            (traveledM / totalDistanceM).coerceIn(0.0, 1.0).toFloat()
        } else 1f
        return RouteProgress(
            waypointIndex = segIndex.coerceAtMost(segments),
            fraction = fraction,
            distanceTraveledM = traveledM,
            totalDistanceM = totalDistanceM,
            pausedAtWaypoint = paused,
            remainingPauseMs = pauseRemainingMs,
            completed = completed
        )
    }
}
