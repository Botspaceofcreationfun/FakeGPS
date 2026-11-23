package com.gpsfakepro.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.gpsfakepro.engine.RoutePoint
import com.gpsfakepro.engine.SimulationConfig

/**
 * Builds the intents that drive [LocationSimulationService]. All commands are
 * fire-and-forget startService calls with the payload packed into extras, so the
 * UI never needs to hold a binding — the service keeps running on its own.
 */
object SimulationController {

    fun startFixed(context: Context, lat: Double, lon: Double, config: SimulationConfig) {
        val intent = base(context, LocationSimulationService.ACTION_START_FIXED)
            .putExtra(LocationSimulationService.EXTRA_LAT, lat)
            .putExtra(LocationSimulationService.EXTRA_LON, lon)
            .putConfig(config)
        ContextCompat.startForegroundService(context, intent)
    }

    fun startRoute(
        context: Context,
        routeName: String,
        waypoints: List<RoutePoint>,
        config: SimulationConfig
    ) {
        val lats = DoubleArray(waypoints.size) { waypoints[it].latitude }
        val lons = DoubleArray(waypoints.size) { waypoints[it].longitude }
        val alts = DoubleArray(waypoints.size) { waypoints[it].altitude ?: Double.NaN }
        val intent = base(context, LocationSimulationService.ACTION_START_ROUTE)
            .putExtra(LocationSimulationService.EXTRA_ROUTE_NAME, routeName)
            .putExtra(LocationSimulationService.EXTRA_WAYPOINT_LATS, lats)
            .putExtra(LocationSimulationService.EXTRA_WAYPOINT_LONS, lons)
            .putExtra(LocationSimulationService.EXTRA_WAYPOINT_ALTS, alts)
            .putConfig(config)
        ContextCompat.startForegroundService(context, intent)
    }

    fun pause(context: Context) = send(context, LocationSimulationService.ACTION_PAUSE)

    fun resume(context: Context) = send(context, LocationSimulationService.ACTION_RESUME)

    fun restart(context: Context) = send(context, LocationSimulationService.ACTION_RESTART)

    fun stop(context: Context) = send(context, LocationSimulationService.ACTION_STOP)

    fun updateConfig(context: Context, config: SimulationConfig) {
        val intent = base(context, LocationSimulationService.ACTION_UPDATE_CONFIG)
            .putConfig(config)
        ContextCompat.startForegroundService(context, intent)
    }

    private fun send(context: Context, action: String) {
        ContextCompat.startForegroundService(context, base(context, action))
    }

    private fun base(context: Context, action: String): Intent =
        Intent(context, LocationSimulationService::class.java).setAction(action)

    private fun Intent.putConfig(config: SimulationConfig): Intent = apply {
        putExtra(LocationSimulationService.EXTRA_INTERVAL_MS, config.intervalMs)
        putExtra(LocationSimulationService.EXTRA_SPEED_MPS, config.speedMps)
        putExtra(LocationSimulationService.EXTRA_ACCURACY_M, config.accuracyM)
        putExtra(LocationSimulationService.EXTRA_ALTITUDE_M, config.altitudeM)
        putExtra(LocationSimulationService.EXTRA_BEARING_DEG, config.bearingDeg)
        putExtra(LocationSimulationService.EXTRA_PAUSE_ENABLED, config.pauseAtWaypoints)
        putExtra(LocationSimulationService.EXTRA_PAUSE_MS, config.pauseMs)
    }
}
