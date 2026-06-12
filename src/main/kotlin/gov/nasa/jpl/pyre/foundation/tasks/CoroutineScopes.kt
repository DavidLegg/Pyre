package gov.nasa.jpl.pyre.foundation.tasks

import gov.nasa.jpl.pyre.foundation.reporting.Channel
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport.ChannelData
import gov.nasa.jpl.pyre.foundation.resources.FaultedResourceException
import gov.nasa.jpl.pyre.foundation.tasks.ReportScope.Companion.report
import gov.nasa.jpl.pyre.foundation.tasks.ResourceScope.Companion.now
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope.Companion.stderr
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope.Companion.subSimulationScope
import gov.nasa.jpl.pyre.kernel.Cell
import gov.nasa.jpl.pyre.kernel.Condition
import gov.nasa.jpl.pyre.kernel.ConditionResult
import gov.nasa.jpl.pyre.kernel.Effect
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.NameOperations.div
import gov.nasa.jpl.pyre.kernel.UnsatisfiedUntil
import gov.nasa.jpl.pyre.kernel.tasks.PureTaskStep
import gov.nasa.jpl.pyre.kernel.tasks.BasicTaskActions
import gov.nasa.jpl.pyre.kernel.tasks.PureStepResult
import gov.nasa.jpl.pyre.kernel.tasks.PureStepResult.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*
import kotlin.time.Duration.Companion.INFINITE


context (scope: SimulationScope)
fun condition(block: context (ConditionScope) () -> ConditionResult): Condition = { actions ->
    try {
        block(object : ConditionScope, SimulationScope by scope {
            override fun <V> read(cell: Cell<V>): V = actions.read(cell)
        })
    } catch (e: FaultedResourceException) {
        // These are a special class of exceptions, a kind of semi-nominal case.
        // We never want to throw these exceptions, as they indicate some kind of modeling error.
        // However, unlike a general error, these exceptions represent errors that are well-contained.
        // In particular, they have an expiry we can use to indicate when it may be worth re-sampling the condition,
        // as it may have automatically cleared the fault.
        UnsatisfiedUntil(e.expiry)
    } catch (_: Throwable) {
        // By default, conditions simply aren't satisfied if they crash.
        // Normally, this is because we're awaiting some state on the resources, and a resource is faulted.
        // Conditions which care about handling faulted resources can add their own try/catch blocks,
        // but most will just fall through to here.
        // If the faulted resource clears its fault, that'll trip this condition to re-evaluate, and the blocked task may resume.
        UnsatisfiedUntil(INFINITE)
    }
}

sealed interface TaskScopeResult {
    object Restart : TaskScopeResult
    object Complete : TaskScopeResult
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
fun coroutineTask(block: suspend context (TaskScope) () -> TaskScopeResult): PureTaskStep =
    // Running the task step creates a new TaskBuilder, allowing for repeating tasks
    {
        TaskBuilder(scope) {
            try {
                block()
            } catch (e: AssertionError) {
                // AssertionError indicates something is deeply broken about the state of the world, and/or a test failure.
                // This is a rare case where crashing the simulator is actually the desired behavior.
                // See https://github.com/google/guava/wiki/ConditionalFailuresExplained for an excellent comparison of
                // the different kinds of errors, and when and why we might use AssertionError over others.
                throw e
            } catch (e: Throwable) {
                stderr.report("Task ${scope.contextName} crashed:\n" + e.stackTraceToString())
                TaskScopeResult.Complete
            }
        }.runTask(it)
    }

/**
 * Write a coroutine Pyre task which never repeats.
 *
 * @see coroutineTask
 * @see repeatingTask
 */
context (scope: SimulationScope)
fun task(block: suspend context (TaskScope) () -> Unit): suspend context (TaskScope) () -> TaskScopeResult =
    { block(); TaskScopeResult.Complete }

/**
 * Write a coroutine Pyre task which automatically repeats.
 *
 * @see coroutineTask
 * @see task
 */
context (scope: SimulationScope)
fun repeatingTask(block: suspend context (TaskScope) () -> Unit): suspend context (TaskScope) () -> TaskScopeResult =
    { block(); TaskScopeResult.Restart }

/*
 * This whole setup is modeled after kotlin.sequences.SequenceBuilderIterator.
 * That provides the ability to construct an iterator with yield statements,
 * which is structurally similar to a task with await statements.
 * From there, I generalized a little to get the various kinds of continuation types to line up.
 * Despite only vaguely understanding how the control flow here operates, it's proved fairly reliable in practice.
 */

private class TaskBuilder(
    simulationScope: SimulationScope,
    block: suspend context (TaskScope) () -> TaskScopeResult
) : TaskScope, Continuation<TaskScopeResult>, SimulationScope by simulationScope {
    private val start: Continuation<Unit> = block.createCoroutineUnintercepted(this, this)
    private var basicTaskActions: BasicTaskActions? = null
    private var nextResult: PureStepResult? = null

    override fun <V> read(cell: Cell<V>): V =
        basicTaskActions!!.read(cell)

    override fun <V> emit(cell: Cell<V>, effect: Effect<V>) =
        basicTaskActions!!.emit(cell, effect)

    override fun <T> report(channel: Channel<T>, value: T) {
        basicTaskActions!!.report(ChannelData(channel.name, now(), value))
    }

    override suspend fun await(condition: Condition) =
        suspendCoroutineUninterceptedOrReturn { c ->
            nextResult = Await(condition, continueWith(c))
            COROUTINE_SUSPENDED
        }

    override suspend fun spawn(childName: Name, child: suspend context (TaskScope) () -> TaskScopeResult) =
        suspendCoroutineUninterceptedOrReturn { c ->
            // Running the task step creates a new TaskBuilder, allowing for repeating tasks
            nextResult = Spawn(
                contextName / childName,
                // Incorporate the child's name into the coroutineTask's context name
                context(subSimulationScope(childName)) { coroutineTask(child) },
                continueWith(c)
            )
            COROUTINE_SUSPENDED
        }

    override val context: CoroutineContext
        get() = EmptyCoroutineContext

    // Completion continuation implementation
    override fun resumeWith(result: Result<TaskScopeResult>) {
        nextResult = when (result.getOrThrow()) {
            is TaskScopeResult.Complete -> Complete
            is TaskScopeResult.Restart -> Restart
        }
    }

    private fun continueWith(continuation: Continuation<Unit>): PureTaskStep = { actions ->
        basicTaskActions = actions
        nextResult = null
        continuation.resume(Unit)
        basicTaskActions = null
        nextResult!!
    }

    fun runTask(actions: BasicTaskActions): PureStepResult = continueWith(start).run(actions)

    override fun toString(): String = contextName.toString()
}
