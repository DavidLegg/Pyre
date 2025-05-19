package org.example.gov.nasa.jpl.pyre.state

import org.example.gov.nasa.jpl.pyre.core.Condition
import org.example.gov.nasa.jpl.pyre.core.Duration
import org.example.gov.nasa.jpl.pyre.core.Task
import org.example.gov.nasa.jpl.pyre.core.Task.TaskStep.*
import org.example.gov.nasa.jpl.pyre.io.JsonValue
import org.example.gov.nasa.jpl.pyre.state.CellSet.CellHandle
import java.util.Comparator.comparing
import java.util.PriorityQueue

class SimulationState {
    // CellHandle is class, not data class, because we *want* to use object-identity equality
    data class TaskEntry(val time: Duration, val task: () -> Task<*>)

    private var time: Duration = Duration(0)
    private val cells: CellSet = CellSet()
    private val tasks: PriorityQueue<TaskEntry> = PriorityQueue(comparing(TaskEntry::time))
    private val cellListeners: MutableMap<CellHandle<*, *>, MutableSet<Await<*>>> = mutableMapOf()
    private val listeningTasks: MutableMap<Await<*>, Set<CellHandle<*, *>>> = mutableMapOf()
    private val conditionalTasks: MutableMap<Await<*>, TaskEntry> = mutableMapOf()
    private val reportHandler: (JsonValue) -> Unit = { println(it.toString()) }

    fun getTime() = time

    fun addTask(task: TaskEntry) {
        tasks.add(task)
    }

    fun step() {
        val cellSetBranches: MutableList<CellSet> = mutableListOf()
        while (tasks.peek()?.time == time) {
            cellSetBranches += cells.split().also { runTask(tasks.remove().task, it) }
        }
    }

    private fun runTask(task: () -> Task<*>, cellSet: CellSet) {
        fun <V, E, T> runTaskRead(step: Read<V, E, T>) =
            runTask({ step.continuation(cellSet[step.cell].value) }, cellSet)

        fun <T> runTaskAwait(step: Await<T>) {
            // TODO: This does a lot of work and re-work, and is likely not as performant as it could be.

            fun reset() {
                // Reset the cells we're listening to
                listeningTasks.remove(step)?.forEach { cellListeners[it]?.remove(step) }
                // TODO: for long-term performance, we may want to use a proper multi-map instead of map-to-sets
                //   That way we won't accrue empty sets in the values over time
                // Remove the task evaluation if it's there
                conditionalTasks.remove(step).let { tasks.remove(it) }
            }

            reset()

            // Evaluate the condition
            val (cellsRead, readyTime) = evaluateCondition(step.condition, cellSet)

            // Schedule listeners to re-evaluate condition
            for (cell in cellsRead) {
                cellListeners.getOrPut(cell) { mutableSetOf() } += step
            }
            listeningTasks[step] = cellsRead

            if (readyTime != null) {
                // Add conditional task
                val continuationEntry = TaskEntry(time + readyTime) {
                    reset()
                    step.continuation()
                }
                tasks.add(continuationEntry)
                conditionalTasks[step] = continuationEntry
            }
        }

        fun <V, E, T> runTaskEmit(step: Emit<V, E, T>) {
            cellSet.emit(step.cell, step.effect)
            runTask(step.continuation, cellSet)
            cellListeners[step.cell]?.forEach { runTaskAwait(it) }
        }

        when (val step = task().step) {
            is Complete -> Unit
            is Delay -> {
                tasks.add(TaskEntry(time + step.time, step.continuation))
            }
            is Await -> runTaskAwait(step)
            is Emit<*, *, *> -> runTaskEmit(step)
            is Read<*, *, *> -> runTaskRead(step)
            is Report -> {
                reportHandler(step.value)
                runTask(step.continuation, cellSet)
            }
            is Spawn<*, *> -> {
                tasks.add(TaskEntry(time, { step.child }))
                runTask(step.continuation, cellSet)
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
}