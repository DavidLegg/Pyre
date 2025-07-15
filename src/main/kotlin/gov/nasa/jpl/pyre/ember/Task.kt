package gov.nasa.jpl.pyre.ember

import gov.nasa.jpl.pyre.ember.CellSet.CellHandle
import gov.nasa.jpl.pyre.ember.FinconCollector.Companion.accrue
import gov.nasa.jpl.pyre.ember.FinconCollector.Companion.withPrefix
import gov.nasa.jpl.pyre.ember.InconProvider.Companion.get
import gov.nasa.jpl.pyre.ember.PureTask.TaskHistoryStep.*
import gov.nasa.jpl.pyre.ember.Task.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

typealias PureTaskStep<T> = () -> PureStepResult<T>

/*
    TODO: Consider ways to refactor Task history collection and restoration.

    Right now, task history is built up in a callback as the task runs, and serialized in two passes.
    The first pass serializes the read values, and the second pass the full task history.
    Conversely, task restoration first deserializes the task history, then the read values.

    This works, but it requires some ugly warts on the incon/fincon interfaces.

    Consider a different approach, where a task can serialize its history in a single pass, by using ReadMarker<V>
    with the actual (not serialized to JsonElement) value in it.
    Then, consider a custom task deserializer, or else an incremental deserialize method for InconProvider, inverse to `accrue` for FinconCollector.

    In fact, perhaps these incremental incon/fincon methods could be utility interfaces on top of the basic incon/fincon interfaces...
    Something where you incrementally report to the incremental interface, which does a single final report to the base interface?
    Then they could be written as just an extension method on the interfaces, and JsonCondition could be simplified.
 */

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
    fun save(finconCollector: FinconCollector)
    fun restore(inconProvider: InconProvider): Sequence<Task<*>>

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
        data class Report<T>(val value: JsonElement, val continuation: PureTaskStep<T>) : PureStepResult<T> {
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
        data class Report<T>(val value: JsonElement, val continuation: Task<T>) : TaskStepResult<T> {
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
    private val saveData: (FinconCollector) -> Unit,
    rootTask: PureTask<T>?
) : Task<T> {
    private val rootTask: PureTask<T> = rootTask ?: this

    override fun runStep(): TaskStepResult<T> {
        return when (val stepResult = step()) {
            is PureStepResult.Complete -> TaskStepResult.Complete(stepResult.value)
            is PureStepResult.Read<*, *, T> -> runRead(stepResult)
            is PureStepResult.Emit<*, *, T> -> runEmit(stepResult)
            is PureStepResult.Report -> TaskStepResult.Report(
                stepResult.value,
                PureTask(id.nextStep(), stepResult.continuation, { saveData(it); it.accrue(value=ReportMarker) }, rootTask)
            )
            is PureStepResult.Delay -> TaskStepResult.Delay(
                stepResult.time,
                PureTask(id.nextStep(), stepResult.continuation, { saveData(it); it.accrue(value=DelayMarker) }, rootTask)
            )
            is PureStepResult.Await -> TaskStepResult.Await(
                stepResult.condition,
                PureTask(id.nextStep(), stepResult.continuation, { saveData(it); it.accrue(value=AwaitMarker) }, rootTask)
            )
            is PureStepResult.Spawn<*, T> -> runSpawn(stepResult)
            is PureStepResult.Restart -> rootTask.runStep()
        }
    }

    private fun <V, E> runRead(step: PureStepResult.Read<V, E, T>) = TaskStepResult.Read(step.cell) { value ->
        PureTask(
            id.nextStep(),
            // Important: step.continuation is deferred to Task.runStep.
            // This means the outer Read.continuation can safely be called immediately after reading,
            // without invoking client code prematurely.
            { step.continuation(value) },
            { finconCollector ->
                saveData(finconCollector)
                finconCollector.accrue(value=ReadMarker(finconCollector.encode(value, step.cell.valueType)))
            },
            rootTask,
        )
    }

    private fun <V, E> runEmit(step: PureStepResult.Emit<V, E, T>) = TaskStepResult.Emit(
        step.cell,
        step.effect,
        PureTask(id.nextStep(), step.continuation, { saveData(it); it.accrue(value=EmitMarker) }, rootTask)
    )

    private fun <S> runSpawn(step: PureStepResult.Spawn<S, T>) = TaskStepResult.Spawn(
        PureTask(id.child(step.childName), step.child, { saveData(it); it.accrue(value=SpawnMarker(true)) }, null),
        PureTask(id.nextStep(), step.continuation, { saveData(it); it.accrue(value=SpawnMarker(false)) }, rootTask)
    )

    override fun save(finconCollector: FinconCollector) {
        val conditionKeys = id.rootId.conditionKeys()

        // Report my fincon state
        var historyCollector = finconCollector
        for (key in conditionKeys) {
            historyCollector = historyCollector.withPrefix(key)
        }
        saveData(historyCollector)

        // If this is a child task, report to root task that I need to be spawned
        if (id.rootId.parent != null) {
            var taskId = id.rootId
            while (taskId.parent != null) taskId = taskId.parent
            finconCollector.accrue(taskId.conditionKeys() + "children", value=conditionKeys)
        }
    }

    override fun restore(inconProvider: InconProvider) = restore(inconProvider, id)

    private fun restore(inconProvider: InconProvider, targetTaskId: TaskId): Sequence<Task<*>> {
        val targetConditionKeys = targetTaskId.rootId.conditionKeys()
        val result = mutableListOf<Task<*>>()
        // Restore this task itself, if there's incon data for it.
        inconProvider.get<List<TaskHistoryStep>>(targetConditionKeys)?.let {
            result.add(restoreSingle(it, inconProvider))
        }
        // For any child reported in the fincon, call restore again, using that child's id
        // to get the state history which spawned and later ran that child.
        inconProvider.get<List<List<String>>>(targetConditionKeys + "children")?.forEach {
            // Since that child is reported as needing to be restored, throw an error if there's no incon data for it
            result.addAll(requireNotNull(rootTask.restore(
                inconProvider, constructTaskId(it))))
        }
        return result.asSequence()
    }

    private fun constructTaskId(components: List<String>): TaskId {
        var rootId: RootTaskId? = null
        components.forEach { rootId = RootTaskId(it, rootId) }
        requireNotNull(rootId)
        return TaskId(rootId, 0)
    }

    private fun restoreSingle(restoreData: List<TaskHistoryStep>, inconProvider: InconProvider): Task<*> {
        // If there's no incon data left, we've reached the active step
        val historyStep = restoreData.firstOrNull() ?: return this
        val remainingRestoreData = restoreData.subList(1, restoreData.size)

        return with (this.runStep()) {
            when (this) {
                is TaskStepResult.Complete -> {
                    throw IllegalArgumentException("Extra restore data for completed task")
                }
                is TaskStepResult.Read<*, *, T> -> {
                    require(historyStep is ReadMarker)
                    restoreRead(this, historyStep.value, remainingRestoreData, inconProvider)
                }
                is TaskStepResult.Emit<*, *, T> -> {
                    require(historyStep is EmitMarker)
                    (continuation as PureTask<T>).restoreSingle(remainingRestoreData, inconProvider)
                }
                is TaskStepResult.Report -> {
                    require(historyStep is ReportMarker)
                    (continuation as PureTask<T>).restoreSingle(remainingRestoreData, inconProvider)
                }
                is TaskStepResult.Delay -> {
                    require(historyStep is DelayMarker)
                    (continuation as PureTask<T>).restoreSingle(remainingRestoreData, inconProvider)
                }
                is TaskStepResult.Await -> {
                    require(historyStep is AwaitMarker)
                    (continuation as PureTask<T>).restoreSingle(remainingRestoreData, inconProvider)
                }
                is TaskStepResult.Spawn<*, T> -> {
                    require(historyStep is SpawnMarker)
                    if (historyStep.onChild) {
                        requireNotNull((child as PureTask<*>).restoreSingle(remainingRestoreData, inconProvider))
                    } else {
                        requireNotNull((continuation as PureTask<T>).restoreSingle(remainingRestoreData, inconProvider))
                    }
                }
            }
        }
    }

    private fun <V, E> restoreRead(
        step: TaskStepResult.Read<V, E, T>,
        historyValue: JsonElement,
        remainingRestoreData: List<TaskHistoryStep>,
        inconProvider: InconProvider,
    ) = (step.continuation(inconProvider.decode(historyValue, step.cell.valueType)) as PureTask<T>)
        .restoreSingle(remainingRestoreData, inconProvider)

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

        @Serializable
        @SerialName("read")
        data class ReadMarker(val value: JsonElement) : TaskHistoryStep

        @Serializable
        @SerialName("spawn")
        data class SpawnMarker(val onChild: Boolean) : TaskHistoryStep
    }
}
