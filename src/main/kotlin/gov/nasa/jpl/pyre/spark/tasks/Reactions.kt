package gov.nasa.jpl.pyre.spark.tasks

import gov.nasa.jpl.pyre.ember.Condition
import gov.nasa.jpl.pyre.ember.Condition.*
import gov.nasa.jpl.pyre.ember.Duration.Companion.ZERO
import gov.nasa.jpl.pyre.ember.PureTaskStep
import gov.nasa.jpl.pyre.ember.minus
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResource
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResourceOperations.not
import gov.nasa.jpl.pyre.spark.resources.*

// Conditions are isomorphic to boolean discrete resources.
// Realize this isomorphism through the whenTrue function, and apply it implicitly by overloading await.
// In practice, most modelers will use conditions this way, by building up a boolean resource derivation.
// Doing so reduces maintenance burden by letting maintainers focus only on resource derivation.

fun whenTrue(resource: BooleanResource): () -> Condition = condition {
    with (resource.getDynamics()) {
        if (data.value) SatisfiedAt(ZERO) else UnsatisfiedUntil(expiry.time)
    }
}

suspend fun TaskScope<*>.await(condition: BooleanResource) = await(whenTrue(condition))

/**
 * Run block whenever the condition resource is true.
 * Note that this waits for block to finish before restarting, but doesn't wait for the condition resource to be false.
 * If block finishes without changing the condition resource, then block will simply run again.
 */
fun SparkContext.whenever(condition: BooleanResource, block: suspend SparkTaskScope<Unit>.() -> Unit): PureTaskStep<Unit> = repeatingTask {
    with (sparkTaskScope()) {
        await(condition)
        block()
    }
}

/**
 * Run a sub-task once whenever the condition resource transitions from false to true.
 * Note that this waits for block to finish before restarting.
 * Spawn a sub-task from block if multiple reactions may need to run simultaneously.
 */
fun SparkContext.onceWhenever(condition: BooleanResource, block: suspend SparkTaskScope<Unit>.() -> Unit): PureTaskStep<Unit> = repeatingTask {
    with (sparkTaskScope()) {
        await(condition)
        block()
        await(!condition)
    }
}

/**
 * Returns a condition that will be satisfied exactly when the dynamics of this resource change
 * inconsistent with the normal continuous evolution of the dynamics.
 */
suspend fun <V, D : Dynamics<V, D>> SparkTaskScope<*>.dynamicsChange(resource: Resource<D>): () -> Condition {
    val dynamics1 = resource.getDynamics()
    val time1 = simulationClock.getValue()
    return condition {
        val dynamics2 = resource.getDynamics()
        val time2 = simulationClock.getValue()
        if (dynamics1.data.step(time2 - time1) != dynamics2.data) SatisfiedAt(ZERO)
        else dynamics2.expiry.time?.let(::SatisfiedAt) ?: UnsatisfiedUntil(null)
    }
}

fun <V, D : Dynamics<V, D>> SparkContext.wheneverChanges(resource: Resource<D>, block: suspend SparkTaskScope<Unit>.() -> Unit): PureTaskStep<Unit> = repeatingTask {
    with (sparkTaskScope()) {
        await(dynamicsChange(resource))
        block()
    }
}
