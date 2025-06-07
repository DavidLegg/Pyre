package gov.nasa.jpl.pyre.spark.tasks

import gov.nasa.jpl.pyre.ember.Condition
import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.Duration.Companion.ZERO
import gov.nasa.jpl.pyre.ember.JsonValue.*
import gov.nasa.jpl.pyre.ember.PureTaskStep
import gov.nasa.jpl.pyre.ember.Serializer
import gov.nasa.jpl.pyre.ember.SimulationState.SimulationInitContext
import gov.nasa.jpl.pyre.ember.minus
import gov.nasa.jpl.pyre.spark.tasks.SparkContext
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResource
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResourceOperations.not
import gov.nasa.jpl.pyre.spark.resources.*

// Conditions are isomorphic to boolean discrete resources.
// Realize this isomorphism through the whenTrue function, and apply it implicitly by overloading await.

fun whenTrue(resource: BooleanResource): () -> Condition = condition {
    if (resource.getValue()) Duration.ZERO else null
}

suspend fun TaskScope<*>.await(condition: BooleanResource) = await(whenTrue(condition))

/**
 * Run block whenever the condition resource is true.
 * Note that this waits for block to finish before restarting, but doesn't wait for the condition resource to be false.
 * If block finishes without changing the condition resource, then block will simply run again.
 */
fun whenever(condition: BooleanResource, block: suspend TaskScope<Unit>.() -> Unit): PureTaskStep<Unit> = repeatingTask {
    await(condition)
    block()
}

/**
 * Run a sub-task once whenever the condition resource transitions from false to true.
 * Note that this waits for block to finish before restarting.
 * Spawn a sub-task from block if multiple reactions may need to run simultaneously.
 */
fun onceWhenever(condition: BooleanResource, block: suspend TaskScope<Unit>.() -> Unit): PureTaskStep<Unit> = repeatingTask {
    await(condition)
    block()
    await(!condition)
}

/**
 * Returns a condition that will be satisfied exactly when the dynamics of this resource change
 * inconsistent with the normal continuous evolution of the dynamics.
 */
context (SparkContext)
suspend fun <V, D : Dynamics<V, D>> TaskScope<*>.dynamicsChange(resource: Resource<D>): () -> Condition {
    val dynamics1 = resource.getDynamics()
    val time1 = SIMULATION_CLOCK.getValue()
    return condition {
        val dynamics2 = resource.getDynamics()
        val time2 = SIMULATION_CLOCK.getValue()
        if (dynamics1.data.step(time2 - time1) != dynamics2.data) ZERO else dynamics2.expiry.time
    }
}

context (SparkContext)
fun <V, D : Dynamics<V, D>> wheneverChanges(resource: Resource<D>, block: suspend TaskScope<Unit>.() -> Unit): PureTaskStep<Unit> = repeatingTask {
    await(dynamicsChange(resource))
    block()
}
