package gov.nasa.jpl.pyre.kernel.tasks

import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.KernelTaskSnapshot

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
     * Save a snapshot of this task.
     */
    fun save(): KernelTaskSnapshot

    /**
     * Treat this as a root task, and restore the child described by [snapshot].
     *
     * This method should be called for every child task present in simulation snapshot to fully restore the simulation.
     */
    fun restoreFrom(snapshot: KernelTaskSnapshot): Task
}