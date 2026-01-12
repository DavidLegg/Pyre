package gov.nasa.jpl.pyre.kernel

import gov.nasa.jpl.pyre.kernel.MutableSnapshot.Companion.report
import gov.nasa.jpl.pyre.utilities.Reflection.withArg
import gov.nasa.jpl.pyre.kernel.Snapshot.Companion.provide
import gov.nasa.jpl.pyre.kernel.Snapshot.Companion.within
import gov.nasa.jpl.pyre.kernel.PureTask.TaskHistoryStep.*
import gov.nasa.jpl.pyre.kernel.Task.*
import gov.nasa.jpl.pyre.kernel.TaskHistoryCollector.Companion.report
import gov.nasa.jpl.pyre.kernel.TaskHistoryProvider.Companion.provide
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KType

fun interface PureTaskStep<T> {
    fun run(actions: BasicTaskActions): PureStepResult<T>
}

/**
 * A Task is a unit of action in the simulation.
 *
 * Each TaskStep is an atomic unit of action - a simulation cannot be stopped during a TaskStep.
 * From the perspective of one TaskStep T, another TaskStep S can only observe the simulation before or after T, never during it.
 *
 * Additionally, Tasks are restorable. Given a root task T, which is now executing TaskStep S,
 * and an identical root task T' (potentially in a new simulation, and hence an entirely separate JVM),
 * (S.save(C); T'.restore(C)) will construct a Task which is functionally identical to S.
 * This extends to task children; a parent task can recursively restore all its children.
 */
interface Task<T> {
    val id: TaskId
    fun runStep(actions: BasicTaskActions): TaskStepResult<T>

    /**
     * Saves this task's history directly to the given collector.
     *
     * Assumes that finconCollector has been restricted using [MutableSnapshot.within] to the desired location for this task's history.
     *
     * Suggested to use [RootTaskId.conditionKeys] in that location.
     */
    fun save(finconCollector: MutableSnapshot)

    /**
     * Restore a task from history provided by inconProvider.
     *
     * Assumes that this is the root task from which the given task was spawned,
     * and that inconProvider has been restricted using [Snapshot.within] to the desired task's history.
     *
     * May return a task with a different [TaskId] from this, if restoring a child task.
     */
    fun restore(inconProvider: Snapshot): Task<*>?

    class TaskId(val rootTaskName: Name, val name: Name, val stepNumber: Int) {
        constructor(name: Name) : this(name, name, 0)

        fun nextStep() = TaskId(rootTaskName, name, stepNumber + 1)
        fun child(childName: Name) = TaskId(rootTaskName, childName, 0)

        override fun toString() = "$name[$stepNumber]"
    }

    /**
     * The "non-yielding" actions a [PureTask] can take.
     *
     * Non-yielding actions don't interrupt the flow of control, aka "yield".
     */
    interface BasicTaskActions {
        fun <V> read(cell: Cell<V>): V
        fun <V> emit(cell: Cell<V>, effect: Effect<V>)
        fun <V> report(value: V)
        // Note that "spawn" is not listed here. Arguably, it's a non-yielding action and should be here.
        // It winds up being easier to restore tasks if we can choose which branch (parent or child) to take.
        // For this reason, it's better to treat spawn as a yielding action.
    }


    // Explanation:
    // A "pure" task step doesn't save the information necessary to resume.
    // These are easy to write, but insufficient to run without first wrapping with additional save/restore functions.
    // This is what a "full" task step has - note the continuations are full-fledged Tasks, not merely task steps.

    /**
     * The ways a task step can "yield" back to the simulation engine, aka "yielding actions".
     *
     * These correspond with the task pausing (or stopping completely!),
     * so the simulation engine can schedule other tasks before the next step of this task.
     */
    sealed interface PureStepResult<T> {
        data class Complete<T>(val value: T) : PureStepResult<T> {
            override fun toString() = "Complete($value)"
        }
        data class Await<T>(val condition: Condition, val continuation: PureTaskStep<T>) : PureStepResult<T> {
            override fun toString() = "Await($condition)"
        }
        data class Spawn<S, T>(val childName: Name, val child: PureTaskStep<S>, val continuation: PureTaskStep<T>) : PureStepResult<T> {
            override fun toString() = "Spawn($childName)"
        }
        class Restart<T> : PureStepResult<T> {
            override fun toString() = "Restart"
        }
    }

