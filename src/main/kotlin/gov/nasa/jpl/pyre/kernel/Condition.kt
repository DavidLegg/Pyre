package gov.nasa.jpl.pyre.kernel

import kotlin.time.Duration
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.ZERO

sealed interface ConditionResult {
    val time: Duration
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
data class UnsatisfiedUntil(override val time: Duration) : ConditionResult {
    override fun toString(): String = "UnsatisfiedUntil(${time.takeUnless { it == INFINITE } ?: "FOREVER"})"
}

interface ReadActions {
    fun <V> read(cell: Cell<V>): V
}
typealias Condition = (ReadActions) -> ConditionResult

// TODO: Find all construction of SatisfiedAt(ZERO) and UnsatisfiedUntil(INFINITE), and replace with a pre-built constant.
//   This is similar to the listOf() vs. emptyList idiom, of constructing one object for common cases of a data class
//   which we re-use to cut down on allocation.
val TRUE: Condition = { SatisfiedAt(ZERO) }
val FALSE: Condition = { UnsatisfiedUntil(INFINITE) }
