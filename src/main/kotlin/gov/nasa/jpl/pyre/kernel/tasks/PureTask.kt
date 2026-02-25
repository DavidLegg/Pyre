package gov.nasa.jpl.pyre.kernel.tasks

import gov.nasa.jpl.pyre.kernel.Cell
import gov.nasa.jpl.pyre.kernel.Effect
import gov.nasa.jpl.pyre.kernel.MutableSnapshot
import gov.nasa.jpl.pyre.kernel.MutableSnapshot.Companion.report
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.Snapshot
import gov.nasa.jpl.pyre.kernel.Snapshot.Companion.provide
import gov.nasa.jpl.pyre.kernel.tasks.PureTask.TaskHistoryStep.*
import gov.nasa.jpl.pyre.kernel.tasks.TaskHistoryCollector.Companion.report
import gov.nasa.jpl.pyre.kernel.tasks.TaskHistoryProvider.Companion.provide
import gov.nasa.jpl.pyre.utilities.Reflection.withArg
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KType

/**
 * A "pure" task is a task driven by [PureTaskStep]s.
 * It records its history to save to a snapshot, and can replay that history to resume from a snapshot.
 */
class PureTask private constructor(
    override val name: Name,
    rootTask: Task?,
    private val pureStepFunction: PureTaskStep,
    private val collectPriorHistory: TaskHistoryCollector.() -> Unit,
) : Task {
    override val rootTask: Task = rootTask ?: this

    // public users of this class can build root tasks, and only root tasks build non-root tasks
    constructor(name: Name, pureStepFunction: PureTaskStep) :
            this(name, null, pureStepFunction, {})

    override fun runStep(actions: BasicTaskActions): TaskStepResult {
        val thisStepHistory = mutableListOf<Pair<ReadMarker<*>, KType>>()
        val historyCapturingActions = object : BasicTaskActions {
            // Capture reads so we can record their value
            override fun <V> read(cell: Cell<V>): V = actions.read(cell).also {
                thisStepHistory += ReadMarker(it) to ReadMarker.concreteType(cell.valueType)
            }
            // Emit and Report don't need to write any history; they'll just run again when restoring.
            override fun <V> emit(cell: Cell<V>, effect: Effect<V>) = actions.emit(cell, effect)
            override fun <V> report(value: V) = actions.report(value)
        }
        fun TaskHistoryCollector.collectHistory() {
            collectPriorHistory()
            thisStepHistory.forEach { report(it.first, it.second) }
        }
        return when (val stepResult = pureStepFunction.run(historyCapturingActions)) {
            PureStepResult.Complete -> TaskStepResult.Complete
            is PureStepResult.Await -> TaskStepResult.Await(
                stepResult.condition,
                // To rewait this task, just return the step result
                PureTask(
                    name,
                    rootTask,
                    { stepResult },
                    // Capture history here without an AwaitMarker; rewaiting implies the await is unfinished.
                    { collectHistory() }
                ),
                // To continue this task, run the continuation
                PureTask(
                    name,
                    rootTask,
                    stepResult.continuation,
                    // Add an AwaitMarker to this history, since continuing implies the await is finished.
                    { collectHistory(); report<TaskHistoryStep>(AwaitMarker) }
                ),
            )
            is PureStepResult.Spawn -> TaskStepResult.Spawn(
                PureTask(
                    stepResult.childName,
                    rootTask,
                    stepResult.child,
                    { collectHistory(); report<TaskHistoryStep>(SpawnMarker(SpawnMarkerBranch.Child)) }
                ),
                PureTask(
                    name,
                    rootTask,
                    stepResult.continuation,
                    { collectHistory(); report<TaskHistoryStep>(SpawnMarker(SpawnMarkerBranch.Parent)) }
                )
            )
            is PureStepResult.Restart -> TaskStepResult.Restart(rootTask)
        }
    }

    override fun saveTo(snapshot: MutableSnapshot) {
        snapshot.apply {
            // Record our own name
            report("name", value=name)
            // Record the root task from which to restore this task
            report("root", value=rootTask.name)
            // Record the history of all the steps this task took
            report("history", value=MutableTaskHistory().apply(collectPriorHistory))
        }
    }

    override fun restoreFrom(snapshot: Snapshot): Task {
        // Reverse the process of saveTo. First, verify this is the correct root task to restore from:
        val resultName = requireNotNull(snapshot.provide<Name>("name")) {
            "Malformed incon: 'name' field is missing from a task"
        }
        val resultRootName = requireNotNull(snapshot.provide<Name>("root")) {
            "Malformed incon: 'root' field is missing from task $resultName"
        }
        require(resultRootName == name) {
            "Internal error! Attempting to restore $resultName with root task $name, but it descends from root task $resultRootName"
        }

        val historyProvider = requireNotNull(snapshot.provide<TaskHistory>("history")) {
            "Malformed incon: 'history' field is missing from task $resultName"
        }.provider()

        val restorationActions = object : BasicTaskActions {
            override fun <V> read(cell: Cell<V>): V =
                requireNotNull(historyProvider.provide<ReadMarker<V>>(ReadMarker.concreteType(cell.valueType))) {
                    "Malformed incon: task $resultName read $cell, but no read data is available in the snapshot"
                }.value
            override fun <V> emit(cell: Cell<V>, effect: Effect<V>) { /* ignore */ }
            override fun <V> report(value: V) { /* ignore */ }
        }

        var result: Task = this
        while (historyProvider.hasNext()) {
            result = when (val stepResult = result.runStep(restorationActions)) {
                is TaskStepResult.Await -> {
                    // If an await step has incon data, it completed, so continue the task
                    historyProvider.provide<AwaitMarker>()?.let { stepResult.continuation } ?: stepResult.rewait
                }
                is TaskStepResult.Spawn -> {
                    // Spawns always run to completion, so there must be incon data for it.
                    val marker = requireNotNull(historyProvider.provide<SpawnMarker>()) {
                        "Malformed incon: 'spawn' action taken by task $resultName does not have a step in incon history"
                    }
                    when (marker.branch) {
                        SpawnMarkerBranch.Parent -> stepResult.continuation
                        SpawnMarkerBranch.Child -> stepResult.child
                    }
                }
                // Restarts and completes shouldn't happen.
                // A restart should have trimmed history; a complete should have eliminated it.
                is TaskStepResult.Restart -> throw IllegalArgumentException(
                    "Malformed incon: task $resultName restarted while restoring from a snapshot"
                )
                is TaskStepResult.Complete -> throw IllegalArgumentException(
                    "Malformed incon: task $resultName completed while restoring from a snapshot"
                )
            }
        }
        // If there's no history data left, we've reached the active step
        return result
    }

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
