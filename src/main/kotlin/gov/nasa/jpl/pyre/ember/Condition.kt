package gov.nasa.jpl.pyre.ember

sealed interface Condition {
    data class Complete(val time: Duration?) : Condition {
        override fun toString() = "Complete(${time ?: "NEVER"})"
    }
    data class Read<V>(val cell: CellSet.CellHandle<V, *>, val continuation: (V) -> Condition) : Condition {
        override fun toString() = "Read(${cell.name}, ...)"
    }
}