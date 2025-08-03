package gov.nasa.jpl.pyre.spark.tasks

import gov.nasa.jpl.pyre.coals.named
import gov.nasa.jpl.pyre.ember.Condition
import gov.nasa.jpl.pyre.ember.Condition.*
import gov.nasa.jpl.pyre.ember.Duration
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
} named resource::toString

suspend fun TaskScope.await(condition: BooleanResource) = await(whenTrue(condition))

/**
 * Run block whenever the condition resource is true.
 * Note that this waits for block to finish before restarting, but doesn't wait for the condition resource to be false.
 * If block finishes without changing the condition resource, then block will simply run again.
 */
fun SparkContext.whenever(condition: BooleanResource, block: suspend SparkTaskScope.() -> Unit): PureTaskStep<Unit> = repeatingTask {
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
fun SparkContext.onceWhenever(condition: BooleanResource, block: suspend SparkTaskScope.() -> Unit): PureTaskStep<Unit> = repeatingTask {
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
suspend fun <V, D : Dynamics<V, D>> SparkTaskScope.dynamicsChange(resource: Resource<D>): () -> Condition {
    val dynamics1 = resource.getDynamics()
    val time1 = simulationClock.getValue()
    return condition {
        val dynamics2 = resource.getDynamics()
        val time2 = simulationClock.getValue()
        if (dynamics1.data.step(time2 - time1) != dynamics2.data) SatisfiedAt(ZERO)
        else dynamics2.expiry.time?.let(::SatisfiedAt) ?: UnsatisfiedUntil(null)
    } named { "When dynamics change for ($resource)" }
}


/**
 * Specialized Condition disjunction operator.
 *
 * When possible, prefer using boolean resource derivation and [whenTrue].
 * This method is primarily for incorporating [dynamicsChange] conditions in specialized cases.
 */
infix fun (() -> Condition).or(other: () -> Condition): () -> Condition =
    conditionMap(this, other) { r1, r2 ->
        // We must take the minimum-time result. If it's a satisfaction, we're satisfied then.
        // If it's an unsatisfied-until, we need to reevaluate then anyways.
        if (r1.expiry() < r2.expiry()) r1 else r2
    } named { "($this) or ($other)" }

/**
 * Specialized Condition conjunction operator.
 *
 * When possible, prefer using boolean resource derivation and [whenTrue].
 * This method is primarily for incorporating [dynamicsChange] conditions in specialized cases.
 */
infix fun (() -> Condition).and(other: () -> Condition): () -> Condition =
    // If both are satisfied at the same time, the conjunction is satisfied at that time.
    // Otherwise, the conjunction is unsatisfied until the later operand time.
    // This is because that operand is unsatisfied at least until that time, so there's no need to evaluate again earlier than that.
    // At that time, even if that operand is satisfied, the other operand needs to be re-evaluated anyways.

    conditionMap(this, other) { r1, r2 ->
        if (r1 is SatisfiedAt && r2 is SatisfiedAt && r1.time == r2.time)
            r1
        else
            UnsatisfiedUntil(maxOf(r1.expiry(), r2.expiry()).time)
    } named { "($this) or ($other)" }

private fun conditionMap(c1: () -> Condition, c2: () -> Condition, f: (ConditionResult, ConditionResult) -> Condition): () -> Condition =
    conditionMap(c1) { r1 -> conditionMap(c2) { r2 -> f(r1, r2) }() }
private fun conditionMap(c1: () -> Condition, f: (ConditionResult) -> Condition): () -> Condition {
    fun <V> mapRead(r: Read<V>) = Read(r.cell) { v -> conditionMap({ r.continuation(v) }, f)() }
    return {
        when (val it = c1()) {
            is ConditionResult -> f(it)
            is Read<*> -> mapRead(it)
        }
    }
}

private fun ConditionResult.expiry() = when(this) {
    is SatisfiedAt -> Expiry(time)
    is UnsatisfiedUntil -> Expiry(time)
}

fun <V, D : Dynamics<V, D>> SparkContext.wheneverChanges(resource: Resource<D>, block: suspend SparkTaskScope.() -> Unit): PureTaskStep<Unit> = repeatingTask {
    with (sparkTaskScope()) {
        await(dynamicsChange(resource))
        block()
    }
}

fun SparkContext.every(interval: Duration, block: suspend SparkTaskScope.() -> Unit): PureTaskStep<Unit> = repeatingTask {
    with (sparkTaskScope()) {
        delay(interval)
        block()
    }
}
