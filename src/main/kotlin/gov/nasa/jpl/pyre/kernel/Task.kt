package gov.nasa.jpl.pyre.kernel

import gov.nasa.jpl.pyre.utilities.Reflection.withArg
import gov.nasa.jpl.pyre.kernel.CellSet.CellHandle
import gov.nasa.jpl.pyre.kernel.FinconCollectingContext.Companion.report
import gov.nasa.jpl.pyre.kernel.FinconCollector.Companion.within
import gov.nasa.jpl.pyre.kernel.InconProvider.Companion.within
import gov.nasa.jpl.pyre.kernel.InconProvidingContext.Companion.provide
import gov.nasa.jpl.pyre.kernel.PureTask.TaskHistoryStep.*
import gov.nasa.jpl.pyre.kernel.Task.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KType

typealias PureTaskStep<T> = () -> PureStepResult<T>

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
    fun runStep(): TaskStepResult<T>

    /**
     * Saves this task's history directly to the given collector.
     *
     * Assumes that finconCollector has been restricted using [FinconCollector.within] to the desired location for this task's history.
     *
     * Suggested to use [RootTaskId.conditionKeys] in that location.
     */
    fun save(finconCollector: FinconCollector)

    /**
     * Restore a task from history provided by inconProvider.
     *
     * Assumes that this is the root task from which the given task was spawned,
     * and that inconProvider has been restricted using [InconProvider.within] to the desired task's history.
     *
     * May return a task with a different [TaskId] from this, if restoring a child task.
     */
    fun restore(inconProvider: InconProvider): Task<*>?

    class TaskId(val rootTaskName: Name, val name: Name, val stepNumber: Int) {
        constructor(name: Name) : this(name, name, 0)

        fun nextStep() = TaskId(rootTaskName, name, stepNumber + 1)
        fun child(childName: Name) = TaskId(rootTaskName, childName, 0)

        override fun toString() = "$name[$stepNumber]"
    }

    // Explanation:
    // A "pure" task step doesn't save the information necessary to resume.
    // These are easy to write, but insufficient to run without first wrapping with additional save/restore functions.
    // This is what a "full" task step has - note the continuations are full-fledged Tasks, not merely task steps.

    sealed interface PureStepResult<T> {
        data class Complete<T>(val value: T) : PureStepResult<T> {
            override fun toString() = "Complete($value)"
        }
        data class Read<V, T>(val cell: CellHandle<V>, val continuation: (V) -> PureStepResult<T>) : PureStepResult<T> {
            override fun toString() = "Read(${cell.name})"
        }
        data class Emit<V, T>(val cell: CellHandle<V>, val effect: Effect<V>, val continuation: PureTaskStep<T>) : PureStepResult<T> {
            override fun toString() = "Emit(${cell.name}, $effect)"
        }
        data class Report<V, T>(val value: V, val type: KType, val continuation: PureTaskStep<T>) : PureStepResult<T> {
            override fun toString() = "Report($value)"
        }
        data class Delay<T>(val time: Duration, val continuation: PureTaskStep<T>) : PureStepResult<T> {
            override fun toString() = "Delay($time)"
        }
        data class Await<T>(val condition: () -> Condition, val continuation: PureTaskStep<T>) : PureStepResult<T> {
            override fun toString() = "Await($condition)"
        }
        data class Spawn<S, T>(val childName: Name, val child: PureTaskStep<S>, val continuation: PureTaskStep<T>) : PureStepResult<T> {
            override fun toString() = "Spawn($childName)"
        }
        class Restart<T> : PureStepResult<T> {
            override fun toString() = "Restart"
        }
    }

    sealed interface TaskStepResult<T> {
        data class Complete<T>(val value: T) : TaskStepResult<T> {
            override fun toString() = "Complete($value)"
        }
        data class Read<V, T>(val cell: CellHandle<V>, val continuation: (V) -> Task<T>) : TaskStepResult<T> {
            override fun toString() = "Read(${cell.name})"
        }
        data class Emit<V, T>(val cell: CellHandle<V>, val effect: Effect<V>, val continuation: Task<T>) : TaskStepResult<T> {
            override fun toString() = "Emit(${cell.name}, $effect)"
        }
        data class Report<V, T>(val value: V, val type: KType, val continuation: Task<T>) : TaskStepResult<T> {
            override fun toString() = "Report($value)"
        }
        data class Delay<T>(val time: Duration, val continuation: Task<T>) : TaskStepResult<T> {
            override fun toString() = "Delay($time)"
        }
        // It's useful to users of Await, especially the coroutine builder, to run some setup code with each condition evaluation.
        // That's why we use () -> Condition instead of just Condition.
        data class Await<T>(val condition: () -> Condition, val continuation: Task<T>) : TaskStepResult<T> {
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
        data class NoOp<T>(val continuation: Task<T>) : TaskStepResult<T> {
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
    private val saveData: FinconCollectingContext.() -> Unit,
    rootTask: PureTask<T>?
) : Task<T> {
    private val rootTask: PureTask<T> = rootTask ?: this

    override fun runStep(): TaskStepResult<T> {
        return when (val stepResult = step()) {
            is PureStepResult.Complete -> TaskStepResult.Complete(stepResult.value)
            is PureStepResult.Read<*, T> -> runRead(stepResult)
            is PureStepResult.Emit<*, T> -> runEmit(stepResult)
            is PureStepResult.Report<*, T> -> runReport(stepResult)
            is PureStepResult.Delay -> TaskStepResult.Delay(
                stepResult.time,
                PureTask(id.nextStep(), stepResult.continuation, { saveData(); report<TaskHistoryStep>(DelayMarker) }, rootTask)
            )
            is PureStepResult.Await -> TaskStepResult.Await(
                stepResult.condition,
                PureTask(id.nextStep(), stepResult.continuation, { saveData(); report<TaskHistoryStep>(AwaitMarker) }, rootTask)
            )
            is PureStepResult.Spawn<*, T> -> runSpawn(stepResult)
            is PureStepResult.Restart -> TaskStepResult.NoOp(rootTask)
        }
    }

    private fun <V> runRead(step: PureStepResult.Read<V, T>) = TaskStepResult.Read(step.cell) { value ->
        PureTask(
            id.nextStep(),
            // Important: step.continuation is deferred to Task.runStep.
            // This means the outer Read.continuation can safely be called immediately after reading,
            // without invoking client code prematurely.
            { step.continuation(value) },
            { saveData(); report<TaskHistoryStep>(ReadMarker(value), ReadMarker.concreteType(step.cell.valueType)) },
            rootTask,
        )
    }

    private fun <V> runEmit(step: PureStepResult.Emit<V, T>) = TaskStepResult.Emit(
        step.cell,
        step.effect,
        PureTask(id.nextStep(), step.continuation, { saveData(); report<TaskHistoryStep>(EmitMarker) }, rootTask)
    )

    private fun <V> runReport(step: PureStepResult.Report<V, T>) = TaskStepResult.Report(
        step.value,
        step.type,
        PureTask(id.nextStep(), step.continuation, { saveData(); report<TaskHistoryStep>(ReportMarker) }, rootTask)
    )

    private fun <S> runSpawn(step: PureStepResult.Spawn<S, T>) = TaskStepResult.Spawn(
        PureTask(id.child(step.childName), step.child, { saveData(); report<TaskHistoryStep>(SpawnMarker(SpawnMarkerBranch.Child)) }, null),
        PureTask(id.nextStep(), step.continuation, { saveData(); report<TaskHistoryStep>(SpawnMarker(SpawnMarkerBranch.Parent)) }, rootTask)
    )

    override fun save(finconCollector: FinconCollector) {
        finconCollector.incremental(saveData)
    }

    override fun restore(inconProvider: InconProvider): Task<*>? {
        // Restore this task itself, if there's incon data for it.
        return inconProvider.incremental(::restoreSingle)
    }

    private fun restoreSingle(inconProvider: InconProvidingContext): Task<*> {
        var thisTask: Task<*> = this

        fun <V, T> restoreRead(step: TaskStepResult.Read<V, T>): Task<*>? =
            // Get the read value from the task history and feed that into the continuation to replay the read
            inconProvider.provide<ReadMarker<V>>(ReadMarker.concreteType(step.cell.valueType))?.let {
                step.continuation(it.value)
            }

        // Only run the next step of the task if we have history for it
        while (inconProvider.inconExists()) {
            val nextTask = when (val stepResult = thisTask.runStep()) {
                // When clauses are
                is TaskStepResult.Read<*, *> -> restoreRead(stepResult)
                // For linear non-Read steps, just check the history marker type and move on to the next step
                is TaskStepResult.Emit<*, *> -> inconProvider.provideStep<EmitMarker> { stepResult.continuation }
                is TaskStepResult.Report<*, *> -> inconProvider.provideStep<ReportMarker> { stepResult.continuation }
                is TaskStepResult.Delay -> inconProvider.provideStep<DelayMarker> { stepResult.continuation }
                is TaskStepResult.Await -> inconProvider.provideStep<AwaitMarker> { stepResult.continuation }
                // For spawns, check the task history and take the branch corresponding with this history
                is TaskStepResult.Spawn<*, *> -> inconProvider.provideStep<SpawnMarker> {
                    when (it.branch) {
                        SpawnMarkerBranch.Parent -> stepResult.continuation
                        SpawnMarkerBranch.Child -> stepResult.child
                    }
                }
                // NoOp's don't contribute to task history themselves, just keep restoring with the next step
                is TaskStepResult.NoOp -> stepResult.continuation
                is TaskStepResult.Complete -> throw IllegalArgumentException("Extra restore data for completed task")
            }
            // Since we asserted before running the task that some incon element exists, this null check should never fail.
            thisTask = checkNotNull(nextTask) {
                "Internal error! inconProvider reported inconExists, but no incon was read!"
            }
        }
        // If there's no history data left, we've reached the active step
        return thisTask
    }

    private inline fun <reified S : TaskHistoryStep> InconProvidingContext.provideStep(block: (S) -> Task<*>): Task<*>? =
        // Provide a general TaskHistoryStep, so deserializer interprets the class discriminator field "type"
        // Then verify it was the exact type we expected
        provide<TaskHistoryStep>()?.let { block(it as S) }

    override fun toString(): String = id.toString()

    @Serializable
    sealed interface TaskHistoryStep {
        @Serializable
        @SerialName("emit")
        object EmitMarker : TaskHistoryStep

        @Serializable
        @SerialName("report")
        object ReportMarker : TaskHistoryStep

        @Serializable
        @SerialName("delay")
        object DelayMarker : TaskHistoryStep

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
