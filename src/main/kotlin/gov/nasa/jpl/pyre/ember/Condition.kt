package gov.nasa.jpl.pyre.ember

import gov.nasa.jpl.pyre.ember.Duration.Companion.ZERO

sealed interface Condition {
    sealed interface ConditionResult : Condition
    data class SatisfiedAt(val time: Duration) : ConditionResult {
        override fun toString(): String = "SatisfiedAt($time)"
    }
    data class UnsatisfiedUntil(val time: Duration?) : ConditionResult {
        override fun toString(): String = "UnsatisfiedUntil(${time ?: "FOREVER"})"
    }
    data class Read<V>(val cell: CellSet.CellHandle<V>, val continuation: (V) -> Condition) : Condition {
        override fun toString() = "Read(${cell.name}, ...)"
    }

    companion object {
        val TRUE: Condition = SatisfiedAt(ZERO)
        val FALSE: Condition = UnsatisfiedUntil(null)
    }
}