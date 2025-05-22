package gov.nasa.jpl.pyre.spark

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.CellSet.CellHandle
import gov.nasa.jpl.pyre.ember.Condition
import gov.nasa.jpl.pyre.ember.JsonValue
import gov.nasa.jpl.pyre.ember.Task
import gov.nasa.jpl.pyre.ember.Task.PureStepResult.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*


/*
 * DL: This whole setup is modeled after kotlin.sequences.SequenceBuilderIterator.
 * That provides the ability to construct an iterator with yield statements,
 * which is structurally similar to a task with await statements.
 * From there, I generalized a little to get the various kinds of continuation types to line up.
 * That said, I'm only about 50% confident that I understand how this thing works.
 */

interface TaskScope<T> {
    suspend fun complete(value: T)
    suspend fun <V, E> read(cell: CellHandle<V, E>): V
    suspend fun <V, E> emit(cell: CellHandle<V, E>, effect: E)
    suspend fun report(value: JsonValue)
    suspend fun delay(time: Duration)
    suspend fun await(condition: Condition)
    suspend fun <S> spawn(childName: String, child: () -> Task.PureStepResult<S>)
}

/**
 * Write a coroutine, but use it as a Pyre task.
 *
 * Example:
 * ```
 * coroutineTask("observation") {
 *   emit(instrumentState, SetTo(WARMUP))
 *   delay(10 * SECOND)
 *   val targetBody = read(targetBodyCell)
 *   if (targetBody != NONE) {
 *     emit(instrumentState, SetTo(IMAGING))
 *     delay(2 * HOUR)
 *   }
 *   emit(instrumentState, SetTo(OFF))
 * }
 * ```
 */
fun task(name: String, block: suspend TaskScope<Unit>.() -> Unit): Task<Unit> = with(TaskBuilder<Unit>()) {
    Task.of(name, continueWith(block.createCoroutineUnintercepted(this, this)))
}

private class TaskBuilder<T> : TaskScope<T>, Continuation<Unit> {
    private var nextResult: Task.PureStepResult<T>? = null

    override suspend fun complete(value: T): Unit =
        suspendCoroutineUninterceptedOrReturn {
            nextResult = Complete(value)
            Unit
        }

    override suspend fun <V, E> read(cell: CellHandle<V, E>): V =
        suspendCoroutineUninterceptedOrReturn { c ->
            nextResult = Read(cell) { value ->
                nextResult = null
                c.resume(value)
                nextResult!!
            }
            COROUTINE_SUSPENDED
        }

    override suspend fun <V, E> emit(cell: CellHandle<V, E>, effect: E) =
        suspendCoroutineUninterceptedOrReturn { c ->
            nextResult = Emit(cell, effect, continueWith(c))
            COROUTINE_SUSPENDED
        }

    override suspend fun report(value: JsonValue) =
        suspendCoroutineUninterceptedOrReturn { c ->
            nextResult = Report(value, continueWith(c))
            COROUTINE_SUSPENDED
        }

    override suspend fun delay(time: Duration) =
        suspendCoroutineUninterceptedOrReturn { c ->
            nextResult = Delay(time, continueWith(c))
            COROUTINE_SUSPENDED
        }

    override suspend fun await(condition: Condition) =
        suspendCoroutineUninterceptedOrReturn { c ->
            nextResult = Await(condition, continueWith(c))
            COROUTINE_SUSPENDED
        }

    override suspend fun <S> spawn(childName: String, child: () -> Task.PureStepResult<S>) =
        suspendCoroutineUninterceptedOrReturn { c ->
            nextResult = Spawn(childName, child, continueWith(c))
            COROUTINE_SUSPENDED
        }

    override val context: CoroutineContext
        get() = EmptyCoroutineContext

    // Completion continuation implementation
    override fun resumeWith(result: Result<Unit>) {
        result.getOrThrow()
    }

    fun continueWith(continuation: Continuation<Unit>): () -> Task.PureStepResult<T> = {
        nextResult = null
        continuation.resume(Unit)
        nextResult!!
    }
}