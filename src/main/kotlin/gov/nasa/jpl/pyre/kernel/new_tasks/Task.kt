package gov.nasa.jpl.pyre.kernel.new_tasks

import gov.nasa.jpl.pyre.kernel.MutableSnapshot
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.Snapshot

/**
 * Any task which may run in a simulation.
 */
interface Task {
    /**
     * A stable and human-readable identifier for this task.
     */
    val name: Name

    /**
     * A handle pointing back to the root task from which this task is first descended.
     *
     * This is either the daemon originally spawned by the model, or the activity first given to the simulator.
     */
    val rootTask: Task

    /**
     * Run the next step of this task.
     */
    fun runStep(actions: BasicTaskActions): TaskStepResult

    /**
     * Save the state of this task to the provided [MutableSnapshot].
     */
    fun saveTo(snapshot: MutableSnapshot)

    /**
     * Treat this as a root task created by the model, and restore all its children in [snapshot].
     *
     * This method should be called for every task created directly by the model to fully restore the simulation.
     */
    fun restoreFrom(snapshot: Snapshot): List<Task>
}