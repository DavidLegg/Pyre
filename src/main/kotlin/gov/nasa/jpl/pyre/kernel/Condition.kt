package gov.nasa.jpl.pyre.kernel

import gov.nasa.jpl.pyre.kernel.CellSet.CellHandle
import gov.nasa.jpl.pyre.kernel.Duration.Companion.ZERO

sealed interface ConditionResult
data class SatisfiedAt(val time: Duration) : ConditionResult {
    override fun toString(): String = "SatisfiedAt($time)"
}
data class UnsatisfiedUntil(val time: Duration?) : ConditionResult {
    override fun toString(): String = "UnsatisfiedUntil(${time ?: "FOREVER"})"
}

interface ReadActions {
    fun <V> read(cell: CellHandle<V>): V
}
typealias Condition = (ReadActions) -> ConditionResult

val TRUE: Condition = { SatisfiedAt(ZERO) }
val FALSE: Condition = { UnsatisfiedUntil(null) }
