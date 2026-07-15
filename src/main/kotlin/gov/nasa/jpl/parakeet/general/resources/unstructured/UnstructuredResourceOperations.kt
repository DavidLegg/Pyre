package gov.nasa.jpl.parakeet.general.resources.unstructured

import gov.nasa.jpl.parakeet.foundation.resources.Dynamics
import gov.nasa.jpl.parakeet.foundation.resources.DynamicsMonad
import gov.nasa.jpl.parakeet.foundation.resources.Resource
import gov.nasa.jpl.parakeet.foundation.resources.ResourceMonad
import gov.nasa.jpl.parakeet.foundation.resources.fullyNamed
import gov.nasa.jpl.parakeet.foundation.resources.getValue
import gov.nasa.jpl.parakeet.foundation.tasks.SimulationScope.Companion.simulationClock
import gov.nasa.jpl.parakeet.kernel.Name
import kotlin.time.Duration
import kotlin.time.Instant

object UnstructuredResourceOperations {
    /**
     * Return an [UnstructuredResource], given a function of absolute time as measured by [simulationClock].
     */
    fun <A> timeBased(fn: (Instant) -> A) = UnstructuredResource {
        val now = simulationClock.getValue()
        DynamicsMonad.pure(Unstructured.of { fn(now + it) })
    }.fullyNamed { Name(fn.toString()) }

    /**
     * Ignore the structure of any [Dynamics]
     */
    fun <V, D : Dynamics<V, D>> D.asUnstructured(): Unstructured<V> = object: Unstructured<V> {
        override fun value(): V = this@asUnstructured.value()
        override fun step(t: Duration): Unstructured<V> = this@asUnstructured.step(t).asUnstructured()
    }

    /**
     * Ignore the structure of any [Resource]
     */
    fun <V, D : Dynamics<V, D>> Resource<D>.asUnstructured(): UnstructuredResource<V> =
        ResourceMonad.map(this) { it.asUnstructured() }.fullyNamed { name }
}