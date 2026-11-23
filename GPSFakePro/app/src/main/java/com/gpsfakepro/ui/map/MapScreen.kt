package com.gpsfakepro.ui.map

import android.content.Context
import android.graphics.Color
import android.location.Geocoder
import android.net.ConnectivityManager
import android.net.Network
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpsfakepro.R
import com.gpsfakepro.data.AppContainer
import com.gpsfakepro.data.SettingsRepository
import com.gpsfakepro.engine.LatLng
import com.gpsfakepro.engine.SimStatus
import com.gpsfakepro.engine.SimulationConfig
import com.gpsfakepro.engine.SimulationState
import com.gpsfakepro.service.SimulationController
import com.gpsfakepro.service.SimulationStateHolder
import com.gpsfakepro.ui.SelectionHolder
import com.gpsfakepro.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import java.util.Locale

/**
 * Main screen: full-screen OpenStreetMap with tap-to-select, search, manual
 * coordinate entry, the simulated-position marker and the simulation control
 * panel.
 */
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val simState by SimulationStateHolder.state.collectAsStateWithLifecycle()
    val selection by SelectionHolder.selection.collectAsStateWithLifecycle()
    val defaults by SettingsRepository.observeDefaults(context).collectAsStateWithLifecycle(
        initialValue = SettingsRepository.Defaults(SimulationConfig(), "system")
    )
    val online by rememberOnlineStatus(context)

    var map by remember { mutableStateOf<MapView?>(null) }
    var showManualDialog by rememberSaveable { mutableStateOf(false) }
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var panelExpanded by rememberSaveable { mutableStateOf(false) }

    val active = simState.status != SimStatus.IDLE
    val effectiveConfig = if (active) simState.config else defaults.config

    LaunchedEffect(simState.status) {
        if (simState.status != SimStatus.IDLE) panelExpanded = true
    }

    LaunchedEffect(message) {
        if (message != null) {
            delay(3000)
            message = null
        }
    }

    fun showMessage(text: String) {
        message = text
    }

    fun onConfigChanged(cfg: SimulationConfig) {
        if (active) {
            SimulationController.updateConfig(context, cfg)
        } else {
            scope.launch { SettingsRepository.updateDefaults(context, cfg) }
        }
    }

    fun startFixed() {
        val sel = SelectionHolder.selection.value
        if (sel == null) {
            showMessage(context.getString(R.string.map_no_selection))
            return
        }
        SimulationController.startFixed(context, sel.latitude, sel.longitude, effectiveConfig)
        scope.launch { AppContainer.locations.addRecent(sel.latitude, sel.longitude) }
        panelExpanded = true
    }

    fun searchPlace(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        if (!Geocoder.isPresent()) {
            showMessage(context.getString(R.string.map_search_unavailable))
            return
        }
        scope.launch {
            searching = true
            try {
                val results = withContext(Dispatchers.IO) {
                    Geocoder(context, Locale.getDefault()).getFromLocationName(q, 1)
                }
                if (results.isNullOrEmpty()) {
                    showMessage(context.getString(R.string.map_search_no_results, q))
                } else {
                    val r = results.first()
                    SelectionHolder.set(r.latitude, r.longitude)
                    map?.controller?.animateTo(GeoPoint(r.latitude, r.longitude))
                    scope.launch { AppContainer.locations.addRecent(r.latitude, r.longitude) }
                    searchQuery = ""
                }
            } catch (e: Exception) {
                showMessage(context.getString(R.string.map_search_error, e.message ?: "unknown"))
            } finally {
                searching = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Search bar
            Surface(tonalElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.map_search_hint)) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
                    )
                    Spacer(Modifier.width(8.dp))
                    if (searching) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                    } else {
                        TextButton(onClick = { searchPlace(searchQuery) }) {
                            Text(stringResource(R.string.map_search_go))
                        }
                    }
                }
            }

            // Map + overlays
            Box(Modifier.weight(1f).fillMaxWidth()) {
                OsmdroidMapHost(
                    selection = selection,
                    simState = simState,
                    onMapTap = { p ->
                        SelectionHolder.set(p)
                        scope.launch { AppContainer.locations.addRecent(p.latitude, p.longitude) }
                    },
                    onMapReady = { map = it }
                )

                if (!online) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                    ) {
                        Text(
                            stringResource(R.string.map_tiles_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                message?.let { msg ->
                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
                    ) {
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Column(
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MapControlButton(Icons.Filled.ZoomIn, stringResource(R.string.map_zoom_in)) {
                        map?.controller?.zoomIn()
                    }
                    MapControlButton(Icons.Filled.ZoomOut, stringResource(R.string.map_zoom_out)) {
                        map?.controller?.zoomOut()
                    }
                    if (selection != null) {
                        MapControlButton(
                            Icons.Filled.CenterFocusStrong,
                            stringResource(R.string.map_center)
                        ) {
                            val s = SelectionHolder.selection.value ?: return@MapControlButton
                            map?.controller?.animateTo(GeoPoint(s.latitude, s.longitude))
                        }
                    }
                    val simPos = simState.position
                    if (simPos != null) {
                        MapControlButton(
                            Icons.Filled.MyLocation,
                            stringResource(R.string.map_sim_position)
                        ) {
                            map?.controller?.animateTo(GeoPoint(simPos.latitude, simPos.longitude))
                        }
                    }
                    MapControlButton(
                        Icons.Filled.AddLocationAlt,
                        stringResource(R.string.map_manual_title)
                    ) {
                        showManualDialog = true
                    }
                }

                selection?.let { sel ->
                    if (!active) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(12.dp),
                            tonalElevation = 3.dp,
                            modifier = Modifier.align(Alignment.BottomCenter)
                                .padding(bottom = 176.dp, start = 8.dp, end = 8.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.map_selected_title),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        Formatters.coords(sel.latitude, sel.longitude),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        stringResource(
                                            R.string.map_selected_accuracy,
                                            Formatters.speed(effectiveConfig.accuracyM.toDouble())
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { showSaveDialog = true }) {
                                    Icon(Icons.Filled.Save, stringResource(R.string.map_save_location))
                                }
                                IconButton(onClick = { SelectionHolder.clear() }) {
                                    Icon(Icons.Filled.Close, stringResource(R.string.map_clear_selection))
                                }
                            }
                        }
                    }
                }

                SimulationPanel(
                    simState = simState,
                    config = effectiveConfig,
                    active = active,
                    expanded = panelExpanded,
                    onExpandedChange = { panelExpanded = it },
                    onConfigChanged = ::onConfigChanged,
                    onStartFixed = ::startFixed,
                    onPause = { SimulationController.pause(context) },
                    onResume = { SimulationController.resume(context) },
                    onRestart = { SimulationController.restart(context) },
                    onStop = {
                        SimulationController.stop(context)
                        panelExpanded = false
                    }
                )
            }
        }
    }

    if (showManualDialog) {
        ManualCoordDialog(
            onDismiss = { showManualDialog = false },
            onConfirm = { lat, lon, alt ->
                showManualDialog = false
                SelectionHolder.set(lat, lon)
                map?.controller?.animateTo(GeoPoint(lat, lon))
                scope.launch { AppContainer.locations.addRecent(lat, lon) }
            }
        )
    }

    if (showSaveDialog) {
        val sel = selection
        if (sel != null) {
            SaveLocationDialog(
                initialName = "",
                onDismiss = { showSaveDialog = false },
                onSave = { name ->
                    showSaveDialog = false
                    scope.launch {
                        AppContainer.locations.addLocation(
                            name, sel.latitude, sel.longitude, null
                        )
                        showMessage(context.getString(R.string.map_saved_confirmation, name))
                    }
                }
            )
        } else {
            showSaveDialog = false
            showMessage(context.getString(R.string.map_no_selection))
        }
    }
}

