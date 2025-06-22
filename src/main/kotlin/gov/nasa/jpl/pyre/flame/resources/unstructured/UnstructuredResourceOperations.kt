package gov.nasa.jpl.pyre.flame.resources.unstructured

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.plus
import gov.nasa.jpl.pyre.spark.resources.Dynamics
import gov.nasa.jpl.pyre.spark.resources.DynamicsMonad
import gov.nasa.jpl.pyre.spark.resources.FullDynamics
import gov.nasa.jpl.pyre.spark.resources.Resource
import gov.nasa.jpl.pyre.spark.resources.ResourceMonad
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.tasks.CellsReadableScope
import gov.nasa.jpl.pyre.spark.tasks.SparkContext

object UnstructuredResourceOperations {
    /**
     * Return an [UnstructuredResource], given a function of absolute time.
     */
    context(SparkContext)
    fun <A> timeBased(fn: (Duration) -> A) = object : UnstructuredResource<A> {
        context(CellsReadableScope@CellsReadableScope)
        override suspend fun getDynamics(): FullDynamics<Unstructured<A>> {
            val now = simulationClock.getValue()
            return DynamicsMonad.pure(Unstructured.of { fn(now + it) })
        }
    }

    /**
     * Ignore the structure of any [Dynamics]
     */
    fun <V, D : Dynamics<V, D>> D.asUnstructured(): Unstructured<V> = object: Unstructured<V> {
        override fun value(): V = this@D.value()
        override fun step(t: Duration): Unstructured<V> = this@D.step(t).asUnstructured()
    }

    /**
     * Ignore the structure of any [Resource]
     */
    fun <V, D : Dynamics<V, D>> Resource<D>.asUnstructured(): UnstructuredResource<V> = ResourceMonad.map(this) { it.asUnstructured() }
}