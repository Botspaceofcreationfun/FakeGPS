package com.gpsfakepro.service

import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import com.gpsfakepro.R
import com.gpsfakepro.engine.LatLng
import com.gpsfakepro.engine.MockLocationProvider
import com.gpsfakepro.engine.RoutePoint
import com.gpsfakepro.engine.RouteSimulator
import com.gpsfakepro.engine.RouteStep
import com.gpsfakepro.engine.SimMode
import com.gpsfakepro.engine.SimStatus
import com.gpsfakepro.engine.SimulationConfig
import com.gpsfakepro.engine.SimulationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the mock-location tick loop.
 *
 * It runs as a "specialUse" foreground service, so it keeps injecting locations
 * while the UI is backgrounded. Commands arrive as startService intents
 * ([SimulationController]); the current state is published through
 * [SimulationStateHolder] for the UI to observe without a binding.
 *
 * Clean stop: the test provider is disabled and removed, the notification is
 * dismissed and the service stops itself.
 */
class LocationSimulationService : Service() {

    companion object {
        const val ACTION_START_FIXED = "com.gpsfakepro.action.START_FIXED"
        const val ACTION_START_ROUTE = "com.gpsfakepro.action.START_ROUTE"
        const val ACTION_PAUSE = "com.gpsfakepro.action.PAUSE"
        const val ACTION_RESUME = "com.gpsfakepro.action.RESUME"
        const val ACTION_RESTART = "com.gpsfakepro.action.RESTART"
        const val ACTION_STOP = "com.gpsfakepro.action.STOP"
        const val ACTION_UPDATE_CONFIG = "com.gpsfakepro.action.UPDATE_CONFIG"

        const val EXTRA_LAT = "lat"
        const val EXTRA_LON = "lon"
        const val EXTRA_ROUTE_NAME = "route_name"
        const val EXTRA_WAYPOINT_LATS = "waypoint_lats"
        const val EXTRA_WAYPOINT_LONS = "waypoint_lons"
        const val EXTRA_WAYPOINT_ALTS = "waypoint_alts"
        const val EXTRA_INTERVAL_MS = "interval_ms"
        const val EXTRA_SPEED_MPS = "speed_mps"
        const val EXTRA_ACCURACY_M = "accuracy_m"
        const val EXTRA_ALTITUDE_M = "altitude_m"
        const val EXTRA_BEARING_DEG = "bearing_deg"
        const val EXTRA_PAUSE_ENABLED = "pause_enabled"
        const val EXTRA_PAUSE_MS = "pause_ms"
    }

