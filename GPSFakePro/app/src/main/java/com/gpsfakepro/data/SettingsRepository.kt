package com.gpsfakepro.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gpsfakepro.engine.SimulationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * App settings persisted in DataStore: simulation defaults, language choice.
 */
object SettingsRepository {

    private val KEY_INTERVAL_MS = longPreferencesKey("interval_ms")
    private val KEY_SPEED_MPS = doublePreferencesKey("speed_mps")
    private val KEY_ACCURACY_M = floatPreferencesKey("accuracy_m")
    private val KEY_ALTITUDE_M = doublePreferencesKey("altitude_m")
    private val KEY_BEARING_DEG = doublePreferencesKey("bearing_deg")
    private val KEY_PAUSE_ENABLED = booleanPreferencesKey("pause_enabled")
    private val KEY_PAUSE_MS = longPreferencesKey("pause_ms")
    private val KEY_LANGUAGE = stringPreferencesKey("language")

    data class Defaults(
        val config: SimulationConfig,
        val language: String
    )

    fun observeDefaults(context: Context): Flow<Defaults> =
        context.dataStore.data.map { prefs ->
            Defaults(
                config = SimulationConfig(
                    intervalMs = prefs[KEY_INTERVAL_MS] ?: 1000L,
                    speedMps = prefs[KEY_SPEED_MPS] ?: 5.0,
                    accuracyM = prefs[KEY_ACCURACY_M] ?: 3f,
                    altitudeM = prefs[KEY_ALTITUDE_M] ?: 0.0,
                    bearingDeg = prefs[KEY_BEARING_DEG] ?: 0.0,
                    pauseAtWaypoints = prefs[KEY_PAUSE_ENABLED] ?: false,
                    pauseMs = prefs[KEY_PAUSE_MS] ?: 0L
                ).sanitized(),
                language = prefs[KEY_LANGUAGE] ?: "system"
            )
        }

    suspend fun updateDefaults(context: Context, config: SimulationConfig) {
        val c = config.sanitized()
        context.dataStore.edit { prefs ->
            prefs[KEY_INTERVAL_MS] = c.intervalMs
            prefs[KEY_SPEED_MPS] = c.speedMps
            prefs[KEY_ACCURACY_M] = c.accuracyM
            prefs[KEY_ALTITUDE_M] = c.altitudeM
            prefs[KEY_BEARING_DEG] = c.bearingDeg
            prefs[KEY_PAUSE_ENABLED] = c.pauseAtWaypoints
            prefs[KEY_PAUSE_MS] = c.pauseMs
        }
    }

    suspend fun setLanguage(context: Context, language: String) {
        context.dataStore.edit { prefs -> prefs[KEY_LANGUAGE] = language }
    }
}
