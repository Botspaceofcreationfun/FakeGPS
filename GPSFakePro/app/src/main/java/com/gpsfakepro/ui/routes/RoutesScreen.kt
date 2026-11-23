package com.gpsfakepro.ui.routes

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpsfakepro.R
import com.gpsfakepro.data.AppContainer
import com.gpsfakepro.data.RouteEntity
import com.gpsfakepro.data.RouteWithCount
import com.gpsfakepro.data.SettingsRepository
import com.gpsfakepro.engine.RoutePoint
import com.gpsfakepro.engine.SimulationConfig
import com.gpsfakepro.gpx.GpxIO
import com.gpsfakepro.service.SimulationController
import com.gpsfakepro.ui.components.EmptyHint
import com.gpsfakepro.ui.components.MessageChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Saved routes: create, edit, start, duplicate, delete, GPX import/export. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutesScreen(
    onEditRoute: (Long) -> Unit,
    onOpenMap: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    val routes by AppContainer.locations.observeRoutesWithCounts(query)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val defaults by SettingsRepository.observeDefaults(context).collectAsStateWithLifecycle(
        initialValue = SettingsRepository.Defaults(SimulationConfig(), "system")
    )
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<RouteEntity?>(null) }
    var pendingExportXml by remember { mutableStateOf<String?>(null) }
    var pendingExportName by remember { mutableStateOf("route.gpx") }

    LaunchedEffect(message) {
        if (message != null) {
            delay(3000)
            message = null
        }
    }

    // ---- GPX import ----
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { GpxIO.parse(it) }
                }.getOrNull() ?: GpxIO.ImportResult.Failure(
                    GpxIO.ImportResult.Reason.PARSE_ERROR, "cannot open file"
                )
            }
            when (result) {
                is GpxIO.ImportResult.Success -> {
                    val points = result.waypoints
                    if (points.size < 2) {
                        message = context.getString(R.string.route_import_too_few)
                    } else {
                        val base = result.name?.takeIf { it.isNotBlank() }
                            ?: displayName(context, uri)
                        val unique = AppContainer.locations.uniqueRouteName(base)
                        AppContainer.locations.saveRoute(
                            routeId = 0L,
                            name = unique,
                            waypoints = points,
                            speedMps = defaults.config.speedMps,
                            pauseEnabled = defaults.config.pauseAtWaypoints,
                            pauseMs = defaults.config.pauseMs
                        )
                        message = if (unique == base) {
                            context.getString(R.string.route_import_done, unique, points.size)
                        } else {
                            context.getString(R.string.route_import_renamed, base, unique)
                        }
                    }
                }
                is GpxIO.ImportResult.Failure -> {
                    message = when (result.reason) {
                        GpxIO.ImportResult.Reason.NOT_XML ->
                            context.getString(R.string.gpx_error_not_xml)
                        GpxIO.ImportResult.Reason.PARSE_ERROR ->
                            context.getString(R.string.gpx_error_parse, result.detail ?: "?")
                        GpxIO.ImportResult.Reason.EMPTY ->
                            context.getString(R.string.route_import_empty)
                        GpxIO.ImportResult.Reason.INVALID_COORDS ->
                            context.getString(
                                R.string.gpx_error_invalid_coords,
                                result.pointIndex ?: 0
                            )
                    }
                }
            }
            busy = false
        }
    }

    // ---- GPX export: all routes + locations ----
    val exportAllLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val locations = AppContainer.locations.observeLocations().first()
                    val routesAll = AppContainer.locations.observeRoutes().first()
                        .mapNotNull { AppContainer.locations.routeWithWaypoints(it.id) }
                    val xml = GpxIO.export(locations, routesAll)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(xml.toByteArray(Charsets.UTF_8))
                    }
                }
            }
            message = outcome.fold(
                onSuccess = { context.getString(R.string.route_export_done, "GPX") },
                onFailure = { e ->
                    context.getString(R.string.route_export_failed, e.message ?: "unknown")
                }
            )
            busy = false
        }
    }

    // ---- GPX export: single route ----
    val exportOneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri ->
        val xml = pendingExportXml ?: return@rememberLauncherForActivityResult
        pendingExportXml = null
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(xml.toByteArray(Charsets.UTF_8))
                    }
                }
            }
            message = outcome.fold(
                onSuccess = { context.getString(R.string.route_export_done, pendingExportName) },
                onFailure = { e ->
                    context.getString(R.string.route_export_failed, e.message ?: "unknown")
                }
            )
            busy = false
        }
    }

    fun exportSingleRoute(item: RouteWithCount) {
        scope.launch {
            val full = AppContainer.locations.routeWithWaypoints(item.route.id)
            if (full != null) {
                pendingExportName = sanitizeFileName(full.route.name) + ".gpx"
                pendingExportXml = GpxIO.exportRoute(full)
                exportOneLauncher.launch(pendingExportName)
            }
        }
    }

    fun startRoute(item: RouteWithCount) {
        scope.launch {
            val full = AppContainer.locations.routeWithWaypoints(item.route.id)
            if (full == null || full.waypoints.size < 2) {
                message = context.getString(
                    R.string.route_start_error, context.getString(R.string.route_import_too_few)
                )
                return@launch
            }
            val points = full.waypoints.map { RoutePoint(it.latitude, it.longitude, it.altitude) }
            val cfg = defaults.config.copy(
                speedMps = full.route.speedMps,
                pauseAtWaypoints = full.route.pauseEnabled,
                pauseMs = full.route.pauseMs
            )
            SimulationController.startRoute(context, full.route.name, points, cfg)
            scope.launch {
                AppContainer.locations.addRecent(points.first().latitude, points.first().longitude)
            }
            onOpenMap()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.routes_title)) },
                actions = {
                    TextButton(onClick = {
                        importLauncher.launch(
                            arrayOf(
                                "application/gpx+xml", "application/xml",
                                "text/xml", "application/octet-stream"
                            )
                        )
                    }) {
                        Icon(Icons.Filled.FileUpload, null, Modifier.padding(end = 4.dp))
                        Text(stringResource(R.string.route_import))
                    }
                    TextButton(onClick = { exportAllLauncher.launch("gps_fake_pro_routes.gpx") }) {
                        Icon(Icons.Filled.UploadFile, null, Modifier.padding(end = 4.dp))
                        Text(stringResource(R.string.route_export_all))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEditRoute(0L) }) {
                Icon(Icons.Filled.Add, stringResource(R.string.route_new))
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                placeholder = { Text(stringResource(R.string.routes_search_hint)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
            )

            Box(Modifier.fillMaxSize()) {
                if (routes.isEmpty() && !busy) {
                    EmptyHint(stringResource(R.string.routes_empty))
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(routes, key = { it.route.id }) { item ->
                            RouteCard(
                                item = item,
                                onStart = { startRoute(item) },
                                onEdit = { onEditRoute(item.route.id) },
                                onDuplicate = {
                                    scope.launch {
                                        AppContainer.locations.duplicateRoute(item.route.id)
                                        message = context.getString(
                                            R.string.route_duplicated, "${item.route.name} (copy)"
                                        )
                                    }
                                },
                                onExport = { exportSingleRoute(item) },
                                onDelete = { pendingDelete = item.route }
                            )
                        }
                    }
                }
                if (busy) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                MessageChip(message, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
            }
        }
    }

    pendingDelete?.let { route ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.route_delete_title)) },
            text = { Text(stringResource(R.string.route_delete_msg, route.name)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        AppContainer.locations.deleteRoute(route.id)
                        message = context.getString(R.string.route_deleted)
                    }
                }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun RouteCard(
    item: RouteWithCount,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.route.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(
                        R.string.route_card_summary,
                        item.waypointCount,
                        com.gpsfakepro.util.Formatters.speed(item.route.speedMps),
                        com.gpsfakepro.util.Formatters.speedKmh(item.route.speedMps)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onStart) {
                Icon(Icons.Filled.PlayArrow, stringResource(R.string.route_start))
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, stringResource(R.string.action_edit))
            }
            IconButton(onClick = onDuplicate) {
                Icon(Icons.Filled.ContentCopy, stringResource(R.string.action_duplicate))
            }
            IconButton(onClick = onExport) {
                Icon(Icons.Filled.UploadFile, stringResource(R.string.route_export))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
            }
        }
    }
}

private fun sanitizeFileName(name: String): String =
    name.replace(Regex("[^A-Za-z0-9_\\- .]"), "_").ifBlank { "route" }

private fun displayName(context: android.content.Context, uri: android.net.Uri): String =
    runCatching {
        context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotEmpty() }
        ?: "Imported route"
