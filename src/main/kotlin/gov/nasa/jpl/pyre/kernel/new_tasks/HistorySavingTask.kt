package gov.nasa.jpl.pyre.kernel.new_tasks

import gov.nasa.jpl.pyre.kernel.Cell
import gov.nasa.jpl.pyre.kernel.Effect
import gov.nasa.jpl.pyre.kernel.MutableSnapshot
import gov.nasa.jpl.pyre.kernel.MutableSnapshot.Companion.report
import gov.nasa.jpl.pyre.kernel.MutableSnapshot.Companion.within
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.NameOperations.asSequence
import gov.nasa.jpl.pyre.kernel.Snapshot
import gov.nasa.jpl.pyre.kernel.Snapshot.Companion.provide
import gov.nasa.jpl.pyre.kernel.Snapshot.Companion.within
import gov.nasa.jpl.pyre.kernel.new_tasks.HistorySavingTask.TaskHistoryStep.*
import gov.nasa.jpl.pyre.kernel.new_tasks.TaskHistoryCollector.Companion.report
import gov.nasa.jpl.pyre.kernel.new_tasks.TaskHistoryProvider.Companion.provide
import gov.nasa.jpl.pyre.utilities.Reflection.withArg
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KType

class HistorySavingTask private constructor(
    override val name: Name,
    override val rootTask: Task,
    private val pureStepFunction: PureTaskStep,
    private val collectPriorHistory: TaskHistoryCollector.() -> Unit
) : Task {

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
                HistorySavingTask(
                    name,
                    rootTask,
                    { stepResult },
                    // Capture history here without an AwaitMarker; rewaiting implies the await is unfinished.
                    { collectHistory() }
                ),
                // To continue this task, run the continuation
                HistorySavingTask(
                    name,
                    rootTask,
                    stepResult.continuation,
                    // Add an AwaitMarker to this history, since continuing implies the await is finished.
                    { collectHistory(); report<TaskHistoryStep>(AwaitMarker) }
                ),
            )
            is PureStepResult.Spawn -> TaskStepResult.Spawn(
                HistorySavingTask(
                    stepResult.childName,
                    rootTask,
                    stepResult.child,
                    { collectHistory(); report<TaskHistoryStep>(SpawnMarker(SpawnMarkerBranch.Child)) }
                ),
                HistorySavingTask(
                    name,
                    rootTask,
                    stepResult.continuation,
                    { collectHistory(); report<TaskHistoryStep>(SpawnMarker(SpawnMarkerBranch.Parent)) }
                )
            )
            // TODO: Think about this again... Should we be wrapping rootTask in a HistorySavingTask with no history?
            is PureStepResult.Restart -> TaskStepResult.Restart(rootTask)
        }
    }

    override fun saveTo(snapshot: MutableSnapshot) {
        // TODO: Do we need to indicate something to the root task?
        snapshot.within(name.asSequence()).report(MutableTaskHistory().apply(collectPriorHistory))
    }

    override fun restoreFrom(snapshot: Snapshot): List<Task> {
        // TODO: Do we need to look for children somehow?
        val snapshotHistory = requireNotNull(snapshot.within(name.asSequence()).provide<TaskHistory>()).provider()

        val restorationActions = object : BasicTaskActions {
            override fun <V> read(cell: Cell<V>): V =
                requireNotNull(snapshotHistory.provide<ReadMarker<V>>(ReadMarker.concreteType(cell.valueType))) {
                    "No restore data available to read $cell! Incon data is malformed for task $name."
                }.value
            override fun <V> emit(cell: Cell<V>, effect: Effect<V>) { /* ignore */ }
            override fun <V> report(value: V) { /* ignore */ }
        }

        TODO("Continue reworking task restoration from tasks/Task.kt")
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