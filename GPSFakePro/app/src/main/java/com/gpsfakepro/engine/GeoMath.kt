package com.gpsfakepro.engine

import kotlin.math.atan2
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geodesic helpers implemented in pure Kotlin so the route simulator can be
 * unit-tested on the JVM without Android stubs.
 */
object GeoMath {

    /** Mean Earth radius in metres (IUGG). */
    const val EARTH_RADIUS_M: Double = 6_371_000.0

    fun toRad(deg: Double): Double = deg * Math.PI / 180.0

    fun toDeg(rad: Double): Double = rad * 180.0 / Math.PI

    /** Great-circle distance between two points, in metres (haversine). */
    fun distanceMeters(a: LatLng, b: LatLng): Double {
        val dLat = toRad(b.latitude - a.latitude)
        val dLon = toRad(b.longitude - a.longitude)
        val lat1 = toRad(a.latitude)
        val lat2 = toRad(b.latitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2.0 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /**
     * Initial (forward) bearing from [a] to [b], degrees clockwise from north,
     * normalized to 0…360.
     */
    fun initialBearingDeg(a: LatLng, b: LatLng): Double {
        val lat1 = toRad(a.latitude)
        val lat2 = toRad(b.latitude)
        val dLon = toRad(b.longitude - a.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return normalizeBearing(toDeg(atan2(y, x)))
    }

    /** Normalize a bearing to 0…360. */
    fun normalizeBearing(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0

    /**
     * Linear interpolation between two points (suitable for short segments;
     * longitude is unwrapped to avoid the 180° seam).
     */
    fun interpolate(a: LatLng, b: LatLng, fraction: Double): LatLng {
        val f = fraction.coerceIn(0.0, 1.0)
        var dLon = b.longitude - a.longitude
        if (dLon > 180.0) dLon -= 360.0
        if (dLon < -180.0) dLon += 360.0
        return LatLng(
            a.latitude + (b.latitude - a.latitude) * f,
            a.longitude + dLon * f
        )
    }
}
