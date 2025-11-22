package gov.nasa.jpl.pyre.general.interrupts

import gov.nasa.jpl.pyre.kernel.CellSet
import gov.nasa.jpl.pyre.kernel.Effect
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResourceOperations.or
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad.pure
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.tasks.Reactions.or
import gov.nasa.jpl.pyre.foundation.tasks.Reactions.whenTrue
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.kernel.Condition

/**
 * Provides utilities for interrupting a task.
 *
 * For example, one may want to run a nominal task behavior, but abort that behavior if some fault condition occurs.
 */
object Interrupts {
    /**
     * Run a task with the option to abort.
     * If at any point any [abortConditions] resource is true,
     * execution of [nominalBehavior] stops and the corresponding handler is executed instead.
     */
    context (scope: TaskScope)
    suspend fun abortIf(
        vararg abortConditions: Pair<BooleanResource, suspend context (TaskScope) () -> Unit>,
        nominalBehavior: suspend context (TaskScope) () -> Unit,
    ) {
        // If any abort condition is true, we've aborted.
        val hasAborted: BooleanResource = abortConditions.map { it.first }.reduceOrNull { a, b -> a or b } ?: pure(false)

        // Construct a new TaskScope, which will throw AbortTaskException if we abort.
        // That will stop the rest of nominal behavior from executing.
        val abortScope = object : TaskScope by scope {
            override fun <V> emit(cell: CellSet.CellHandle<V>, effect: Effect<V>) {
                scope.emit(cell, effect)
                // It's possible that the effect we just emitted caused our own abort condition to fire!
                if (hasAborted.getValue()) throw AbortTaskException()
            }

            override suspend fun await(condition: Condition) {
                // Await either the desired condition or an abort, whichever is earlier.
                scope.await(condition or whenTrue(hasAborted))
                // Check why we stopped, and handle a possible abort.
                if (hasAborted.getValue()) throw AbortTaskException()
            }
        }

        try {
            // Run the nominal behavior using the abort-aware task scope
            nominalBehavior(abortScope)
        } catch (_ : AbortTaskException) {
            // If the nominal behavior is aborted, find which abort condition occurred and run its handler
            abortConditions.first { it.first.getValue() }.second(scope)
        }
    }

    private class AbortTaskException : RuntimeException()
}