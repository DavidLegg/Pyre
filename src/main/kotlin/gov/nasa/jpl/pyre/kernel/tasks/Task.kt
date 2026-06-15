package gov.nasa.jpl.pyre.kernel.tasks

import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.KernelTaskCheckpoint

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
    val rootTaskName: Name

    /**
     * Run the next step of this task.
     */
    fun runStep(actions: BasicTaskActions): TaskStepResult

    /**
     * Save a checkpoint of this task.
     */
    fun save(): KernelTaskCheckpoint

    /**
     * Treat this as a root task, and restore the child described by [checkpoint].
     *
     * This method should be called for every child task present in simulation checkpoint to fully restore the simulation.
     */
    fun restoreFrom(checkpoint: KernelTaskCheckpoint): Task
}