@Composable
private fun MapControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        shape = CircleShape
    ) {
        Icon(icon, label, modifier = Modifier.size(20.dp))
    }
}

/** Observes connectivity so the map can show an honest offline fallback banner. */
@Composable
private fun rememberOnlineStatus(context: Context): State<Boolean> {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return produceState(initialValue = cm.activeNetwork != null) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                value = true
            }

            override fun onLost(network: Network) {
                value = false
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        awaitDispose {
            runCatching { cm.unregisterNetworkCallback(callback) }
        }
    }
}

/** Hosts the osmdroid MapView and keeps its overlays in sync with app state. */
@Composable
fun OsmdroidMapHost(
    selection: LatLng?,
    simState: SimulationState,
    onMapTap: (LatLng) -> Unit,
    onMapReady: (MapView) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }

    AndroidView(
        factory = { ctx ->
            val map = MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                setBuiltInZoomControls(false)
                setFlingEnabled(true)
                minZoomLevel = 3.0
                maxZoomLevel = 19.5
                controller.setZoom(16.0)
                controller.setCenter(GeoPoint(51.5074, -0.1278)) // default: London

                overlays.add(
                    MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            if (p == null) return false
                            onMapTap(LatLng(p.latitude, p.longitude))
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint?): Boolean = false
                    })
                )
            }
            onMapReady(map)
            map
        },
        update = { map ->
            syncOverlays(map, selection, simState, context)
        },
        modifier = Modifier.fillMaxSize()
    )

    // osmdroid lifecycle: onResume/onPause keep tile loading and memory tidy.
    DisposableEffect(lifecycleOwner, mapView) {
        val map = mapView
        if (map == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> map.onResume()
                    Lifecycle.Event.ON_PAUSE -> map.onPause()
                    else -> Unit
                }
            }
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                map.onResume()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                map.onDetach()
            }
        }
    }
}

