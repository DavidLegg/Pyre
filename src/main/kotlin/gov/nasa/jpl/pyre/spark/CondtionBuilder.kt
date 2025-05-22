package gov.nasa.jpl.pyre.spark

import gov.nasa.jpl.pyre.ember.CellSet.CellHandle
import gov.nasa.jpl.pyre.ember.Condition
import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.Duration.Companion.ZERO
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

import kotlin.coroutines.intrinsics.*
import kotlin.coroutines.resume

interface ConditionScope {
    suspend fun <V, E> read(cell: CellHandle<V, E>): V
}

// Return value aliases for condition code.
inline fun ConditionScope.now(): Duration = ZERO
inline fun ConditionScope.never(): Duration? = null

/**
 * Write a coroutine, but use it as a Pyre condition.
 *
 * Example:
 * ```
 * condition {
 *   val x = read(x_handle)
 *   val y = read(y_handle)
 *   if (x < y) {
 *     never()
 *   } else if (x == y) {
 *     now()
 *   } else {
 *     (x - y) * SECOND
 *   }
 * }
 * ```
 */
fun condition(block: suspend ConditionScope.() -> Duration?): Condition = with(ConditionBuilder()) {
    getCondition(block.createCoroutineUnintercepted(this, this))
}

private class ConditionBuilder : ConditionScope, Continuation<Duration?> {
    private var nextResult: Condition? = null
    override suspend fun <V, E> read(cell: CellHandle<V, E>) =
        suspendCoroutineUninterceptedOrReturn { c ->
            nextResult = Condition.Read(cell) { value ->
                nextResult = null
                c.resume(value)
                nextResult!!
            }
            COROUTINE_SUSPENDED
        }

    override val context: CoroutineContext
        get() = EmptyCoroutineContext

    // Completion continuation implementation
    override fun resumeWith(result: Result<Duration?>) {
        nextResult = Condition.Complete(result.getOrThrow())
    }

    fun getCondition(continuation: Continuation<Unit>): Condition {
        nextResult = null
        continuation.resume(Unit)
        return nextResult!!
    }
}
