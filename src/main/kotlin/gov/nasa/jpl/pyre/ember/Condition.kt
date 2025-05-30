package gov.nasa.jpl.pyre.ember

sealed interface Condition {
    // TODO: Add re-evaluate result to facilitate partial search?
    //  Maybe structure this as SatisfiedAt(Duration), UnsatisfiedUntil(Duration?)
    //  That way, SatisfiedAt(t) and UnsatisfiedUntil(null) are definitive, but UnsatUntil(t) isn't.
    //  Or maybe Complete(Duration?) and ReEvaluateAt(Duration) would be clearer?
    data class Complete(val time: Duration?) : Condition {
        override fun toString() = "Complete(${time ?: "NEVER"})"
    }
    data class Read<V>(val cell: CellSet.CellHandle<V, *>, val continuation: (V) -> Condition) : Condition {
        override fun toString() = "Read(${cell.name}, ...)"
    }
}