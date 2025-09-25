package gov.nasa.jpl.pyre.flame.interrupts

import gov.nasa.jpl.pyre.ember.CellSet
import gov.nasa.jpl.pyre.ember.Condition
import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.plus
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResource
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResourceOperations.or
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.pure
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.resources.timer.TimerResourceOperations.greaterThanOrEquals
import gov.nasa.jpl.pyre.spark.tasks.Reactions.await
import gov.nasa.jpl.pyre.spark.tasks.Reactions.or
import gov.nasa.jpl.pyre.spark.tasks.Reactions.whenTrue
import gov.nasa.jpl.pyre.spark.tasks.TaskScope

/**
 * Provides utilities for interrupting a task.
 *
 * For example, one may want to run a nominal task behavior, but interrupt that behavior and abort if some fault condition occurs.
 */
object Interrupts {
    /**
     * Run an interruptible task.
     * If at any point any [interrupts] resource is true,
     * execution of [nominalBehavior] stops and the corresponding interrupt handler is executed instead.
     */
    context (scope: TaskScope)
    suspend fun withInterrupts(
        nominalBehavior: suspend context (TaskScope) () -> Unit,
        vararg interrupts: Pair<BooleanResource, suspend context (TaskScope) () -> Unit>,
    ) {
        // If any interrupt is true, we've been interrupted.
        val isInterrupted: BooleanResource = interrupts.map { it.first }.reduceOrNull { a, b -> a or b } ?: pure(false)

        // Construct a new TaskScope, which will throw InterruptException if an interruption occurs.
        // That will stop the rest of nominal behavior from executing.
        val interruptScope = object : TaskScope by scope {
            override suspend fun <V, E> emit(cell: CellSet.CellHandle<V, E>, effect: E) {
                scope.emit(cell, effect)
                // It's possible that the effect we just emitted caused our own interrupt condition to fire!
                if (isInterrupted.getValue()) throw InterruptException()
            }

            override suspend fun delay(time: Duration) {
                // Convert simple delay to a condition await, so we can combine it with interrupt condition
                await(simulationClock greaterThanOrEquals (simulationClock.getValue() + time))
            }

            override suspend fun await(condition: () -> Condition) {
                // Await either the desired condition or an interruption, whichever is earlier.
                scope.await(condition or whenTrue(isInterrupted))
                // Check why we stopped, and handle a possible interruption.
                if (isInterrupted.getValue()) throw InterruptException()
            }
        }

        try {
            // Run the nominal behavior using the interrupt-aware task scope
            nominalBehavior(interruptScope)
        } catch (_ : InterruptException) {
            // If the nominal behavior is interrupted, find which interrupt occurred and run its handler
            interrupts.first { it.first.getValue() }.second(scope)
        }
    }

    private class InterruptException : RuntimeException()
}