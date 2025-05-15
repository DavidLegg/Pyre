package org.example.gov.nasa.jpl.pyre.state

import org.example.gov.nasa.jpl.pyre.core.Duration
import org.example.gov.nasa.jpl.pyre.core.Task
import org.example.gov.nasa.jpl.pyre.io.Serializer
import java.util.PriorityQueue

@Suppress("UNCHECKED_CAST")
class SimulationState {
    // CellHandle is class, not data class, because we *want* to use object-identity equality
    // TODO: Find out how to declare constructor private
    class CellHandle<T>(val name: String, val serializer: Serializer<T>)
    data class Cell<T>(val name: String, val value: T, val serializer: Serializer<T>, val stepper: (T, Duration) -> T)
    data class TaskEntry(val time: Duration, val task: Task<*>)

    private var time: Duration = Duration(0)
    private val cells: MutableMap<CellHandle<*>, Cell<*>> = mutableMapOf()
    private val tasks: PriorityQueue<TaskEntry> = PriorityQueue()
    private val monitors: MutableMap<CellHandle<*>, Set<Task<*>>> = mutableMapOf()

    fun getTime() = time

    fun <T: Any> allocate(cell: Cell<T>): CellHandle<T> {
        return CellHandle(cell.name, cell.serializer).also { cells[it] = cell }
    }

    fun <T> getCell(cellHandle: CellHandle<T>): Cell<T> {
        return requireNotNull(cells[cellHandle], { "Invalid cell handle" }) as Cell<T>
    }

    fun addTask(task: TaskEntry) {
        tasks.add(task)
    }

    fun step() {

    }
}