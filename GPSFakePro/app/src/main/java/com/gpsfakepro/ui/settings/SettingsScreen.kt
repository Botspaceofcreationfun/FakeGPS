package com.gpsfakepro.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpsfakepro.BuildConfig
import com.gpsfakepro.R
import com.gpsfakepro.data.AppContainer
import com.gpsfakepro.data.SettingsRepository
import com.gpsfakepro.engine.SimulationConfig
import com.gpsfakepro.ui.components.MessageChip
import com.gpsfakepro.util.Formatters
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val defaults by SettingsRepository.observeDefaults(context).collectAsStateWithLifecycle(
        initialValue = SettingsRepository.Defaults(SimulationConfig(), "system")
    )
    var message by remember { mutableStateOf<String?>(null) }
    var mockHelpExpanded by rememberSaveable { mutableStateOf(false) }
    var cfg by remember { mutableStateOf(defaults.config) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* state updates on next recomposition */ }

    LaunchedEffect(defaults.config) { cfg = defaults.config }
    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(3000)
            message = null
        }
    }

    fun updateConfig(newCfg: SimulationConfig) {
        cfg = newCfg
        scope.launch { SettingsRepository.updateDefaults(context, newCfg) }
    }

    fun changeLanguage(lang: String) {
        scope.launch {
            SettingsRepository.setLanguage(context, lang)
            val tags = when (lang) {
                "en" -> "en"
                "zh" -> "zh-CN"
                else -> ""
            }
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags))
        }
    }

    val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
        (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // ---- Simulation defaults ----
            SectionTitle(stringResource(R.string.settings_simulation))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    LabeledSetting(stringResource(R.string.settings_interval)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(500L, 1000L, 2000L, 5000L, 10000L).forEach { opt ->
                                OutlinedButton(
                                    onClick = { updateConfig(cfg.copy(intervalMs = opt)) },
                                    contentPadding = androidx.compose.material3.ButtonDefaults.ContentPadding
                                ) {
                                    Text(
                                        stringResource(R.string.sim_interval_value, (opt / 1000).toString()),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }

                    SliderSetting(
                        label = stringResource(R.string.settings_default_speed),
                        value = cfg.speedMps.toFloat(),
                        range = 0f..30f,
                        valueText = stringResource(
                            R.string.sim_speed_value,
                            Formatters.speed(cfg.speedMps),
                            Formatters.speedKmh(cfg.speedMps)
                        )
                    ) { updateConfig(cfg.copy(speedMps = it.toDouble())) }

                    SliderSetting(
                        label = stringResource(R.string.settings_default_accuracy),
                        value = cfg.accuracyM,
                        range = 1f..100f,
                        valueText = "${cfg.accuracyM.toInt()} m"
                    ) { updateConfig(cfg.copy(accuracyM = it)) }

                    SliderSetting(
                        label = stringResource(R.string.settings_default_altitude),
                        value = cfg.altitudeM.toFloat(),
                        range = -200f..3000f,
                        valueText = "${cfg.altitudeM.toInt()} m"
                    ) { updateConfig(cfg.copy(altitudeM = it.toDouble())) }

                    SliderSetting(
                        label = stringResource(R.string.settings_default_bearing),
                        value = cfg.bearingDeg.toFloat(),
                        range = 0f..359f,
                        valueText = "${cfg.bearingDeg.toInt()}°"
                    ) { updateConfig(cfg.copy(bearingDeg = it.toDouble())) }
                }
            }

            // ---- Language ----
            SectionTitle(stringResource(R.string.settings_language))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    LanguageRow(
                        label = stringResource(R.string.settings_language_system),
                        selected = defaults.language == "system",
                        onClick = { changeLanguage("system") }
                    )
                    LanguageRow(
                        label = stringResource(R.string.settings_language_en),
                        selected = defaults.language == "en",
                        onClick = { changeLanguage("en") }
                    )
                    LanguageRow(
                        label = stringResource(R.string.settings_language_zh),
                        selected = defaults.language == "zh",
                        onClick = { changeLanguage("zh") }
                    )
                }
            }

            // ---- Mock location help ----
            SectionTitle(stringResource(R.string.settings_mock_help_title))
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().clickable { mockHelpExpanded = !mockHelpExpanded }
                        .padding(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.settings_mock_help_body),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            if (mockHelpExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null
                        )
                    }
                    if (mockHelpExpanded) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.settings_mock_step1),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            stringResource(R.string.settings_mock_step2),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            stringResource(R.string.settings_mock_step3),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            stringResource(R.string.settings_mock_step4),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            stringResource(R.string.settings_mock_step5),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.settings_mock_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ---- Notifications ----
            SectionTitle(stringResource(R.string.settings_notif_permission))
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Notifications, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_notif_permission),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            stringResource(R.string.settings_notif_permission_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (notificationsGranted) {
                        Text(
                            stringResource(R.string.settings_notif_permission_granted),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Button(onClick = {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }) {
                            Text(stringResource(R.string.action_save))
                        }
                    }
                }
            }

            // ---- Data ----
            SectionTitle(stringResource(R.string.settings_clear_recents))
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_clear_recents),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            stringResource(R.string.settings_recents_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = {
                        scope.launch {
                            AppContainer.locations.clearRecent()
                            message = context.getString(R.string.settings_cleared)
                        }
                    }) {
                        Text(stringResource(R.string.action_delete))
                    }
                }
            }

            // ---- About ----
            SectionTitle(stringResource(R.string.settings_about_title))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.settings_about_body),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.settings_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            MessageChip(message, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun LabeledSetting(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        content()
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
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
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
