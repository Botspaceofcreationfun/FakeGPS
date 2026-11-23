package com.gpsfakepro.engine

/**
 * Pure, Android-free model types shared by the simulation engine, service, GPX
 * layer and UI. Keeping them dependency-free makes the route logic unit-testable
 * on the JVM.
 */

/** A geographic point. Latitude: −90…90, longitude: −180…180. */
data class LatLng(val latitude: Double, val longitude: Double) {
    val isValid: Boolean
        get() = latitude in -90.0..90.0 && longitude in -180.0..180.0
}

/**
 * A route waypoint. [altitude] is optional; when null the configured default
 * altitude is used while simulating.
 */
data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null
) {
    val isValid: Boolean
        get() = latitude in -90.0..90.0 && longitude in -180.0..180.0
}

enum class SimStatus {
    IDLE, RUNNING, PAUSED, COMPLETED
}

enum class SimMode {
    FIXED, ROUTE
}

/**
 * Runtime configuration of a simulation. All values are user-adjustable and can
 * be changed live while a simulation is running.
 */
data class SimulationConfig(
    val intervalMs: Long = 1000L,
    val speedMps: Double = 5.0,
    val accuracyM: Float = 3f,
    val altitudeM: Double = 0.0,
    val bearingDeg: Double = 0.0,
    val pauseAtWaypoints: Boolean = false,
    val pauseMs: Long = 0L
) {
    fun sanitized(): SimulationConfig = copy(
        intervalMs = intervalMs.coerceIn(200L, 60_000L),
        speedMps = speedMps.coerceIn(0.0, 50.0),
        accuracyM = accuracyM.coerceIn(1f, 100f),
        altitudeM = altitudeM.coerceIn(-500.0, 9000.0),
        bearingDeg = ((bearingDeg % 360.0) + 360.0) % 360.0,
        pauseMs = pauseMs.coerceIn(0L, 3_600_000L)
    )
}

/** Progress of a route simulation. */
data class RouteProgress(
    val waypointIndex: Int,
    val fraction: Float,
    val distanceTraveledM: Double,
    val totalDistanceM: Double,
    val pausedAtWaypoint: Boolean,
    val remainingPauseMs: Long,
    val completed: Boolean
)

/** The full observable state of the simulator, shared by service and UI. */
data class SimulationState(
    val status: SimStatus = SimStatus.IDLE,
    val mode: SimMode = SimMode.FIXED,
    val position: LatLng? = null,
    val speedMps: Double = 0.0,
    val bearingDeg: Double = 0.0,
    val altitudeM: Double = 0.0,
    val accuracyM: Float = 3f,
    val config: SimulationConfig = SimulationConfig(),
    val routeProgress: RouteProgress? = null,
    val routeName: String? = null,
    val routeWaypoints: List<LatLng>? = null,
    val error: String? = null
)

/** Result of a single route simulation tick. */
data class RouteStep(
    val position: LatLng,
    val altitudeM: Double?,
    val speedMps: Double,
    val bearingDeg: Double,
    val paused: Boolean,
    val remainingPauseMs: Long,
    val completed: Boolean,
    val progress: RouteProgress
)