/**
 * Rebuilds the dynamic overlays (route lines, waypoint markers, selection pin,
 * simulated-position arrow) from the current state. Cheap at 1 Hz updates.
 */
private fun syncOverlays(
    map: MapView,
    selection: LatLng?,
    simState: SimulationState,
    context: Context
) {
    val dynamic = mutableListOf<Overlay>()

    // Full route line
    simState.routeWaypoints?.let { pts ->
        if (pts.size >= 2) {
            dynamic += Polyline(map).apply {
                setPoints(pts.map { GeoPoint(it.latitude, it.longitude) })
                setColor(Color.parseColor("#757575"))
                width = 7f
            }
        }
    }

    // Traveled part of the route
    val pos = simState.position
    val rp = simState.routeWaypoints
    if (pos != null && rp != null && rp.size >= 2) {
        val progress = simState.routeProgress
        val traveledPts = ArrayList<GeoPoint>()
        val idx = (progress?.waypointIndex ?: 0).coerceIn(0, rp.size - 1)
        for (i in 0..idx) traveledPts.add(GeoPoint(rp[i].latitude, rp[i].longitude))
        traveledPts.add(GeoPoint(pos.latitude, pos.longitude))
        dynamic += Polyline(map).apply {
            setPoints(traveledPts)
            setColor(Color.parseColor("#1565C0"))
            width = 8f
        }
    }

    // Waypoint dots
    simState.routeWaypoints?.forEach { p ->
        dynamic += waypointMarker(map, p)
    }

    // Selection pin
    selection?.let { sel ->
        dynamic += Marker(map).apply {
            position = GeoPoint(sel.latitude, sel.longitude)
            icon = ContextCompat.getDrawable(context, R.drawable.ic_location_pin)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            setInfoWindow(null)
            title = context.getString(R.string.cd_selection_marker)
        }
    }

    // Simulated-position arrow (rotated by bearing)
    pos?.let { p ->
        dynamic += Marker(map).apply {
            position = GeoPoint(p.latitude, p.longitude)
            icon = ContextCompat.getDrawable(context, R.drawable.ic_current_location)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setRotation(simState.bearingDeg.toFloat())
            setInfoWindow(null)
            title = context.getString(R.string.cd_sim_marker)
        }
    }

    // Replace all overlays after the tap-event overlay (index 0).
    while (map.overlays.size > 1) {
        map.overlays.removeAt(map.overlays.size - 1)
    }
    map.overlays.addAll(dynamic)
    map.invalidate()
}

private fun waypointMarker(map: MapView, p: LatLng): Marker = Marker(map).apply {
    position = GeoPoint(p.latitude, p.longitude)
    icon = ContextCompat.getDrawable(map.context, R.drawable.ic_waypoint)
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    setInfoWindow(null)
}