    /**
     * Like [PureStepResult], but includes task history information needed to fully save and restore the task.
     */
    sealed interface TaskStepResult<T> {
        data class Complete<T>(val value: T) : TaskStepResult<T> {
            override fun toString() = "Complete($value)"
        }
        data class Await<T>(val condition: Condition, val rewait: Task<T>, val continuation: Task<T>) : TaskStepResult<T> {
            override fun toString() = "Await($condition)"
        }
        data class Spawn<S, T>(val child: Task<S>, val continuation: Task<T>) : TaskStepResult<T> {
            override fun toString() = "Spawn($child)"
        }
        // NoOp's are stand-ins for steps without special handling by the engine, which can't be eliminated entirely.
        // The motivating use case here is restarting a task that immediately awaits.
        // When saving the awaiting task, we save the history for the task step before the await.
        // If the restart is boiled out completely, that's the full history for the last iteration.
        // If the restart instead becomes a NoOp, that NoOp can yield an empty history, keeping the fincon files clean.
        // In particular, for any PlanSimulation, the activity loader immediately awaits the next activity to load.
        // Without the NoOp, it always saves the last activity it loaded instead of saving an empty history.
        data class Restart<T>(val continuation: Task<T>) : TaskStepResult<T> {
            override fun toString() = "NoOp"
        }
    }

    companion object {
        fun <T> of(name: Name, step: PureTaskStep<T>): Task<T> {
            return PureTask(TaskId(name), step, {}, null)
        }
    }
}


