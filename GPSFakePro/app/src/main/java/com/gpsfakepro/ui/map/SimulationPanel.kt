package com.gpsfakepro.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gpsfakepro.R
import com.gpsfakepro.engine.SimMode
import com.gpsfakepro.engine.SimStatus
import com.gpsfakepro.engine.SimulationConfig
import com.gpsfakepro.engine.SimulationState
import com.gpsfakepro.util.Formatters
import kotlin.math.roundToInt

/**
 * Bottom control panel: status, start/pause/resume/restart/stop, live route
 * progress and live-adjustable reporting parameters (speed, bearing, altitude,
 * accuracy, update interval, waypoint pauses).
 */
@Composable
fun SimulationPanel(
    simState: SimulationState,
    config: SimulationConfig,
    active: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onConfigChanged: (SimulationConfig) -> Unit,
    onStartFixed: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Header row (always visible) — contains the prominent Stop control.
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(simState.status)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        statusLabel(simState),
                        style = MaterialTheme.typography.titleSmall,
                        color = statusColor(simState.status)
                    )
                    Text(
                        statusDetail(simState),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (active) {
                    IconButton(onClick = onStop) {
                        Icon(
                            Icons.Filled.Stop,
                            stringResource(R.string.sim_stop),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                IconButton(onClick = { onExpandedChange(!expanded) }) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null
                    )
                }
            }

            if (expanded) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 4.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    simState.error?.let { error ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Text(
                                error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    when {
                        active && simState.mode == SimMode.ROUTE -> RouteControls(
                            simState = simState,
                            config = config,
                            onConfigChanged = onConfigChanged,
                            onPause = onPause,
                            onResume = onResume,
                            onRestart = onRestart
                        )
                        else -> FixedControls(
                            simState = simState,
                            config = config,
                            onConfigChanged = onConfigChanged,
                            onStartFixed = onStartFixed,
                            showStartButton = !active
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteControls(
    simState: SimulationState,
    config: SimulationConfig,
    onConfigChanged: (SimulationConfig) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit
) {
    val progress = simState.routeProgress
    val context = androidx.compose.ui.platform.LocalContext.current

    if (progress != null && progress.totalDistanceM > 0.0) {
        val pct = (progress.fraction * 100).roundToInt()
        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        )
        Text(
            stringResource(R.string.sim_progress, pct),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            stringResource(
                R.string.sim_distance,
                Formatters.distance(progress.distanceTraveledM),
                Formatters.distance(progress.totalDistanceM)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (progress.pausedAtWaypoint && !progress.completed) {
            Text(
                stringResource(
                    R.string.sim_paused_at_waypoint_fmt,
                    progress.waypointIndex + 1,
                    Formatters.seconds(progress.remainingPauseMs)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        if (simState.status == SimStatus.COMPLETED) {
            Text(
                stringResource(R.string.sim_completed_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    ControlRow {
        if (simState.status == SimStatus.PAUSED) {
            Button(onClick = onResume) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.sim_resume))
            }
        } else {
            Button(onClick = onPause) {
                Icon(Icons.Filled.Pause, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.sim_pause))
            }
        }
        FilledTonalButton(onClick = onRestart) {
            Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.sim_restart))
        }
    }

    SpeedSlider(config.speedMps) { onConfigChanged(config.copy(speedMps = it)) }
    IntervalChips(config.intervalMs) { onConfigChanged(config.copy(intervalMs = it)) }

    // Waypoint pauses (live)
    val ctx = context
    SwitchRow(
        checked = config.pauseAtWaypoints,
        label = stringResource(R.string.sim_pause_at_waypoints),
        onChecked = { onConfigChanged(config.copy(pauseAtWaypoints = it)) }
    )
    if (config.pauseAtWaypoints) {
        PauseDurationSlider(config.pauseMs) { onConfigChanged(config.copy(pauseMs = it)) }
    }
}

@Composable
private fun FixedControls(
    simState: SimulationState,
    config: SimulationConfig,
    onConfigChanged: (SimulationConfig) -> Unit,
    onStartFixed: () -> Unit,
    showStartButton: Boolean
) {
    SpeedSlider(config.speedMps) { onConfigChanged(config.copy(speedMps = it)) }

    LabeledSlider(
        value = config.bearingDeg.toFloat(),
        valueRange = 0f..359f,
        steps = 358,
        label = stringResource(R.string.sim_bearing_label),
        valueText = "${config.bearingDeg.roundToInt()}°"
    ) { onConfigChanged(config.copy(bearingDeg = it.toDouble())) }

    LabeledSlider(
        value = config.altitudeM.toFloat(),
        valueRange = -200f..3000f,
        label = stringResource(R.string.sim_altitude_label),
        valueText = "${config.altitudeM.roundToInt()} m"
    ) { onConfigChanged(config.copy(altitudeM = it.toDouble())) }

    LabeledSlider(
        value = config.accuracyM,
        valueRange = 1f..100f,
        label = stringResource(R.string.sim_accuracy_label),
        valueText = "${config.accuracyM.roundToInt()} m"
    ) { onConfigChanged(config.copy(accuracyM = it)) }

    IntervalChips(config.intervalMs) { onConfigChanged(config.copy(intervalMs = it)) }

    if (showStartButton) {
        Button(onClick = onStartFixed, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.sim_start_fixed))
        }
        if (simState.status == SimStatus.IDLE) {
            Text(
                stringResource(R.string.map_initial_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun SpeedSlider(value: Double, onChange: (Double) -> Unit) {
    LabeledSlider(
        value = value.toFloat(),
        valueRange = 0f..30f,
        label = stringResource(R.string.sim_speed_label),
        valueText = stringResource(
            R.string.sim_speed_value,
            Formatters.speed(value),
            Formatters.speedKmh(value)
        )
    ) { onChange(it.toDouble()) }
}

@Composable
private fun PauseDurationSlider(valueMs: Long, onChange: (Long) -> Unit) {
    LabeledSlider(
        value = (valueMs / 1000f).coerceIn(1f, 120f),
        valueRange = 1f..120f,
        label = stringResource(R.string.sim_pause_duration_label),
        valueText = Formatters.seconds(valueMs)
    ) { onChange((it * 1000).toLong()) }
}

private val INTERVAL_OPTIONS_MS = listOf(500L, 1000L, 2000L, 5000L, 10000L)

@Composable
private fun IntervalChips(intervalMs: Long, onChange: (Long) -> Unit) {
    Text(
        stringResource(R.string.sim_interval_label),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        INTERVAL_OPTIONS_MS.forEach { opt ->
            OutlinedButton(
                onClick = { onChange(opt) },
                contentPadding = ButtonDefaults.ContentPadding,
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    stringResource(R.string.sim_interval_value, (opt / 1000).toString()),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    label: String,
    valueText: String,
    steps: Int = 0,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(valueText, style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SwitchRow(checked: Boolean, label: String, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun ControlRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        content()
    }
}

@Composable
private fun StatusDot(status: SimStatus) {
    val color = statusColor(status)
    androidx.compose.foundation.Canvas(Modifier.size(12.dp)) {
        drawCircle(color)
    }
}

private fun statusColor(status: SimStatus): Color = when (status) {
    SimStatus.RUNNING -> Color(0xFF2E7D32)
    SimStatus.PAUSED -> Color(0xFFF9A825)
    SimStatus.COMPLETED -> Color(0xFF455A64)
    SimStatus.IDLE -> Color(0xFF9E9E9E)
}

@Composable
private fun statusLabel(simState: SimulationState): String = when (simState.status) {
    SimStatus.RUNNING -> stringResource(R.string.sim_status_running)
    SimStatus.PAUSED -> stringResource(R.string.sim_status_paused)
    SimStatus.COMPLETED -> stringResource(R.string.sim_status_completed)
    SimStatus.IDLE -> stringResource(R.string.sim_status_idle)
}

@Composable
private fun statusDetail(simState: SimulationState): String = when {
    simState.mode == SimMode.ROUTE && simState.routeName != null ->
        "${stringResource(R.string.sim_route_mode)} · ${simState.routeName}"
    simState.mode == SimMode.FIXED -> stringResource(R.string.sim_fixed_mode)
    else -> stringResource(R.string.sim_fixed_mode)
}
