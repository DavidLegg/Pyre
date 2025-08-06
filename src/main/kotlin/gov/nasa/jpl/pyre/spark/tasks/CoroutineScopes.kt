package gov.nasa.jpl.pyre.spark.tasks

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.CellSet.CellHandle
import gov.nasa.jpl.pyre.ember.Condition
import gov.nasa.jpl.pyre.ember.Condition.ConditionResult
import gov.nasa.jpl.pyre.ember.PureTaskStep
import gov.nasa.jpl.pyre.ember.Task
import gov.nasa.jpl.pyre.ember.Task.PureStepResult.*
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
fun SparkContext.condition(block: suspend ConditionScope.() -> ConditionResult): () -> Condition = { ConditionBuilder(this, block).getCondition() }

object ConditionsThroughContext {
    context (context: SparkContext)
    fun condition(block: suspend ConditionScope.() -> ConditionResult): () -> Condition = context.condition(block)
}

private class ConditionBuilder(
    sparkContext: SparkContext,
    block: suspend ConditionScope.() -> ConditionResult,
) : ConditionScope, Continuation<ConditionResult>, SparkContext by sparkContext {
    private val start: Continuation<Unit> = block.createCoroutineUnintercepted(this, this)
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
fun <T> SparkContext.coroutineTask(block: suspend TaskScope.() -> TaskScopeResult<T>): PureTaskStep<T> =
    // Running the task step creates a new TaskBuilder, allowing for repeating tasks
    { TaskBuilder(this, block).runTask() }

/**
 * Write a coroutine Pyre task which never repeats.
 *
 * @see coroutineTask
 * @see repeatingTask
 */
fun <T> SparkContext.task(block: suspend TaskScope.() -> T): PureTaskStep<T> =
    coroutineTask { TaskScopeResult.Complete(block()) }

/**
 * Write a coroutine Pyre task which automatically repeats.
 *
 * @see coroutineTask
 * @see task
 */
fun SparkContext.repeatingTask(block: suspend TaskScope.() -> Unit): PureTaskStep<Unit> =
    coroutineTask { block(); TaskScopeResult.Restart() }

object CoroutineTasksThroughContext {
    context (context: SparkContext)
    fun <T> coroutineTask(block: suspend TaskScope.() -> TaskScopeResult<T>): PureTaskStep<T> = context.coroutineTask(block)

    context (context: SparkContext)
    fun <T> task(block: suspend TaskScope.() -> T): PureTaskStep<T> = context.task(block)

    context (context: SparkContext)
    fun repeatingTask(block: suspend TaskScope.() -> Unit): PureTaskStep<Unit> = context.repeatingTask(block)
}

private class TaskBuilder<T>(
    sparkContext: SparkContext,
    block: suspend TaskScope.() -> TaskScopeResult<T>
) : TaskScope, Continuation<TaskScopeResult<T>>, SparkContext by sparkContext {
    private val start: Continuation<Unit> = block.createCoroutineUnintercepted(this, this)
    private var nextResult: Task.PureStepResult<T>? = null

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