// Although you could technically implement Task from scratch,
// it's so complicated in practice that everyone basically uses PureTask.
// Even I use it to test the kernel engine, since writing Task from scratch would be so miserable.
// For that reason, I'm keeping this in kernel instead of foundation.
private class PureTask<T>(
    override val id: TaskId,
    private val step: PureTaskStep<T>,
    private val saveData: TaskHistoryCollector.() -> Unit,
    rootTask: PureTask<T>?
) : Task<T> {
    private val rootTask: PureTask<T> = rootTask ?: this

    override fun runStep(actions: BasicTaskActions): TaskStepResult<T> {
        val partialHistory: MutableList<TaskHistoryCollector.() -> Unit> = mutableListOf()
        fun TaskHistoryCollector.reportHistory() {
            saveData()
            partialHistory.forEach { it() }
        }
        val historyCapturingActions = object : BasicTaskActions {
            override fun <V> read(cell: Cell<V>): V =
                actions.read(cell).also { value ->
                    partialHistory += {
                        report(ReadMarker(value), ReadMarker.concreteType(cell.valueType))
                    }
                }

            // Emit and Report don't need to write any history; they'll just run again when restoring.
            override fun <V> emit(cell: Cell<V>, effect: Effect<V>) = actions.emit(cell, effect)
            override fun <V> report(value: V) = actions.report(value)
        }
        return when (val stepResult = step.run(historyCapturingActions)) {
            is PureStepResult.Complete -> TaskStepResult.Complete(stepResult.value)
            is PureStepResult.Await -> TaskStepResult.Await(
                stepResult.condition,
                PureTask(
                    id,
                    { stepResult },
                    { reportHistory() },
                    rootTask
                ),
                PureTask(
                    id.nextStep(),
                    stepResult.continuation,
                    { reportHistory(); report<TaskHistoryStep>(AwaitMarker) },
                    rootTask
                )
            )
            is PureStepResult.Spawn<*, T> -> runSpawn(stepResult) { reportHistory() }
            is PureStepResult.Restart -> TaskStepResult.Restart(rootTask)
        }
    }

    private fun <S> runSpawn(step: PureStepResult.Spawn<S, T>, reportHistory: TaskHistoryCollector.() -> Unit) = TaskStepResult.Spawn(
        PureTask(
            id.child(step.childName),
            step.child,
            { reportHistory(); report<TaskHistoryStep>(SpawnMarker(SpawnMarkerBranch.Child)) },
            null
        ),
        PureTask(
            id.nextStep(),
            step.continuation,
            { reportHistory(); report<TaskHistoryStep>(SpawnMarker(SpawnMarkerBranch.Parent)) },
            rootTask
        )
    )

    override fun save(finconCollector: MutableSnapshot) =
        // We only have a serializer for TaskHistory, not for MutableTaskHistory.
        finconCollector.report<TaskHistory>(MutableTaskHistory().apply(saveData))

    override fun restore(inconProvider: Snapshot): Task<*>? =
        // Restore this task itself, if there's incon data for it.
        inconProvider.provide<TaskHistory>()?.let {
            restoreSingle(it.provider())
        }

    private fun restoreSingle(inconProvider: TaskHistoryProvider): Task<*> {
        val restorationActions = object : BasicTaskActions {
            override fun <V> read(cell: Cell<V>): V =
                requireNotNull(inconProvider.provide<ReadMarker<V>>(ReadMarker.concreteType(cell.valueType))) {
                    "No restore data available to read $cell! Incon data is malformed."
                }.value
            override fun <V> emit(cell: Cell<V>, effect: Effect<V>) { /* ignore and continue */ }
            override fun <V> report(value: V) { /* ignore and continue */ }
        }

        var thisTask: Task<*> = this

        // Only run the next step of the task if we have history for it
        while (inconProvider.hasNext()) {
            thisTask = when (val stepResult = thisTask.runStep(restorationActions)) {
                is TaskStepResult.Await -> {
                    // If an await step has incon data, it completed, so continue the task
                    inconProvider.provideStep<AwaitMarker> { stepResult.continuation }
                        // Otherwise, we're awaiting right now, so return the rewait task
                        ?: stepResult.rewait
                }
                // For spawns, check the task history and take the branch corresponding with this history
                is TaskStepResult.Spawn<*, *> -> checkNotNull(inconProvider.provideStep<SpawnMarker> {
                    when (it.branch) {
                        SpawnMarkerBranch.Parent -> stepResult.continuation
                        SpawnMarkerBranch.Child -> stepResult.child
                    }
                }) {
                    // It should not be possible not to have incon data for a spawn - they always run to completion
                    "'spawn' step missing from incon. Incon data for ${thisTask.id.name} is malformed."
                }
                // We really shouldn't get a restart, but if we do, just keep restoring. They don't directly contribute to task history.
                is TaskStepResult.Restart -> stepResult.continuation
                is TaskStepResult.Complete -> throw IllegalArgumentException("Extra restore data for completed task")
            }
        }
        // If there's no history data left, we've reached the active step
        return thisTask
    }

    private inline fun <reified S : TaskHistoryStep> TaskHistoryProvider.provideStep(block: (S) -> Task<*>): Task<*>? =
        // Provide a general TaskHistoryStep, so deserializer interprets the class discriminator field "type"
        // Then verify it was the exact type we expected
        provide<TaskHistoryStep>()?.let { block(it as S) }

    override fun toString(): String = id.toString()

    @Serializable
    sealed interface TaskHistoryStep {
        @Serializable
        @SerialName("await")
        object AwaitMarker : TaskHistoryStep

        // This is a hack to get a "polymorphic" interface with the type parameter, for ReadMarker.
        // By constructing a KType with this, we can both include a concrete type for T,
        // avoiding the "no serializer in polymorphic scope of Any" error caused by erasure,
        // while also including the class discriminator "type": "read".
        @Serializable
        sealed interface TaskHistoryStepWithValue<T> : TaskHistoryStep

        @Serializable
        @SerialName("read")
        data class ReadMarker<T>(val value: T) : TaskHistoryStepWithValue<T> {
            companion object {
                fun concreteType(valueType: KType) = TaskHistoryStepWithValue::class.withArg(valueType)
            }
        }

        @Serializable
        @SerialName("spawn")
        data class SpawnMarker(val branch: SpawnMarkerBranch) : TaskHistoryStep
        enum class SpawnMarkerBranch {
            @SerialName("parent")
            Parent,
            @SerialName("child")
            Child,
        }
    }
}
