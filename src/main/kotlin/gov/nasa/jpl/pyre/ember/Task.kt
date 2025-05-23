package gov.nasa.jpl.pyre.ember

import gov.nasa.jpl.pyre.ember.JsonValue.*
import gov.nasa.jpl.pyre.ember.CellSet.CellHandle
import gov.nasa.jpl.pyre.ember.Task.*

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
    fun restore(inconProvider: InconProvider): Sequence<Task<*>>?
    fun isCompleted(): Boolean

    data class RootTaskId(val name: String, val parent: RootTaskId?) {
        fun conditionKeys() : Sequence<String> = generateSequence(this) { it.parent }
            .map { it.name }
            .toList()
            .asReversed()
            .asSequence()

        override fun toString() = conditionKeys().joinToString(".")
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
        data class Emit<V, E, T>(val cell: CellHandle<V, E>, val effect: E, val continuation: () -> PureStepResult<T>) : PureStepResult<T> {
            override fun toString() = "Emit(${cell.name}, $effect)"
        }
        data class Report<T>(val value: JsonValue, val continuation: () -> PureStepResult<T>) : PureStepResult<T> {
            override fun toString() = "Report($value)"
        }
        data class Delay<T>(val time: Duration, val continuation: () -> PureStepResult<T>) : PureStepResult<T> {
            override fun toString() = "Delay($time)"
        }
        data class Await<T>(val condition: Condition, val continuation: () -> PureStepResult<T>) : PureStepResult<T> {
            override fun toString() = "Await($condition)"
        }
        data class Spawn<S, T>(val childName: String, val child: () -> PureStepResult<S>, val continuation: () -> PureStepResult<T>) : PureStepResult<T> {
            override fun toString() = "Spawn($childName)"
        }
        class Restart<T> : PureStepResult<T> {
            override fun toString() = "Restart"
        }
        // TODO - other task results / steps?
    }

    sealed interface TaskStepResult<T> {
        data class Complete<T>(val value: T, val continuation: Task<T>) : TaskStepResult<T> {
            override fun toString() = "Complete($value)"
        }
        data class Read<V, E, T>(val cell: CellHandle<V, E>, val continuation: (V) -> Task<T>) : TaskStepResult<T> {
            override fun toString() = "Read(${cell.name})"
        }
        data class Emit<V, E, T>(val cell: CellHandle<V, E>, val effect: E, val continuation: Task<T>) : TaskStepResult<T> {
            override fun toString() = "Emit(${cell.name}, $effect)"
        }
        data class Report<T>(val value: JsonValue, val continuation: Task<T>) : TaskStepResult<T> {
            override fun toString() = "Report($value)"
        }
        data class Delay<T>(val time: Duration, val continuation: Task<T>) : TaskStepResult<T> {
            override fun toString() = "Delay($time)"
        }
        data class Await<T>(val condition: Condition, val continuation: Task<T>) : TaskStepResult<T> {
            override fun toString() = "Await($condition)"
        }
        data class Spawn<S, T>(val child: Task<S>, val continuation: Task<T>) : TaskStepResult<T> {
            override fun toString() = "Spawn($child)"
        }
    }

    companion object {
        fun <T> of(name: String, step: () -> PureStepResult<T>): Task<T> {
            return PureTask(TaskId(RootTaskId(name, null), 0), step, { emptyList() }, null)
        }
    }
}


// Although you could technically implement Task from scratch,
// it's so complicated in practice that everyone basically uses PureTask.
// Even I use it to test the ember engine, since writing Task from scratch would be so miserable.
// For that reason, I'm keeping this in ember instead of spark.
private class PureTask<T> : Task<T> {
    override val id: TaskId
    private val step: () -> PureStepResult<T>
    private val saveData: () -> List<JsonValue>
    private val rootTask: PureTask<T>

    constructor(
        id: TaskId,
        step: () -> PureStepResult<T>,
        saveData: () -> List<JsonValue>,
        rootTask: PureTask<T>?
    ) {
        this.id = id
        this.step = step
        this.saveData = saveData
        this.rootTask = rootTask ?: this
    }

    override fun runStep(): TaskStepResult<T> {
        return when (val stepResult = step()) {
            is PureStepResult.Complete -> TaskStepResult.Complete(
                stepResult.value,
                completedTask(id.nextStep(), stepResult.value, { saveData() + COMPLETE_MARKER })
            )
            is PureStepResult.Read<*, *, T> -> runRead(stepResult)
            is PureStepResult.Emit<*, *, T> -> runEmit(stepResult)
            is PureStepResult.Report -> TaskStepResult.Report(
                stepResult.value,
                PureTask(id.nextStep(), stepResult.continuation, { saveData() + REPORT_MARKER }, rootTask)
            )
            is PureStepResult.Delay -> TaskStepResult.Delay(
                stepResult.time,
                PureTask(id.nextStep(), stepResult.continuation, { saveData() + DELAY_MARKER }, rootTask)
            )
            is PureStepResult.Await -> TaskStepResult.Await(
                stepResult.condition,
                PureTask(id.nextStep(), stepResult.continuation, { saveData() + AWAIT_MARKER }, rootTask)
            )
            is PureStepResult.Spawn<*, T> -> runSpawn(stepResult)
            is PureStepResult.Restart -> rootTask.runStep()
        }
    }

    private fun <V, E> runRead(step: PureStepResult.Read<V, E, T>) = TaskStepResult.Read(step.cell) {
        PureTask(
            id.nextStep(),
            // Important: step.continuation is deferred to Task.runStep.
            // This means the outer Read.continuation can safely be called immediately after reading,
            // without invoking client code prematurely.
            { step.continuation(it) },
            { saveData() + conditionReadEntry(step.cell.serializer.serialize(it)) },
            rootTask,
        )
    }

