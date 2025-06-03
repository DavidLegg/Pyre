package gov.nasa.jpl.pyre.spark

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.CellSet.CellHandle
import gov.nasa.jpl.pyre.ember.Condition
import gov.nasa.jpl.pyre.ember.JsonValue
import gov.nasa.jpl.pyre.ember.PureTaskStep
import gov.nasa.jpl.pyre.ember.Task
import gov.nasa.jpl.pyre.ember.Task.PureStepResult.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*


/*
 * DL: This whole setup is modeled after kotlin.sequences.SequenceBuilderIterator.
 * That provides the ability to construct an iterator with yield statements,
 * which is structurally similar to a task with await statements.
 * From there, I generalized a little to get the various kinds of continuation types to line up.
 * That said, I'm only about 80% confident that I understand how this thing works.
 */

interface CellsReadableScope {
    suspend fun <V, E> read(cell: CellHandle<V, E>): V
}

interface ConditionScope : CellsReadableScope

/**
 * Write a coroutine, but use it as a Pyre condition.
 *
 * Example:
 * ```
 * condition {
 *   val x = read(x_handle)
 *   val y = read(y_handle)
 *   if (x < y) {
 *     null
 *   } else if (x == y) {
 *     ZERO
 *   } else {
 *     (x - y) * SECOND
 *   }
 * }
 * ```
 */
// Reconstruct the ConditionBuilder with each re-evaluation of the condition
fun condition(block: suspend ConditionScope.() -> Duration?): () -> Condition = { ConditionBuilder(block).getCondition() }

private class ConditionBuilder : ConditionScope, Continuation<Duration?> {
    private val start: Continuation<Unit>
    private var nextResult: Condition? = null

    constructor(block: suspend ConditionScope.() -> Duration?) {
        start = block.createCoroutineUnintercepted(this, this)
    }

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

    fun getCondition(): Condition {
        nextResult = null
        start.resume(Unit)
        return nextResult!!
    }
}

sealed interface TaskScopeResult<T> {
    class Restart<T> : TaskScopeResult<T>
    data class Complete<T>(val result: T) : TaskScopeResult<T>
}

interface TaskScope<T> : CellsReadableScope {
    suspend fun <V, E> emit(cell: CellHandle<V, E>, effect: E)
    suspend fun report(value: JsonValue)
    suspend fun delay(time: Duration)
    suspend fun await(condition: () -> Condition)
    suspend fun <S> spawn(childName: String, child: PureTaskStep<S>)
}

/**
 * Write a coroutine Pyre task.
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
 *
 * @see task
 * @see repeatingTask
 */
fun <T> coroutineTask(block: suspend TaskScope<T>.() -> TaskScopeResult<T>): PureTaskStep<T> =
    // Running the task step creates a new TaskBuilder, allowing for repeating tasks
    { TaskBuilder(block).runTask() }

/**
 * Write a coroutine Pyre task which never repeats.
 *
 * @see coroutineTask
 * @see repeatingTask
 */
fun <T> task(block: suspend TaskScope<T>.() -> T): PureTaskStep<T> =
    coroutineTask { TaskScopeResult.Complete(block()) }

/**
 * Write a coroutine Pyre task which automatically repeats.
 *
 * @see coroutineTask
 * @see task
 */
fun repeatingTask(block: suspend TaskScope<Unit>.() -> Unit): PureTaskStep<Unit> =
    coroutineTask { block(); TaskScopeResult.Restart() }

private class TaskBuilder<T> : TaskScope<T>, Continuation<TaskScopeResult<T>> {
    private val start: Continuation<Unit>
    private var nextResult: Task.PureStepResult<T>? = null

    constructor(block: suspend TaskScope<T>.() -> TaskScopeResult<T>) {
        start = block.createCoroutineUnintercepted(this, this)
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

    override suspend fun await(condition: () -> Condition) =
        suspendCoroutineUninterceptedOrReturn { c ->
            nextResult = Await(condition, continueWith(c))
            COROUTINE_SUSPENDED
        }

    override suspend fun <S> spawn(childName: String, child: PureTaskStep<S>) =
        suspendCoroutineUninterceptedOrReturn { c ->
            // Running the task step creates a new TaskBuilder, allowing for repeating tasks
            nextResult = Spawn(childName, child, continueWith(c))
            COROUTINE_SUSPENDED
        }

    override val context: CoroutineContext
        get() = EmptyCoroutineContext

    // Completion continuation implementation
    override fun resumeWith(result: Result<TaskScopeResult<T>>) {
        nextResult = when (val it = result.getOrThrow()) {
            is TaskScopeResult.Complete -> Complete<T>(it.result)
            is TaskScopeResult.Restart -> Restart()
        }
    }

    private fun continueWith(continuation: Continuation<Unit>): PureTaskStep<T> = {
        nextResult = null
        continuation.resume(Unit)
        nextResult!!
    }

    fun runTask(): Task.PureStepResult<T> = continueWith(start)()
}