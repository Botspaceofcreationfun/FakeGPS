package com.gpsfakepro.ui.locations

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gpsfakepro.R
import com.gpsfakepro.data.SavedLocationEntity
import java.util.Locale

/** Add/edit dialog for a favourite location with validation. */
@Composable
fun LocationFormDialog(
    location: SavedLocationEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, lat: Double, lon: Double, altitude: Double?) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(location?.name ?: "") }
    var latText by rememberSaveable { mutableStateOf(format(location?.latitude)) }
    var lonText by rememberSaveable { mutableStateOf(format(location?.longitude)) }
    var altText by rememberSaveable { mutableStateOf(location?.altitude?.let { format(it) } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (location == null) R.string.location_add_title else R.string.location_edit_title
                )
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text(stringResource(R.string.location_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it; error = null },
                    label = { Text(stringResource(R.string.map_manual_lat)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = lonText,
                    onValueChange = { lonText = it; error = null },
                    label = { Text(stringResource(R.string.map_manual_lon)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = altText,
                    onValueChange = { altText = it },
                    label = { Text(stringResource(R.string.map_manual_alt_optional)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val lat = latText.trim().toDoubleOrNull()
                val lon = lonText.trim().toDoubleOrNull()
                val alt = altText.trim().toDoubleOrNull()
                when {
                    name.trim().isEmpty() ->
                        error = context.getString(R.string.location_name_required)
                    lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0 ->
                        error = context.getString(R.string.location_coords_invalid)
                    else -> onSave(name.trim(), lat, lon, alt)
                }
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

private fun format(value: Double?): String =
    value?.let { String.format(Locale.US, "%.6f", it) } ?: ""
