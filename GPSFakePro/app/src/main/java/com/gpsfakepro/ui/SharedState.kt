package com.gpsfakepro.ui

import com.gpsfakepro.engine.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The point currently selected on the map. Shared across screens: the map writes
 * it, the route editor and fixed-position start read it.
 */
object SelectionHolder {
    private val _selection = MutableStateFlow<LatLng?>(null)
    val selection: StateFlow<LatLng?> = _selection.asStateFlow()

    fun set(lat: Double, lon: Double) {
        _selection.value = LatLng(lat, lon)
    }

    fun set(point: LatLng) {
        _selection.value = point
    }

    fun clear() {
        _selection.value = null
    }
}
