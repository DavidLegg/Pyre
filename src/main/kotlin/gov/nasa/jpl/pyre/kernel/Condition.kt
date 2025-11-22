package gov.nasa.jpl.pyre.kernel

import gov.nasa.jpl.pyre.kernel.CellSet.CellHandle
import gov.nasa.jpl.pyre.kernel.Duration.Companion.ZERO

sealed interface ConditionResult

/**
 * @param time Relative time from now when this condition will be satisfied.
 */
data class SatisfiedAt(val time: Duration) : ConditionResult {
    override fun toString(): String = "SatisfiedAt($time)"
}
/**
 * @param time Relative time from now when this condition may be satisfied, or null if it will never be satisfied.
 */
data class UnsatisfiedUntil(val time: Duration?) : ConditionResult {
    override fun toString(): String = "UnsatisfiedUntil(${time ?: "FOREVER"})"
}

interface ReadActions {
    fun <V> read(cell: CellHandle<V>): V
}
typealias Condition = (ReadActions) -> ConditionResult

val TRUE: Condition = { SatisfiedAt(ZERO) }
val FALSE: Condition = { UnsatisfiedUntil(null) }
