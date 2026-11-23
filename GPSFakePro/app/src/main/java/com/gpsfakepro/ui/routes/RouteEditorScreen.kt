package com.gpsfakepro.ui.routes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gpsfakepro.R
import com.gpsfakepro.data.AppContainer
import com.gpsfakepro.engine.RoutePoint
import com.gpsfakepro.engine.SimulationConfig
import com.gpsfakepro.service.SimulationController
import com.gpsfakepro.ui.SelectionHolder
import com.gpsfakepro.ui.components.MessageChip
import com.gpsfakepro.ui.map.ManualCoordDialog
import com.gpsfakepro.util.Formatters
import kotlinx.coroutines.launch

/**
 * Route editor: name, ordered waypoints (add/remove/reorder/edit), movement
 * speed, waypoint pauses, save and start. Editing an existing route updates it
 * in place; a new route is created with routeId = 0.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteEditorScreen(
    routeId: Long,
    onDone: () -> Unit,
    onStart: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by rememberSaveable { mutableStateOf("") }
    val waypoints = remember { mutableStateListOf<RoutePoint>() }
    var speedMps by rememberSaveable { mutableStateOf(5.0f) }
    var pauseEnabled by rememberSaveable { mutableStateOf(false) }
    var pauseSec by rememberSaveable { mutableStateOf(5f) }

    var message by remember { mutableStateOf<String?>(null) }
    var showManualAdd by rememberSaveable { mutableStateOf(false) }
    var editingIndex by remember { mutableIntStateOf(-1) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (routeId > 0L) {
            val full = AppContainer.locations.routeWithWaypoints(routeId)
            if (full != null) {
                name = full.route.name
                speedMps = full.route.speedMps.toFloat()
                pauseEnabled = full.route.pauseEnabled
                pauseSec = (full.route.pauseMs / 1000f).coerceIn(1f, 120f)
                waypoints.clear()
                waypoints.addAll(full.waypoints.map {
                    RoutePoint(it.latitude, it.longitude, it.altitude)
                })
            }
        }
    }

    fun validate(): List<RoutePoint>? {
        if (name.trim().isEmpty()) {
            message = context.getString(R.string.route_name_required)
            return null
        }
        if (waypoints.size < 2) {
            message = context.getString(R.string.route_import_too_few)
            return null
        }
        val invalidIndex = waypoints.indexOfFirst { !it.isValid }
        if (invalidIndex >= 0) {
            message = context.getString(R.string.gpx_error_invalid_coords, invalidIndex + 1)
            return null
        }
        return waypoints.toList()
    }

    fun savePoints(points: List<RoutePoint>, then: (Long) -> Unit) {
        if (saving) return
        saving = true
        scope.launch {
            val id = AppContainer.locations.saveRoute(
                routeId = routeId,
                name = name.trim(),
                waypoints = points,
                speedMps = speedMps.toDouble(),
                pauseEnabled = pauseEnabled,
                pauseMs = (pauseSec * 1000).toLong()
            )
            saving = false
            then(id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.route_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_cancel))
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val points = validate() ?: return@TextButton
                        savePoints(points) { if (it > 0L) onDone() }
                    }) {
                        Icon(Icons.Filled.Save, null, Modifier.padding(end = 4.dp))
                        Text(stringResource(R.string.route_save))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.route_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.route_waypoints_label, waypoints.size),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(onClick = {
                    val sel = SelectionHolder.selection.value
                    if (sel == null) {
                        message = context.getString(R.string.route_needs_selection)
                    } else {
                        waypoints.add(RoutePoint(sel.latitude, sel.longitude, null))
                        message = context.getString(R.string.map_waypoint_added)
                    }
                }) {
                    Icon(Icons.Filled.MyLocation, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.route_add_from_selection))
                }
                Spacer(Modifier.width(6.dp))
                FilledTonalButton(onClick = { showManualAdd = true }) {
                    Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.route_add_manual))
                }
            }

            if (waypoints.isEmpty()) {
                Text(
                    stringResource(R.string.route_no_waypoints),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                waypoints.forEachIndexed { index, point ->
                    WaypointRow(
                        index = index,
                        point = point,
                        canMoveUp = index > 0,
                        canMoveDown = index < waypoints.size - 1,
                        onMoveUp = {
                            val tmp = waypoints[index]
                            waypoints[index] = waypoints[index - 1]
                            waypoints[index - 1] = tmp
                        },
                        onMoveDown = {
                            val tmp = waypoints[index]
                            waypoints[index] = waypoints[index + 1]
                            waypoints[index + 1] = tmp
                        },
                        onEdit = { editingIndex = index },
                        onDelete = { waypoints.removeAt(index) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                stringResource(R.string.route_speed_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(
                        R.string.sim_speed_value,
                        Formatters.speed(speedMps.toDouble()),
                        Formatters.speedKmh(speedMps.toDouble())
                    ),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Slider(
                value = speedMps.coerceIn(0f, 30f),
                onValueChange = { speedMps = it },
                valueRange = 0f..30f,
                modifier = Modifier.fillMaxWidth()
            )

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.route_pause_at_waypoints),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = pauseEnabled, onCheckedChange = { pauseEnabled = it })
            }
            if (pauseEnabled) {
                Text(
                    stringResource(R.string.route_pause_duration_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(Formatters.seconds((pauseSec * 1000).toLong()))
                }
                Slider(
                    value = pauseSec.coerceIn(1f, 120f),
                    onValueChange = { pauseSec = it },
                    valueRange = 1f..120f,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val points = validate() ?: return@Button
                    savePoints(points) { id ->
                        if (id > 0L) {
                            SimulationController.startRoute(
                                context,
                                name.trim(),
                                points,
                                SimulationConfig(
                                    speedMps = speedMps.toDouble(),
                                    pauseAtWaypoints = pauseEnabled,
                                    pauseMs = (pauseSec * 1000).toLong()
                                )
                            )
                            onStart()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving
            ) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.route_start))
                } else {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.route_start))
                }
            }

            Spacer(Modifier.height(24.dp))

            MessageChip(message, Modifier.fillMaxWidth())
        }
    }

    if (showManualAdd) {
        ManualCoordDialog(
            onDismiss = { showManualAdd = false },
            onConfirm = { lat, lon, alt ->
                showManualAdd = false
                waypoints.add(RoutePoint(lat, lon, alt))
            }
        )
    }

    if (editingIndex >= 0 && editingIndex < waypoints.size) {
        ManualCoordDialog(
            onDismiss = { editingIndex = -1 },
            onConfirm = { lat, lon, alt ->
                waypoints[editingIndex] = RoutePoint(lat, lon, alt)
                editingIndex = -1
            }
        )
    }
}

@Composable
private fun WaypointRow(
    index: Int,
    point: RoutePoint,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(28.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    Formatters.coords(point.latitude, point.longitude),
                    style = MaterialTheme.typography.bodySmall
                )
                point.altitude?.let {
                    Text(
                        "${Formatters.speed(it)} m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Filled.ArrowUpward, stringResource(R.string.route_move_up))
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Filled.ArrowDownward, stringResource(R.string.route_move_down))
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, stringResource(R.string.route_edit_waypoint))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, stringResource(R.string.route_delete_waypoint))
            }
        }
    }
}
