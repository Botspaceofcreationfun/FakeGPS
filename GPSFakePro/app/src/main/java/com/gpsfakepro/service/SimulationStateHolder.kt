package com.gpsfakepro.service

import com.gpsfakepro.engine.SimulationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-process singleton carrying the live simulation state. The foreground
 * service writes it; the UI collects it. This is what lets the app show an
 * up-to-date marker and progress after it returns from the background.
 */
object SimulationStateHolder {

    private val _state = MutableStateFlow(SimulationState())
    val state: StateFlow<SimulationState> = _state.asStateFlow()

    val current: SimulationState get() = _state.value

    fun update(transform: (SimulationState) -> SimulationState) {
        _state.update(transform)
    }

    fun reset() {
        _state.value = SimulationState()
    }
}
