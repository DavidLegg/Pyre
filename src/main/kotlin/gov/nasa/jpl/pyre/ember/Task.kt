package org.example.gov.nasa.jpl.pyre.core

import org.example.gov.nasa.jpl.pyre.core.JsonValue.*
import org.example.gov.nasa.jpl.pyre.core.CellSet.CellHandle

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
    sealed interface PureStepResult<T> {
        data class Complete<T>(val value: T) : PureStepResult<T>
        data class Read<V, E, T>(val cell: CellHandle<V, E>, val continuation: (V) -> PureStepResult<T>) : PureStepResult<T>
        data class Emit<V, E, T>(val cell: CellHandle<V, E>, val effect: E, val continuation: () -> PureStepResult<T>) : PureStepResult<T>
        data class Report<T>(val value: JsonValue, val continuation: () -> PureStepResult<T>) : PureStepResult<T>
        data class Delay<T>(val time: Duration, val continuation: () -> PureStepResult<T>) : PureStepResult<T>
        data class Await<T>(val condition: Condition, val continuation: () -> PureStepResult<T>) : PureStepResult<T>
        data class Spawn<S, T>(val childName: String, val child: () -> PureStepResult<S>, val continuation: () -> PureStepResult<T>) : PureStepResult<T>
        // delay? Or should that just be a special case of await?
        // TODO - other task results / steps?
    }

    sealed interface TaskStepResult<T> {
        data class Complete<T>(val value: T) : TaskStepResult<T>
        data class Read<V, E, T>(val cell: CellHandle<V, E>, val continuation: (V) -> Task<T>) : TaskStepResult<T>
        data class Emit<V, E, T>(val cell: CellHandle<V, E>, val effect: E, val continuation: Task<T>) : TaskStepResult<T>
        data class Report<T>(val value: JsonValue, val continuation: Task<T>) : TaskStepResult<T>
        data class Delay<T>(val time: Duration, val continuation: Task<T>) : TaskStepResult<T>
        data class Await<T>(val condition: Condition, val continuation: Task<T>) : TaskStepResult<T>
        data class Spawn<S, T>(val child: Task<S>, val continuation: Task<T>) : TaskStepResult<T>

        companion object Factory {
            fun <T> of(id: TaskId, step: () -> PureStepResult<T>, saveData: () -> List<JsonValue>): () -> TaskStepResult<T> {
                fun <V, E, T> ofRead(step: PureStepResult.Read<V, E, T>) = Read(step.cell) {
                    Task.of(
                        id.nextStep(),
                        // Important: step.continuation is deferred to Task.runStep.
                        // This means the outer Read.continuation can safely be called immediately after reading,
                        // without invoking client code prematurely.
                        { step.continuation(it) },
                        { saveData() + conditionReadEntry(step.cell.serializer.serialize(it)) }
                    )
                }

                fun <V, E, T> ofEmit(step: PureStepResult.Emit<V, E, T>) = Emit(
                    step.cell,
                    step.effect,
                    Task.of(id.nextStep(), step.continuation, { saveData() + EMIT_MARKER })
                )

                fun <S, T> ofSpawn(step: PureStepResult.Spawn<S, T>) = Spawn(
                    Task.of(id.child(step.childName), step.child, { emptyList() }),
                    Task.of(id.nextStep(), step.continuation, { saveData() + SPAWN_MARKER })
                )

                return {
                    with (step()) {
                        when (this) {
                            is PureStepResult.Complete -> Complete(value)
                            is PureStepResult.Read<*, *, T> -> ofRead(this)
                            is PureStepResult.Emit<*, *, T> -> ofEmit(this)
                            is PureStepResult.Report -> Report(
                                value,
                                Task.of(id.nextStep(), continuation, { saveData() + REPORT_MARKER })
                            )
                            is PureStepResult.Delay -> Delay(
                                time,
                                Task.of(id.nextStep(), continuation, { saveData() + DELAY_MARKER })
                            )
                            is PureStepResult.Await -> Await(
                                condition,
                                Task.of(id.nextStep(), continuation, { saveData() + AWAIT_MARKER })
                            )
                            is PureStepResult.Spawn<*, T> -> ofSpawn(this)
                        }
                    }
                }
            }

        }
    }

    companion object {
        private val EMIT_MARKER = conditionEntry("emit")
        private val REPORT_MARKER = conditionEntry("report")
        private val DELAY_MARKER = conditionEntry("delay")
        private val AWAIT_MARKER = conditionEntry("await")
        private val SPAWN_MARKER = conditionEntry("spawn")

        fun <T> of(name: String, step: () -> PureStepResult<T>) = of(TaskId(RootTaskId(name, null), 0), step, { emptyList() })

        private fun <T> of(id: TaskId, step: () -> PureStepResult<T>, saveData: () -> List<JsonValue>) = object : Task<T> {
            override val id: TaskId
                get() = id

            override fun runStep(): TaskStepResult<T> {
                return TaskStepResult.of(id, step, saveData)()
            }

            override fun save(finconCollector: FinconCollector) =
                finconCollector.report(id.rootId.conditionKeys(), JsonArray(saveData()))

            override fun restore(inconProvider: InconProvider): Sequence<Task<*>>? {
                // If there's no incon data for this task, signal that by returning null
                val restoreData = inconProvider.get(id.rootId.conditionKeys()) ?: return null
                // If there's no incon data for this step, we've merely reached the current step, start here
                val restoreDatum = (restoreData as JsonArray).values.getOrNull(id.stepNumber) ?: return sequenceOf(this)
                val restoreDatumMap = (restoreDatum as JsonMap).values
                val restoreDatumType = (restoreDatumMap["type"] as JsonString).value

                val restoreDatumValue = restoreDatumMap["value"]
                fun requireType(requiredType: String) =
                    require(restoreDatumType == requiredType) { "Expected restore datum of type \"$requiredType\"" }

                // TODO: Think through, or at least test thoroughly, this restore procedure.
                //   I have a nagging feeling there's an off-by-one error lurking in here...
                with (this.runStep()) {
                    return when (this) {
                        is TaskStepResult.Complete -> emptySequence()
                        is TaskStepResult.Read<*, *, T> -> {
                            requireType("read")
                            return restoreRead(this, requireNotNull(restoreDatumValue), inconProvider)
                        }
                        is TaskStepResult.Emit<*, *, T> -> {
                            requireType("emit")
                            return continuation.restore(inconProvider)
                        }
                        is TaskStepResult.Report -> {
                            requireType("report")
                            return continuation.restore(inconProvider)
                        }
                        is TaskStepResult.Delay -> {
                            requireType("delay")
                            return continuation.restore(inconProvider)
                        }
                        is TaskStepResult.Await -> {
                            requireType("await")
                            return continuation.restore(inconProvider)
                        }
                        is TaskStepResult.Spawn<*, T> -> {
                            requireType("spawn")
                            return requireNotNull(child.restore(inconProvider)) + requireNotNull(continuation.restore(inconProvider))
                        }
                    }
                }
            }

            private fun <V, E> restoreRead(
                step: TaskStepResult.Read<V, E, T>,
                restoreDatum: JsonValue,
                inconProvider: InconProvider
            ) = step.continuation(step.cell.serializer.deserialize(restoreDatum).getOrThrow()).restore(inconProvider)
        }

        private fun conditionEntry(type: String) = JsonMap(mapOf("type" to JsonString(type)))
        private fun conditionReadEntry(value: JsonValue) = JsonMap(mapOf("type" to JsonString("read"), "value" to value))
    }
}
