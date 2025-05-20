package org.example.gov.nasa.jpl.pyre.core

import org.example.gov.nasa.jpl.pyre.core.Task.TaskStepResult.*
import org.example.gov.nasa.jpl.pyre.core.CellSet.CellHandle
import java.util.Comparator.comparing
import java.util.PriorityQueue

class SimulationState(private val reportHandler: (JsonValue) -> Unit) {
    // CellHandle is class, not data class, because we *want* to use object-identity equality
    private data class TaskEntry(val time: Duration, val task: Task<*>)

    private var time: Duration = Duration(0)
    private var cells: CellSet = CellSet()
    private val tasks: PriorityQueue<TaskEntry> = PriorityQueue(comparing(TaskEntry::time))
    private val cellListeners: MutableMap<CellHandle<*, *>, MutableSet<Await<*>>> = mutableMapOf()
    private val listeningTasks: MutableMap<Await<*>, Set<CellHandle<*, *>>> = mutableMapOf()
    private val conditionalTasks: MutableMap<Await<*>, TaskEntry> = mutableMapOf()

    /**
     * These are the actions allowed during "initialization", before the simulation starts running.
     * Note that this is the only time we're allowed to allocate cells.
     */
    interface SimulationInitializer {
        fun <T: Any, E> allocate(cell: Cell<T, E>): CellHandle<T, E>
        fun spawn(task: Task<*>)
    }

    fun initializer() = object : SimulationInitializer {
        override fun <T: Any, E> allocate(cell: Cell<T, E>) = cells.allocate(cell)
        override fun spawn(task: Task<*>) = addTask(task)
    }

    fun time() = time

    fun addTask(task: Task<*>, time: Duration = time()) {
        tasks.add(TaskEntry(time, task))
    }

    fun stepTo(endTime: Duration) {
        if (tasks.peek()?.time == time) {
            val cellSetBranches: MutableList<CellSet> = mutableListOf()
            while (tasks.peek()?.time == time) {
                cellSetBranches += cells.split().also { runTask(tasks.remove().task, it) }
            }
            cells = CellSet.join(cellSetBranches)
        } else {
            val stepTime = tasks.peek()?.time ?: endTime
            cells.stepBy(stepTime - time)
            time = stepTime
        }
    }

    private fun runTask(task: Task<*>, cellSet: CellSet) {
        fun <V, E, T> runTaskRead(stepResult: Read<V, E, T>) =
            runTask(stepResult.continuation(cellSet[stepResult.cell].value), cellSet)

        fun <T> runTaskAwait(stepResult: Await<T>) {
            // TODO: This does a lot of work and re-work, and is likely not as performant as it could be.

            fun reset() {
                // Reset the cells we're listening to
                listeningTasks.remove(stepResult)?.forEach { cellListeners[it]?.remove(stepResult) }
                // TODO: for long-term performance, we may want to use a proper multi-map instead of map-to-sets
                //   That way we won't accrue empty sets in the values over time
                // Remove the task evaluation if it's there
                conditionalTasks.remove(stepResult).let { tasks.remove(it) }
            }

            fun <T> Task<T>.withReset(): Task<T> {
                val original = this
                return object : Task<T> by this {
                    override fun runStep(): Task.TaskStepResult<T> {
                        reset()
                        return original.runStep()
                    }
                }
            }

            reset()

            // Evaluate the condition
            val (cellsRead, readyTime) = evaluateCondition(stepResult.condition, cellSet)

            // Schedule listeners to re-evaluate condition
            for (cell in cellsRead) {
                cellListeners.getOrPut(cell) { mutableSetOf() } += stepResult
            }
            listeningTasks[stepResult] = cellsRead

            if (readyTime != null) {
                // Add conditional task
                val continuationEntry = TaskEntry(time + readyTime, stepResult.continuation.withReset())
                tasks.add(continuationEntry)
                conditionalTasks[stepResult] = continuationEntry
            }
        }

        fun <V, E, T> runTaskEmit(step: Emit<V, E, T>) {
            cellSet.emit(step.cell, step.effect)
            runTask(step.continuation, cellSet)
            cellListeners[step.cell]?.forEach { runTaskAwait(it) }
        }

        when (val stepResult = task.runStep()) {
            is Complete -> Unit
            is Delay -> {
                tasks.add(TaskEntry(time + stepResult.time, stepResult.continuation))
            }
            is Await -> runTaskAwait(stepResult)
            is Emit<*, *, *> -> runTaskEmit(stepResult)
            is Read<*, *, *> -> runTaskRead(stepResult)
            is Report -> {
                reportHandler(stepResult.value)
                runTask(stepResult.continuation, cellSet)
            }
            is Spawn<*, *> -> {
                tasks.add(TaskEntry(time, stepResult.child))
                runTask(stepResult.continuation, cellSet)
            }
        }
    }

    private fun evaluateCondition(condition: Condition, cellSet: CellSet): Pair<Set<CellHandle<*, *>>, Duration?> {
        fun <V> evaluateRead(read: Condition.Read<V>) =
            with (evaluateCondition(read.continuation(cellSet[read.cell].value), cellSet)) { copy(first = first + read.cell) }

        return when (condition) {
            is Condition.Complete -> Pair(emptySet(), condition.time)
            is Condition.Read<*> -> evaluateRead(condition)
        }
    }

    // REVIEW: Not completely sure this save/restore procedure will work.
    // This needs to be tested fairly extensively...

    fun save(finconCollector: FinconCollector) {
        with (finconCollector.withPrefix("simulation")) {
            report("time", value=Duration.serializer().serialize(time))
        }
        cells.save(finconCollector.withPrefix("cells"))
        val taskCollector = finconCollector.withPrefix("tasks")
        val taskStateCollector = taskCollector.withSuffix("state")
        val taskTimeCollector = taskCollector.withSuffix("time")
        tasks.forEach {
            it.task.save(taskStateCollector)
            taskTimeCollector.report(it.task.id.rootId.conditionKeys(), Duration.serializer().serialize(it.time))
        }
    }

    fun restore(inconProvider: InconProvider) {
        with (inconProvider.withPrefix("simulation")) {
            time = Duration.serializer().deserialize(requireNotNull(get("time"))).getOrThrow()
        }
        cells.restore(inconProvider.withPrefix("cells"))
        val taskProvider = inconProvider.withPrefix("tasks")
        val taskStateProvider = taskProvider.withSuffix("state")
        val taskTimeProvider = taskProvider.withSuffix("time")
        val rootTasks = tasks.asSequence()
        tasks.clear()
        for (rootTask in rootTasks) {
            val restoredTasks = rootTask.task.restore(taskStateProvider)
            // If there was no incon data for this task, default back to the root task.
            if (restoredTasks == null) tasks.add(rootTask)
            // Otherwise, restore all the children of that task
            else for (restoredTask in restoredTasks) {
                val restoredTime = Duration.serializer()
                    .deserialize(requireNotNull(taskTimeProvider.get(restoredTask.id.rootId.conditionKeys())))
                    .getOrThrow()
                tasks.add(TaskEntry(restoredTime, restoredTask))
            }
        }
    }
}
