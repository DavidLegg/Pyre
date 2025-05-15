package org.example.gov.nasa.jpl.pyre.core

import org.example.gov.nasa.jpl.pyre.conditions.FinconCollector
import org.example.gov.nasa.jpl.pyre.conditions.InconProvider
import org.example.gov.nasa.jpl.pyre.io.JsonValue
import org.example.gov.nasa.jpl.pyre.io.JsonValue.*
import org.example.gov.nasa.jpl.pyre.state.SimulationState

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
    val step: TaskStep<T>
    fun save(finconCollector: FinconCollector)
    fun restore(inconProvider: InconProvider): Sequence<Task<*>>

    class RootTaskId(val name: String, val parent: RootTaskId?) {
        fun conditionKeys() : Sequence<String> = generateSequence(this) { it.parent }.map { it.name }
    }
    class TaskId(val rootId: RootTaskId, val stepNumber: Int) {
        fun nextStep() = TaskId(rootId, stepNumber + 1)
        fun child(childName: String) = TaskId(RootTaskId(childName, rootId), 0)
    }

    // Explanation:
    // A "pure" task step doesn't save the information necessary to resume.
    // These are easy to write, but insufficient to run without first wrapping with additional save/restore functions.
    // This is what a "full" task step has - note the continuations are full-fledged Tasks, not merely task steps.

    // TODO: Should there be any checking of things like task name collisions?
    //   Maybe that can be handled elsewhere...
    sealed interface PureTaskStep<T> {
        data class Complete<T>(val value: T) : PureTaskStep<T>
        data class Read<V, T>(val cell: SimulationState.CellHandle<V>, val continuation: (V) -> PureTaskStep<T>) : PureTaskStep<T>
        data class Report<T>(val value: JsonValue, val continuation: () -> PureTaskStep<T>) : PureTaskStep<T>
        data class Await<T>(val condition: Condition, val continuation: () -> PureTaskStep<T>) : PureTaskStep<T>
        data class Spawn<S, T>(val childName: String, val child: PureTaskStep<S>, val continuation: () -> PureTaskStep<T>) : PureTaskStep<T>
        // delay? Or should that just be a special case of await?
        // TODO - other task results / steps?
    }

    sealed interface TaskStep<T> {
        data class Complete<T>(val value: T) : TaskStep<T>
        data class Read<V, T>(val cell: SimulationState.CellHandle<V>, val continuation: (V) -> Task<T>) : TaskStep<T>
        data class Report<T>(val value: JsonValue, val continuation: () -> Task<T>) : TaskStep<T>
        data class Await<T>(val condition: Condition, val continuation: () -> Task<T>) : TaskStep<T>
        data class Spawn<S, T>(val child: Task<S>, val continuation: () -> Task<T>) : TaskStep<T>

        companion object Factory {
            fun <T> of(id: TaskId, step: PureTaskStep<T>, saveData: () -> List<JsonValue>): TaskStep<T> = when (step) {
                is PureTaskStep.Complete -> Complete(step.value)
                is PureTaskStep.Read<*, T> -> ofRead(id, step, saveData)
                is PureTaskStep.Report -> Report(step.value) { Task.of(id.nextStep(), step.continuation(), { saveData() + REPORT_MARKER }) }
                is PureTaskStep.Await -> Await(step.condition) { Task.of(id.nextStep(), step.continuation(), { saveData() + AWAIT_MARKER })}
                is PureTaskStep.Spawn<*, T> -> ofSpawn(id, step, saveData)
            }

            private fun <V, T> ofRead(
                id: TaskId,
                step: PureTaskStep.Read<V, T>,
                saveData: () -> List<JsonValue>
            ) = Read(step.cell) {
                Task.of(id.nextStep(), step.continuation(it), { saveData() + conditionReadEntry(step.cell.serializer.serialize(it)) })
            }

            private fun <S, T> ofSpawn(
                id: TaskId,
                step: PureTaskStep.Spawn<S, T>,
                saveData: () -> List<JsonValue>
            ) = Spawn(Task.of(id.child(step.childName), step.child, { emptyList() })) {
                Task.of(id.nextStep(), step.continuation(), { saveData() + SPAWN_MARKER })
            }
        }
    }

    companion object {
        private val REPORT_MARKER = conditionEntry("report")
        private val AWAIT_MARKER = conditionEntry("await")
        private val SPAWN_MARKER = conditionEntry("spawn")

        fun <T> of(name: String, step: PureTaskStep<T>) = of(TaskId(RootTaskId(name, null), 0), step, { emptyList() })

        private fun <T> of(id: TaskId, step: PureTaskStep<T>, saveData: () -> List<JsonValue>) = object : Task<T> {
            override val id: TaskId
                get() = id
            override val step: TaskStep<T>
                get() = TaskStep.of(id, step, saveData)

            override fun save(finconCollector: FinconCollector) {
                finconCollector.report(id.rootId.conditionKeys(), JsonArray(saveData()))
            }

            override fun restore(inconProvider: InconProvider): Sequence<Task<*>> {
                // If there's no incon data for this task or step, start here
                val restoreData = inconProvider.get(id.rootId.conditionKeys()) ?: return sequenceOf(this)
                val restoreDatum = (restoreData as JsonArray).values.getOrNull(id.stepNumber) ?: return sequenceOf(this)
                val restoreDatumMap = (restoreDatum as JsonMap).values
                val restoreDatumType = (restoreDatumMap["type"] as JsonString).value
                val restoreDatumValue = restoreDatumMap["value"]
                fun requireType(requiredType: String) =
                    require(restoreDatumType == requiredType) { "Expected restore datum of type \"$requiredType\"" }

                return when (step) {
                    is PureTaskStep.Complete -> emptySequence()
                    is PureTaskStep.Read<*, T> -> {
                        requireType("read")
                        return restoreRead(this.step as TaskStep.Read<*, T>, requireNotNull(restoreDatumValue), inconProvider)
                    }
                    is PureTaskStep.Report -> {
                        requireType("report")
                        return (this.step as TaskStep.Report).continuation().restore(inconProvider)
                    }
                    is PureTaskStep.Await -> {
                        requireType("await")
                        return (this.step as TaskStep.Await).continuation().restore(inconProvider)
                    }
                    is PureTaskStep.Spawn<*, T> -> {
                        requireType("spawn")
                        val spawnStep = this.step as TaskStep.Spawn<*, T>
                        return spawnStep.child.restore(inconProvider) + spawnStep.continuation().restore(inconProvider)
                    }
                }
            }

            private fun <V> restoreRead(
                step: TaskStep.Read<V, T>,
                restoreDatum: JsonValue,
                inconProvider: InconProvider
            ) = step.continuation(step.cell.serializer.deserialize(restoreDatum).getOrThrow()).restore(inconProvider)
        }

        private fun conditionEntry(type: String) = JsonMap(mapOf("type" to JsonString(type)))
        private fun conditionReadEntry(value: JsonValue) = JsonMap(mapOf("type" to JsonString("read"), "value" to value))
    }
}
