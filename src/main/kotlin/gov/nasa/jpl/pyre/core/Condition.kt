package org.example.gov.nasa.jpl.pyre.core

import org.example.gov.nasa.jpl.pyre.state.SimulationState

sealed interface Condition {
    data class Complete(val time: Duration?) : Condition
    data class Read<V>(val cell: SimulationState.CellHandle<V>, val continuation: (V) -> Condition) : Condition
}