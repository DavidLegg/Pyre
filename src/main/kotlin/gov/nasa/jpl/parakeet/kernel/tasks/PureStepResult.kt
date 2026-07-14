package gov.nasa.jpl.parakeet.kernel.tasks

import gov.nasa.jpl.parakeet.kernel.Condition
import gov.nasa.jpl.parakeet.kernel.Name

fun interface PureTaskStep {
    fun run(actions: BasicTaskActions): PureStepResult
}

/**
 * The ways a task step can "yield" back to the simulation engine, aka "yielding actions".
 *
 * These correspond with the task pausing (or stopping completely!),
 * so the simulation engine can schedule other tasks before the next step of this task.
 */
sealed interface PureStepResult {
    object Complete : PureStepResult {
        override fun toString() = "Complete"
    }
    data class Await(val condition: Condition, val continuation: PureTaskStep) : PureStepResult {
        override fun toString() = "Await($condition)"
    }
    data class Spawn(val childName: Name, val child: PureTaskStep, val continuation: PureTaskStep) : PureStepResult {
        override fun toString() = "Spawn($childName)"
    }
    object Restart : PureStepResult {
        override fun toString() = "Restart"
    }
}