    private fun <V, E> runEmit(step: PureStepResult.Emit<V, E, T>) = TaskStepResult.Emit(
        step.cell,
        step.effect,
        PureTask(id.nextStep(), step.continuation, { saveData() + EMIT_MARKER }, rootTask)
    )

    private fun <S> runSpawn(step: PureStepResult.Spawn<S, T>) = TaskStepResult.Spawn(
        PureTask(id.child(step.childName), step.child, { saveData() + conditionSpawnEntry(onChild=true) }, null),
        PureTask(id.nextStep(), step.continuation, { saveData() + conditionSpawnEntry(onChild=false) }, rootTask)
    )

    override fun save(finconCollector: FinconCollector) {
        // Report my fincon state
        finconCollector.report(id.rootId.conditionKeys() + "state", value=JsonArray(saveData()))
        // If this is a child task, report to the parent that I need to be spawned
        id.rootId.parent?.let {
            finconCollector.accrue(it.conditionKeys() + "children", value=JsonString(id.rootId.name))
        }
    }

    override fun isCompleted() = false

    override fun restore(inconProvider: InconProvider) = restore(inconProvider, id)

    private fun restore(inconProvider: InconProvider, targetTaskId: TaskId): Sequence<Task<*>>? {
        // if there's no incon data for this task, report that by returning null
        val targetConditionKeys = targetTaskId.rootId.conditionKeys()
        val inconData = inconProvider.get(targetConditionKeys + "state") ?: return null
        // Restore this task itself
        val result = mutableListOf(restoreSingle((inconData as JsonArray).values))
        // For any child reported in the fincon, call restore again, using that child's id
        // to get the state history which spawned and later ran that child.
        (inconProvider.get(targetConditionKeys + "children") as JsonArray?)?.values?.forEach {
            // Since that child is reported as needing to be restored, throw an error if we can't restore it.
            result.addAll(requireNotNull(rootTask.restore(inconProvider, id.child((it as JsonString).value))))
        }
        return result.asSequence()
    }

    private fun restoreSingle(restoreData: List<JsonValue>): Task<*> {
        // If there's no incon data left, we've reached the active step
        val restoreDatum = restoreData.firstOrNull() ?: return this
        val restoreDatumMap = (restoreDatum as JsonMap).values
        val restoreDatumType = (restoreDatumMap["type"] as JsonString).value
        val remainingRestoreData = restoreData.subList(1, restoreData.size)

        fun requireType(requiredType: String) =
            require(restoreDatumType == requiredType) { "Expected restore datum of type \"$requiredType\"" }

        return with (this.runStep()) {
            when (this) {
                is TaskStepResult.Complete -> {
                    requireType("complete")
                    continuation
                }
                is TaskStepResult.Read<*, *, T> -> {
                    requireType("read")
                    restoreRead(this, requireNotNull(restoreDatumMap["value"]), remainingRestoreData)
                }
                is TaskStepResult.Emit<*, *, T> -> {
                    requireType("emit")
                    (continuation as PureTask<T>).restoreSingle(remainingRestoreData)
                }
                is TaskStepResult.Report -> {
                    requireType("report")
                    (continuation as PureTask<T>).restoreSingle(remainingRestoreData)
                }
                is TaskStepResult.Delay -> {
                    requireType("delay")
                    (continuation as PureTask<T>).restoreSingle(remainingRestoreData)
                }
                is TaskStepResult.Await -> {
                    requireType("await")
                    (continuation as PureTask<T>).restoreSingle(remainingRestoreData)
                }
                is TaskStepResult.Spawn<*, T> -> {
                    requireType("spawn")
                    when (val branch = restoreDatumMap["branch"]) {
                        JsonString("child") -> requireNotNull((child as PureTask<*>).restoreSingle(remainingRestoreData))
                        JsonString("parent") -> requireNotNull((continuation as PureTask<T>).restoreSingle(remainingRestoreData))
                        else -> throw IllegalArgumentException("Branch $branch should be \"child\" or \"parent\"")
                    }
                }
            }
        }
    }

    private fun <V, E> restoreRead(
        step: TaskStepResult.Read<V, E, T>,
        restoreDatum: JsonValue,
        remainingRestoreData: List<JsonValue>,
    ) = (step.continuation(step.cell.serializer.deserialize(restoreDatum).getOrThrow()) as PureTask<T>).restoreSingle(remainingRestoreData)

    private fun <T> completedTask(id: TaskId, result: T, saveData: () -> List<JsonValue>) =
        object : Task<T> {
            override val id: TaskId
                get() = id
            override fun runStep(): TaskStepResult<T> = TaskStepResult.Complete(result, this)
            override fun save(finconCollector: FinconCollector) =
                finconCollector.report(id.rootId.conditionKeys() + "state", JsonArray(saveData()))
            override fun isCompleted() = true
            override fun restore(inconProvider: InconProvider) = sequenceOf<Task<T>>(this)
        }

    companion object {
        private val EMIT_MARKER = conditionEntry("emit")
        private val REPORT_MARKER = conditionEntry("report")
        private val DELAY_MARKER = conditionEntry("delay")
        private val AWAIT_MARKER = conditionEntry("await")
        private val COMPLETE_MARKER = conditionEntry("complete")

        private fun conditionEntry(type: String) = JsonMap(mapOf("type" to JsonString(type)))
        private fun conditionReadEntry(value: JsonValue) = JsonMap(mapOf(
            "type" to JsonString("read"),
            "value" to value))
        private fun conditionSpawnEntry(onChild: Boolean) = JsonMap(mapOf(
            "type" to JsonString("spawn"),
            "branch" to JsonString(if (onChild) "child" else "parent")))
    }
}