    private data class TickOutput(
        val position: LatLng,
        val speedMps: Double,
        val bearingDeg: Double,
        val altitudeM: Double,
        val accuracyM: Float,
        val step: RouteStep?
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null
    private var startedForeground = false

    private lateinit var provider: MockLocationProvider
    private var simulator: RouteSimulator? = null
    private var fixedPoint: LatLng? = null
    private var config = SimulationConfig()

    override fun onCreate() {
        super.onCreate()
        provider = MockLocationProvider(this)
        NotificationHelper.createChannel(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FIXED -> {
                val lat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN)
                val lon = intent.getDoubleExtra(EXTRA_LON, Double.NaN)
                startFixed(lat, lon, readConfig(intent))
            }
            ACTION_START_ROUTE -> startRoute(
                intent.getStringExtra(EXTRA_ROUTE_NAME),
                readPoints(intent),
                readConfig(intent)
            )
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_RESTART -> restart()
            ACTION_STOP -> stopSimulation()
            ACTION_UPDATE_CONFIG -> updateConfig(readConfig(intent))
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        loopJob?.cancel()
        loopJob = null
        provider.disable()
        provider.remove()
        NotificationHelper.cancel(this)
        scope.cancel()
        super.onDestroy()
    }

    // ---- Command handlers -------------------------------------------------

    private fun startFixed(lat: Double, lon: Double, cfg: SimulationConfig) {
        if (!lat.isFinite() || !lon.isFinite() || !LatLng(lat, lon).isValid) {
            SimulationStateHolder.update {
                it.copy(status = SimStatus.IDLE, error = getString(R.string.sim_error_route_invalid, "coords"))
            }
            return
        }
        fixedPoint = LatLng(lat, lon)
        simulator = null
        config = cfg.sanitized()
        beginSimulation(SimMode.FIXED, routeName = null)
    }

    private fun startRoute(name: String?, points: List<RoutePoint>, cfg: SimulationConfig) {
        val routeName = name ?: getString(R.string.app_name)
        simulator = try {
            RouteSimulator(
                rawWaypoints = points,
                initialSpeedMps = cfg.speedMps,
                pauseMs = cfg.pauseMs,
                pauseEnabled = cfg.pauseAtWaypoints
            )
        } catch (e: RouteSimulator.RouteValidationException) {
            SimulationStateHolder.update {
                it.copy(status = SimStatus.IDLE, error = e.message)
            }
            return
        }
        fixedPoint = null
        config = cfg.sanitized()
        beginSimulation(SimMode.ROUTE, routeName)
    }

    private fun beginSimulation(mode: SimMode, routeName: String?) {
        when (val result = provider.ensureEnabled()) {
            is MockLocationProvider.Result.Ready -> {
                val sim = simulator
                SimulationStateHolder.update {
                    it.copy(
                        status = SimStatus.RUNNING,
                        mode = mode,
                        routeName = routeName,
                        config = config,
                        routeWaypoints = sim?.waypoints?.map { p -> LatLng(p.latitude, p.longitude) },
                        error = null,
                        routeProgress = null
                    )
                }
                ensureForeground()
                startLoop()
            }
            MockLocationProvider.Result.MockNotAllowed -> {
                SimulationStateHolder.update {
                    it.copy(status = SimStatus.IDLE, error = getString(R.string.sim_error_mock_not_allowed))
                }
                ensureForeground()
            }
            is MockLocationProvider.Result.Unavailable -> {
                SimulationStateHolder.update {
                    it.copy(status = SimStatus.IDLE, error = getString(R.string.sim_error_mock_service))
                }
                ensureForeground()
            }
        }
        NotificationHelper.update(this, SimulationStateHolder.current)
    }

    private fun pause() {
        SimulationStateHolder.update { it.copy(status = SimStatus.PAUSED, error = null) }
        NotificationHelper.update(this, SimulationStateHolder.current)
    }

    private fun resume() {
        val st = SimulationStateHolder.current
        if (st.status == SimStatus.PAUSED) {
            SimulationStateHolder.update { it.copy(status = SimStatus.RUNNING, error = null) }
            startLoop()
        }
        NotificationHelper.update(this, SimulationStateHolder.current)
    }

    private fun restart() {
        simulator?.restart()
        SimulationStateHolder.update {
            it.copy(status = SimStatus.RUNNING, routeProgress = null, error = null)
        }
        startLoop()
        NotificationHelper.update(this, SimulationStateHolder.current)
    }

    private fun updateConfig(cfg: SimulationConfig) {
        config = cfg.sanitized()
        simulator?.let {
            it.speedMps = config.speedMps
            it.pauseMs = config.pauseMs
            it.pauseEnabled = config.pauseAtWaypoints
        }
        SimulationStateHolder.update { it.copy(config = config, error = null) }
        startLoop()
        NotificationHelper.update(this, SimulationStateHolder.current)
    }

    private fun stopSimulation() {
        loopJob?.cancel()
        loopJob = null
        provider.disable()
        provider.remove()
        SimulationStateHolder.update { st ->
            st.copy(
                status = SimStatus.IDLE,
                routeProgress = null,
                error = null
                // position is kept so the map still shows the last simulated point
            )
        }
        NotificationHelper.cancel(this)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        startedForeground = false
        stopSelf()
    }

    // ---- Engine loop ------------------------------------------------------

    private fun ensureForeground() {
        if (startedForeground) return
        startForeground(
            NotificationHelper.NOTIFICATION_ID,
            NotificationHelper.build(this, SimulationStateHolder.current)
        )
        startedForeground = true
    }

    private fun startLoop() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            while (isActive) {
                val st = SimulationStateHolder.current
                if (st.status == SimStatus.RUNNING || st.status == SimStatus.COMPLETED) {
                    tick(st)
                }
                delay(st.config.intervalMs.coerceIn(200L, 60_000L))
            }
        }
    }

    private fun tick(st: SimulationState) {
        val output = when (st.mode) {
            SimMode.FIXED -> fixedPoint?.let { p ->
                TickOutput(p, config.speedMps, config.bearingDeg, config.altitudeM, config.accuracyM, null)
            }
            SimMode.ROUTE -> simulator?.let { sim ->
                val step = sim.advance(st.config.intervalMs)
                TickOutput(
                    step.position,
                    step.speedMps,
                    step.bearingDeg,
                    step.altitudeM ?: config.altitudeM,
                    config.accuracyM,
                    step
                )
            }
        } ?: return

        val location = Location(MockLocationProvider.PROVIDER_NAME).apply {
            latitude = output.position.latitude
            longitude = output.position.longitude
            altitude = output.altitudeM
            speed = output.speedMps.toFloat()
            bearing = output.bearingDeg.toFloat()
            accuracy = output.accuracyM
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }

        try {
            provider.push(location)
            SimulationStateHolder.update { current ->
                current.copy(
                    position = output.position,
                    speedMps = output.speedMps,
                    bearingDeg = output.bearingDeg,
                    altitudeM = output.altitudeM,
                    accuracyM = output.accuracyM,
                    routeProgress = output.step?.progress,
                    status = if (output.step?.completed == true) SimStatus.COMPLETED else current.status,
                    error = null
                )
            }
        } catch (e: SecurityException) {
            // The mock-location grant was lost while running (e.g. developer
            // options changed). Surface it and stop injecting.
            SimulationStateHolder.update { current ->
                current.copy(error = getString(R.string.sim_error_mock_not_allowed))
            }
            loopJob?.cancel()
        } catch (e: Exception) {
            SimulationStateHolder.update { current ->
                current.copy(error = getString(R.string.sim_error_generic, e.message ?: "unknown"))
            }
            loopJob?.cancel()
        }
        NotificationHelper.update(this, SimulationStateHolder.current)
    }

    private fun readConfig(intent: Intent): SimulationConfig =
        SimulationConfig(
            intervalMs = intent.getLongExtra(EXTRA_INTERVAL_MS, config.intervalMs),
            speedMps = intent.getDoubleExtra(EXTRA_SPEED_MPS, config.speedMps),
            accuracyM = intent.getFloatExtra(EXTRA_ACCURACY_M, config.accuracyM),
            altitudeM = intent.getDoubleExtra(EXTRA_ALTITUDE_M, config.altitudeM),
            bearingDeg = intent.getDoubleExtra(EXTRA_BEARING_DEG, config.bearingDeg),
            pauseAtWaypoints = intent.getBooleanExtra(EXTRA_PAUSE_ENABLED, config.pauseAtWaypoints),
            pauseMs = intent.getLongExtra(EXTRA_PAUSE_MS, config.pauseMs)
        ).sanitized()

    private fun readPoints(intent: Intent): List<RoutePoint> {
        val lats = intent.getDoubleArrayExtra(EXTRA_WAYPOINT_LATS) ?: return emptyList()
        val lons = intent.getDoubleArrayExtra(EXTRA_WAYPOINT_LONS) ?: return emptyList()
        val alts = intent.getDoubleArrayExtra(EXTRA_WAYPOINT_ALTS)
        val size = minOf(lats.size, lons.size, alts?.size ?: lats.size)
        return (0 until size).map { i ->
            val alt = alts?.getOrNull(i)?.takeIf { it.isFinite() }
            RoutePoint(lats[i], lons[i], alt)
        }
    }
}
