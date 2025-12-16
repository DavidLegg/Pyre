package gov.nasa.jpl.pyre.foundation.tasks

import gov.nasa.jpl.pyre.foundation.reporting.Channel
import gov.nasa.jpl.pyre.foundation.reporting.ChannelizedReport
import gov.nasa.jpl.pyre.foundation.tasks.ResourceScope.Companion.now
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope.Companion.subSimulationScope
import gov.nasa.jpl.pyre.kernel.Cell
import gov.nasa.jpl.pyre.kernel.Condition
import gov.nasa.jpl.pyre.kernel.ConditionResult
import gov.nasa.jpl.pyre.kernel.Effect
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.NameOperations.div
import gov.nasa.jpl.pyre.kernel.PureTaskStep
import gov.nasa.jpl.pyre.kernel.Task
import gov.nasa.jpl.pyre.kernel.Task.BasicTaskActions
import gov.nasa.jpl.pyre.kernel.Task.PureStepResult.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*
import kotlin.reflect.KType


context (scope: SimulationScope)
fun condition(block: context (ConditionScope) () -> ConditionResult): Condition = { actions ->
    block(object : ConditionScope, SimulationScope by scope {
        override fun <V> read(cell: Cell<V>): V = actions.read(cell)
    })
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
 * coroutineTask {
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
context (scope: SimulationScope)
fun <T> coroutineTask(block: suspend context (TaskScope) () -> TaskScopeResult<T>): PureTaskStep<T> =
    // Running the task step creates a new TaskBuilder, allowing for repeating tasks
    { TaskBuilder(scope, block).runTask(it) }

/**
 * Write a coroutine Pyre task which never repeats.
 *
 * @see coroutineTask
 * @see repeatingTask
 */
context (scope: SimulationScope)
fun <T> task(block: suspend context (TaskScope) () -> T): suspend context (TaskScope) () -> TaskScopeResult<T> =
    { TaskScopeResult.Complete(block()) }

/**
 * Write a coroutine Pyre task which automatically repeats.
 *
 * @see coroutineTask
 * @see task
 */
context (scope: SimulationScope)
fun repeatingTask(block: suspend context (TaskScope) () -> Unit): suspend context (TaskScope) () -> TaskScopeResult<Unit> =
    { block(); TaskScopeResult.Restart() }

/*
 * This whole setup is modeled after kotlin.sequences.SequenceBuilderIterator.
 * That provides the ability to construct an iterator with yield statements,
 * which is structurally similar to a task with await statements.
 * From there, I generalized a little to get the various kinds of continuation types to line up.
 * Despite only vaguely understanding how the control flow here operates, it's proved fairly reliable in practice.
 */

private class TaskBuilder<T>(
    simulationScope: SimulationScope,
    block: suspend context (TaskScope) () -> TaskScopeResult<T>
) : TaskScope, Continuation<TaskScopeResult<T>>, SimulationScope by simulationScope {
    private val start: Continuation<Unit> = block.createCoroutineUnintercepted(this, this)
    private var basicTaskActions: BasicTaskActions? = null
    private var nextResult: Task.PureStepResult<T>? = null

    override fun <V> read(cell: Cell<V>): V =
        basicTaskActions!!.read(cell)

    override fun <V> emit(cell: Cell<V>, effect: Effect<V>) =
        basicTaskActions!!.emit(cell, effect)

    override fun <T> report(channel: Channel<T>, value: T) {
        basicTaskActions!!.report(ChannelizedReport(channel.name, now(), value), channel.reportType)
    }

    override suspend fun await(condition: Condition) =
        suspendCoroutineUninterceptedOrReturn { c ->
            nextResult = Await(condition, continueWith(c))
            COROUTINE_SUSPENDED
        }

    override suspend fun <S> spawn(childName: Name, child: suspend context (TaskScope) () -> TaskScopeResult<S>) =
        suspendCoroutineUninterceptedOrReturn { c ->
            // Running the task step creates a new TaskBuilder, allowing for repeating tasks
            nextResult = Spawn(
                contextName / childName,
                // Incorporate the child's name into the coroutineTask's context name
                context (subSimulationScope(childName)) { coroutineTask(child) },
                continueWith(c)
            )
            COROUTINE_SUSPENDED
        }

    override val context: CoroutineContext
        get() = EmptyCoroutineContext

    // Completion continuation implementation
    override fun resumeWith(result: Result<TaskScopeResult<T>>) {
        nextResult = when (val it = result.getOrThrow()) {
            is TaskScopeResult.Complete -> Complete(it.result)
            is TaskScopeResult.Restart -> Restart()
        }
    }

    private fun continueWith(continuation: Continuation<Unit>): PureTaskStep<T> = { actions ->
        basicTaskActions = actions
        nextResult = null
        continuation.resume(Unit)
        basicTaskActions = null
        nextResult!!
    }

    fun runTask(actions: BasicTaskActions): Task.PureStepResult<T> = continueWith(start)(actions)

    override fun toString(): String = contextName.toString()
}