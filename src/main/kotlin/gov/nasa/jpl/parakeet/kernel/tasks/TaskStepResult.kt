package gov.nasa.jpl.parakeet.kernel.tasks

import gov.nasa.jpl.pyre.kernel.Condition

/**
 * The result of running one step of a [Task]
 */
sealed interface TaskStepResult {
    object Complete : TaskStepResult {
        override fun toString() = "Complete"
    }
    data class Await(val condition: Condition, val rewait: Task, val continuation: Task) : TaskStepResult {
        override fun toString() = "Await($condition)"
    }
    data class Spawn(val child: Task, val continuation: Task) : TaskStepResult {
        override fun toString() = "Spawn($child)"
    }
    data class Restart(val continuation: Task) : TaskStepResult {
        override fun toString() = "Restart"
    }
}