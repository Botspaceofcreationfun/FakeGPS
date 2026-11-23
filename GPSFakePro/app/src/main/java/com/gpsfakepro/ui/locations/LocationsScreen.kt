package com.gpsfakepro.ui.locations

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import com.gpsfakepro.data.SavedLocationEntity
import com.gpsfakepro.ui.SelectionHolder
import com.gpsfakepro.ui.components.EmptyHint
import com.gpsfakepro.ui.components.MessageChip
import com.gpsfakepro.util.Formatters
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Saved favourite locations: search, use, edit, duplicate, delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationsScreen(onUseLocation: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    val locations by AppContainer.locations.searchLocations(query)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var message by remember { mutableStateOf<String?>(null) }
    var showNew by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SavedLocationEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<SavedLocationEntity?>(null) }

    LaunchedEffect(message) {
        if (message != null) {
            delay(3000)
            message = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.locations_title)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNew = true }) {
                Icon(Icons.Filled.Add, stringResource(R.string.location_add_title))
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                placeholder = { Text(stringResource(R.string.locations_search_hint)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
            )

            Box(Modifier.fillMaxSize()) {
                if (locations.isEmpty()) {
                    EmptyHint(stringResource(R.string.locations_empty))
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp, vertical = 8.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(locations, key = { it.id }) { loc ->
                            LocationCard(
                                location = loc,
                                onUse = {
                                    SelectionHolder.set(loc.latitude, loc.longitude)
                                    onUseLocation()
                                },
                                onEdit = { editing = loc },
                                onDuplicate = {
                                    scope.launch {
                                        AppContainer.locations.duplicateLocation(loc.id)
                                        message = context.getString(R.string.locations_duplicated, "${loc.name} (copy)")
                                    }
                                },
                                onDelete = { pendingDelete = loc }
                            )
                        }
                    }
                }

                MessageChip(
                    message,
                    Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                )
            }
        }
    }

    if (showNew) {
        LocationFormDialog(
            location = null,
            onDismiss = { showNew = false },
            onSave = { name, lat, lon, alt ->
                showNew = false
                scope.launch {
                    AppContainer.locations.addLocation(name, lat, lon, alt)
                    message = context.getString(R.string.locations_updated)
                }
            }
        )
    }

    editing?.let { loc ->
        LocationFormDialog(
            location = loc,
            onDismiss = { editing = null },
            onSave = { name, lat, lon, alt ->
                editing = null
                scope.launch {
                    AppContainer.locations.updateLocation(loc.id, name, lat, lon, alt)
                    message = context.getString(R.string.locations_updated)
                }
            }
        )
    }

    pendingDelete?.let { loc ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.locations_delete_title)) },
            text = { Text(stringResource(R.string.locations_delete_msg, loc.name)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        AppContainer.locations.deleteLocation(loc.id)
                        message = context.getString(R.string.locations_deleted)
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
private fun LocationCard(
    location: SavedLocationEntity,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(location.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${Formatters.coordShort(location.latitude)}, ${Formatters.coordShort(location.longitude)}" +
                        (location.altitude?.let { " · ${Formatters.speed(it)} m" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onUse) {
                Icon(Icons.Filled.MyLocation, stringResource(R.string.action_use))
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, stringResource(R.string.action_edit))
            }
            IconButton(onClick = onDuplicate) {
                Icon(Icons.Filled.ContentCopy, stringResource(R.string.action_duplicate))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
            }
        }
    }
}
