package gov.nasa.jpl.pyre.general.resources.unstructured

import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.plus
import gov.nasa.jpl.pyre.foundation.resources.Dynamics
import gov.nasa.jpl.pyre.foundation.resources.DynamicsMonad
import gov.nasa.jpl.pyre.foundation.resources.Resource
import gov.nasa.jpl.pyre.foundation.resources.ResourceMonad
import gov.nasa.jpl.pyre.foundation.resources.fullyNamed
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope
import gov.nasa.jpl.pyre.kernel.Name

object UnstructuredResourceOperations {
    /**
     * Return an [UnstructuredResource], given a function of absolute time as measured by [SimulationScope.simulationClock].
     */
    context(context: SimulationScope)
    fun <A> timeBased(fn: (Duration) -> A) = UnstructuredResource {
        val now = context.simulationClock.getValue()
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