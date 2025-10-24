package gov.nasa.jpl.pyre.spark.tasks

import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.CellSet.CellHandle
import gov.nasa.jpl.pyre.kernel.Condition
import gov.nasa.jpl.pyre.kernel.Condition.ConditionResult
import gov.nasa.jpl.pyre.kernel.Effect
import gov.nasa.jpl.pyre.kernel.PureTaskStep
import gov.nasa.jpl.pyre.kernel.Task
import gov.nasa.jpl.pyre.kernel.Task.PureStepResult.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*
import kotlin.reflect.KType


/*
 * DL: This whole setup is modeled after kotlin.sequences.SequenceBuilderIterator.
 * That provides the ability to construct an iterator with yield statements,
 * which is structurally similar to a task with await statements.
 * From there, I generalized a little to get the various kinds of continuation types to line up.
 * That said, I'm only about 80% confident that I understand how this thing works.
 */

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
context (scope: SparkScope)
fun condition(block: suspend context (ConditionScope) () -> ConditionResult): () -> Condition = { ConditionBuilder(scope, block).getCondition() }

private class ConditionBuilder(
    sparkScope: SparkScope,
    block: suspend context (ConditionScope) () -> ConditionResult,
) : ConditionScope, Continuation<ConditionResult>, SparkScope by sparkScope {
    private val start: Continuation<Unit> = block.createCoroutineUnintercepted(this, this)
    private var nextResult: Condition? = null

    override suspend fun <V> read(cell: CellHandle<V>) =
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
    override fun resumeWith(result: Result<ConditionResult>) {
        nextResult = result.getOrThrow()
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
context (scope: SparkScope)
fun <T> coroutineTask(block: suspend context (TaskScope) () -> TaskScopeResult<T>): PureTaskStep<T> =
    // Running the task step creates a new TaskBuilder, allowing for repeating tasks
    { TaskBuilder(scope, block).runTask() }

/**
 * Write a coroutine Pyre task which never repeats.
 *
 * @see coroutineTask
 * @see repeatingTask
 */
context (scope: SparkScope)
fun <T> task(block: suspend context (TaskScope) () -> T): PureTaskStep<T> =
    coroutineTask { TaskScopeResult.Complete(block()) }

/**
 * Write a coroutine Pyre task which automatically repeats.
 *
 * @see coroutineTask
 * @see task
 */
context (scope: SparkScope)
fun repeatingTask(block: suspend context (TaskScope) () -> Unit): PureTaskStep<Unit> =
    coroutineTask { block(); TaskScopeResult.Restart() }

private class TaskBuilder<T>(
    sparkScope: SparkScope,
    block: suspend context (TaskScope) () -> TaskScopeResult<T>
) : TaskScope, Continuation<TaskScopeResult<T>>, SparkScope by sparkScope {
    private val start: Continuation<Unit> = block.createCoroutineUnintercepted(this, this)
    private var nextResult: Task.PureStepResult<T>? = null

    override suspend fun <V> read(cell: CellHandle<V>): V =
        suspendCoroutineUninterceptedOrReturn { c ->
            nextResult = Read(cell) { value ->
                nextResult = null
                c.resume(value)
                nextResult!!
            }
            COROUTINE_SUSPENDED
        }

    override suspend fun <V> emit(cell: CellHandle<V>, effect: Effect<V>) =
        suspendCoroutineUninterceptedOrReturn { c ->
            nextResult = Emit(cell, effect, continueWith(c))
            COROUTINE_SUSPENDED
        }

    override suspend fun <T> report(value: T, type: KType) =
        suspendCoroutineUninterceptedOrReturn { c ->
            nextResult = Report(value, type, continueWith(c))
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