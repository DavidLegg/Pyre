package gov.nasa.jpl.pyre.ember

import gov.nasa.jpl.pyre.coals.Reflection.withArg
import gov.nasa.jpl.pyre.ember.CellSet.CellHandle
import gov.nasa.jpl.pyre.ember.FinconCollectingContext.Companion.report
import gov.nasa.jpl.pyre.ember.FinconCollector.Companion.within
import gov.nasa.jpl.pyre.ember.InconProvider.Companion.within
import gov.nasa.jpl.pyre.ember.InconProvidingContext.Companion.provide
import gov.nasa.jpl.pyre.ember.PureTask.TaskHistoryStep.*
import gov.nasa.jpl.pyre.ember.Task.*
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

    data class RootTaskId(val name: String, val parent: RootTaskId?) {
        fun conditionKeys() : Sequence<String> = generateSequence(this) { it.parent }
            .map { it.name }
            .toList()
            .asReversed()
            .asSequence()

        override fun toString() = conditionKeys()
            .map { if (it.contains(Regex("[ .\"\']"))) "($it)" else it }
            .joinToString(".")
    }
    data class TaskId(val rootId: RootTaskId, val stepNumber: Int) {
        fun nextStep() = TaskId(rootId, stepNumber + 1)
        fun child(childName: String) = TaskId(RootTaskId(childName, rootId), 0)

        override fun toString() = "$rootId[$stepNumber]"
    }

    // Explanation:
    // A "pure" task step doesn't save the information necessary to resume.
    // These are easy to write, but insufficient to run without first wrapping with additional save/restore functions.
    // This is what a "full" task step has - note the continuations are full-fledged Tasks, not merely task steps.

    // TODO: Should there be any checking of things like task name collisions?
    //   Maybe that can be handled elsewhere...
    sealed interface PureStepResult<T> {
        data class Complete<T>(val value: T) : PureStepResult<T> {
            override fun toString() = "Complete($value)"
        }
        data class Read<V, E, T>(val cell: CellHandle<V, E>, val continuation: (V) -> PureStepResult<T>) : PureStepResult<T> {
            override fun toString() = "Read(${cell.name})"
        }
        data class Emit<V, E, T>(val cell: CellHandle<V, E>, val effect: E, val continuation: PureTaskStep<T>) : PureStepResult<T> {
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
        data class Spawn<S, T>(val childName: String, val child: PureTaskStep<S>, val continuation: PureTaskStep<T>) : PureStepResult<T> {
            override fun toString() = "Spawn($childName)"
        }
        class Restart<T> : PureStepResult<T> {
            override fun toString() = "Restart"
        }
        // TODO - other task results / steps?
    }

    sealed interface TaskStepResult<T> {
        data class Complete<T>(val value: T) : TaskStepResult<T> {
            override fun toString() = "Complete($value)"
        }
        data class Read<V, E, T>(val cell: CellHandle<V, E>, val continuation: (V) -> Task<T>) : TaskStepResult<T> {
            override fun toString() = "Read(${cell.name})"
        }
        data class Emit<V, E, T>(val cell: CellHandle<V, E>, val effect: E, val continuation: Task<T>) : TaskStepResult<T> {
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
        fun <T> of(name: String, step: PureTaskStep<T>): Task<T> {
            return PureTask(TaskId(RootTaskId(name, null), 0), step, {}, null)
        }
    }
}


// Although you could technically implement Task from scratch,
// it's so complicated in practice that everyone basically uses PureTask.
// Even I use it to test the ember engine, since writing Task from scratch would be so miserable.
// For that reason, I'm keeping this in ember instead of spark.
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
            is PureStepResult.Read<*, *, T> -> runRead(stepResult)
            is PureStepResult.Emit<*, *, T> -> runEmit(stepResult)
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

    private fun <V, E> runRead(step: PureStepResult.Read<V, E, T>) = TaskStepResult.Read(step.cell) { value ->
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

    private fun <V, E> runEmit(step: PureStepResult.Emit<V, E, T>) = TaskStepResult.Emit(
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

    private fun constructTaskId(components: List<String>): TaskId {
        var rootId: RootTaskId? = null
        components.forEach { rootId = RootTaskId(it, rootId) }
        requireNotNull(rootId)
        return TaskId(rootId, 0)
    }

    private fun restoreSingle(inconProvider: InconProvidingContext): Task<*> {
        // If there's no incon data left, we've reached the active step
        if (!inconProvider.inconExists()) return this
        return with (this.runStep()) {
            when (this) {
                is TaskStepResult.Complete -> throw IllegalArgumentException("Extra restore data for completed task")
                is TaskStepResult.Read<*, *, T> -> restoreRead(this, inconProvider)
                is TaskStepResult.Emit<*, *, T> -> restoreWith<EmitMarker>(inconProvider) {
                    (continuation as PureTask<T>).restoreSingle(inconProvider)
                }
                is TaskStepResult.Report<*, T> -> restoreWith<ReportMarker>(inconProvider) {
                    (continuation as PureTask<T>).restoreSingle(inconProvider)
                }
                is TaskStepResult.Delay -> restoreWith<DelayMarker>(inconProvider) {
                    (continuation as PureTask<T>).restoreSingle(inconProvider)
                }
                is TaskStepResult.Await -> restoreWith<AwaitMarker>(inconProvider) {
                    (continuation as PureTask<T>).restoreSingle(inconProvider)
                }
                is TaskStepResult.Spawn<*, T> -> restoreWith<SpawnMarker>(inconProvider) {
                    when (it.branch) {
                        SpawnMarkerBranch.Parent -> requireNotNull((continuation as PureTask<T>).restoreSingle(inconProvider))
                        SpawnMarkerBranch.Child -> requireNotNull((child as PureTask<*>).restoreSingle(inconProvider))
                    }
                }
                is TaskStepResult.NoOp<T> ->
                    // NoOp's don't contribute to task history themselves, just keep restoring with the next step
                    (continuation as PureTask<T>).restoreSingle(inconProvider)
            }
        }
    }

    private fun <V, E> restoreRead(
        step: TaskStepResult.Read<V, E, T>,
        inconProvider: InconProvidingContext,
    ): Task<*> = inconProvider.provide<ReadMarker<V>>(ReadMarker.concreteType(step.cell.valueType))?.let {
        (step.continuation(it.value) as PureTask<T>).restoreSingle(inconProvider)
    } ?: this

    private inline fun <reified T> restoreWith(inconProvider: InconProvidingContext, restoreBlock: (T) -> Task<*>): Task<*> {
        // Provide a general TaskHistoryStep, so deserializer interprets the class discriminator field "type"
        return inconProvider.provide<TaskHistoryStep>()?.let {
            // Then verify it was the exact type we expected
            restoreBlock(it as T)
        } ?: this
    }

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
