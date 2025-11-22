package gov.nasa.jpl.pyre.foundation.tasks

import gov.nasa.jpl.pyre.utilities.named
import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.Duration.Companion.ZERO
import gov.nasa.jpl.pyre.kernel.minus
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResourceOperations.not
import gov.nasa.jpl.pyre.foundation.resources.*
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope.Companion.simulationClock
import gov.nasa.jpl.pyre.foundation.tasks.TaskOperations.delay
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope.Companion.await
import gov.nasa.jpl.pyre.kernel.Condition
import gov.nasa.jpl.pyre.kernel.ConditionResult
import gov.nasa.jpl.pyre.kernel.ReadActions
import gov.nasa.jpl.pyre.kernel.SatisfiedAt
import gov.nasa.jpl.pyre.kernel.UnsatisfiedUntil

// Conditions are isomorphic to boolean discrete resources.
// Realize this isomorphism through the whenTrue function, and apply it implicitly by overloading await.
// In practice, most modelers will use conditions this way, by building up a boolean resource derivation.
// Doing so reduces maintenance burden by letting maintainers focus only on resource derivation.

object Reactions {
    context (scope: SimulationScope)
    fun whenTrue(resource: BooleanResource): Condition = condition {
        with (resource.getDynamics()) {
            if (data.value) SatisfiedAt(ZERO) else UnsatisfiedUntil(expiry.time)
        }
    } named resource::toString

    context (scope: TaskScope)
    suspend fun await(condition: BooleanResource) = await(whenTrue(condition))

    /**
     * Run block whenever the condition resource is true.
     * Note that this waits for block to finish before restarting, but doesn't wait for the condition resource to be false.
     * If block finishes without changing the condition resource, then block will simply run again.
     */
    context (scope: SimulationScope)
    fun whenever(condition: BooleanResource, block: suspend context (TaskScope) () -> Unit) = repeatingTask {
        await(condition)
        block()
    }

    /**
     * Run a sub-task once whenever the condition resource transitions from false to true.
     * Note that this waits for block to finish before restarting.
     * Spawn a sub-task from block if multiple reactions may need to run simultaneously.
     */
    context (scope: SimulationScope)
    fun onceWhenever(condition: BooleanResource, block: suspend context (TaskScope) () -> Unit) = repeatingTask {
        await(condition)
        block()
        await(!condition)
    }

    /**
     * Returns a condition that will be satisfied exactly when the dynamics of this resource change
     * inconsistent with the normal continuous evolution of the dynamics.
     */
    context (scope: TaskScope)
    fun <V, D : Dynamics<V, D>> dynamicsChange(resource: Resource<D>): Condition {
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
    infix fun (Condition).or(other: Condition): Condition = { actions: ReadActions ->
        val r1 = this(actions)
        val r2 = other(actions)
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
    infix fun (Condition).and(other: Condition): Condition = { actions: ReadActions ->
        // If both are satisfied at the same time, the conjunction is satisfied at that time.
        // Otherwise, the conjunction is unsatisfied until the later operand time.
        // This is because that operand is unsatisfied at least until that time, so there's no need to evaluate again earlier than that.
        // At that time, even if that operand is satisfied, the other operand needs to be re-evaluated anyways.
        val r1 = this(actions)
        val r2 = other(actions)

        if (r1 is SatisfiedAt && r2 is SatisfiedAt && r1.time == r2.time) r1
        else UnsatisfiedUntil(maxOf(r1.expiry(), r2.expiry()).time)
    } named { "($this) or ($other)" }

    private fun ConditionResult.expiry() = when(this) {
        is SatisfiedAt -> Expiry(time)
        is UnsatisfiedUntil -> Expiry(time)
    }

    context (scope: SimulationScope)
    fun <V, D : Dynamics<V, D>> wheneverChanges(resource: Resource<D>, block: suspend context (TaskScope) () -> Unit) = repeatingTask {
        await(dynamicsChange(resource))
        block()
    }

    context (scope: SimulationScope)
    fun every(interval: Duration, block: suspend context (TaskScope) () -> Unit) = repeatingTask {
        delay(interval)
        block()
    }
}
