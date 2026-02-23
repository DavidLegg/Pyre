package gov.nasa.jpl.pyre.kernel

import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

sealed interface ConditionResult {
    val time: Duration?
}

/**
 * @param time Relative time from now when this condition will be satisfied.
 */
data class SatisfiedAt(override val time: Duration) : ConditionResult {
    override fun toString(): String = "SatisfiedAt($time)"
}
/**
 * @param time Relative time from now when this condition may be satisfied, or null if it will never be satisfied.
 */
// TODO: kotlin.time.Duration includes support for infinity. Remove null and just use that.
data class UnsatisfiedUntil(override val time: Duration?) : ConditionResult {
    override fun toString(): String = "UnsatisfiedUntil(${time ?: "FOREVER"})"
}

interface ReadActions {
    fun <V> read(cell: Cell<V>): V
}
typealias Condition = (ReadActions) -> ConditionResult

val TRUE: Condition = { SatisfiedAt(ZERO) }
val FALSE: Condition = { UnsatisfiedUntil(null) }
