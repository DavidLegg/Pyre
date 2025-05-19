package org.example.gov.nasa.jpl.pyre.core

sealed interface Condition {
    data class Complete(val time: Duration?) : Condition
    data class Read<V>(val cell: CellSet.CellHandle<V, *>, val continuation: (V) -> Condition) : Condition
}