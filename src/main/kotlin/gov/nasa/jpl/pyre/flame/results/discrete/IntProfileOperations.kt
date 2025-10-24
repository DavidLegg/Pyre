package gov.nasa.jpl.pyre.flame.results.discrete

import gov.nasa.jpl.pyre.kernel.BasicInitScope.Companion.spawn
import gov.nasa.jpl.pyre.flame.plans.ActivityActions.ActivityEvent
import gov.nasa.jpl.pyre.flame.results.ProfileOperations.asResource
import gov.nasa.jpl.pyre.flame.results.ProfileOperations.compute
import gov.nasa.jpl.pyre.flame.results.SimulationResults
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.IntResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.IntResourceOperations.div
import gov.nasa.jpl.pyre.foundation.resources.discrete.IntResourceOperations.minus
import gov.nasa.jpl.pyre.foundation.resources.discrete.IntResourceOperations.plus
import gov.nasa.jpl.pyre.foundation.resources.discrete.IntResourceOperations.times
import gov.nasa.jpl.pyre.foundation.resources.discrete.IntResourceOperations.unaryMinus
import gov.nasa.jpl.pyre.foundation.resources.discrete.IntResourceOperations.unaryPlus
import gov.nasa.jpl.pyre.foundation.resources.discrete.IntResourceOperations.decrement
import gov.nasa.jpl.pyre.foundation.resources.discrete.IntResourceOperations.increment
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.ResourceScope.Companion.now
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope.Companion.delayUntil
import gov.nasa.jpl.pyre.foundation.tasks.task

typealias IntProfile = DiscreteProfile<Int>

object IntProfileOperations {
    /**
     * Compute a profile counting how many activities were running.
     *
     * Designed to be called within a [compute] block on the implicit [SimulationResults] receiver.
     *
     * @param predicate Count only activities satisfying this predicate; defaults to counting all activities.
     */
    context (scope: InitScope)
    suspend fun SimulationResults.countActivities(predicate: (ActivityEvent) -> Boolean = { true }): IntResource {
        // Run a simulation where we keep track of how many matching activities are running
        val counter = discreteResource("running activities satisfying $predicate", 0)
        activities.values
            // Restrict to activities that haven't already ended and satisfy the predicate
            .filter { (it.end?.let { it >= now() } ?: true) && predicate(it) }
            .forEach {
            // If the activity satisfies predicate, spawn a task for it
            spawn(it.name, task {
                // Increment when the activity starts
                delayUntil(it.start)
                counter.increment()
                if (it.end != null) {
                    // Decrement when it ends, if it ends
                    delayUntil(it.end)
                    counter.decrement()
                }
            })
        }
        return counter
    }

    operator fun IntProfile.unaryPlus(): IntProfile = compute { +this }
    operator fun IntProfile.unaryMinus(): IntProfile = compute { -this }
    operator fun IntProfile.plus(other: IntProfile): IntProfile =
        compute { this + other.asResource() }
    operator fun IntProfile.minus(other: IntProfile): IntProfile =
        compute { this - other.asResource() }
    operator fun IntProfile.times(other: IntProfile): IntProfile =
        compute { this * other.asResource() }
    operator fun IntProfile.div(other: IntProfile): IntProfile =
        compute { this / other.asResource() }
}