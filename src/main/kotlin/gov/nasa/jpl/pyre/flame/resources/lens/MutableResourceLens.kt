package gov.nasa.jpl.pyre.flame.resources.lens

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.coals.named
import gov.nasa.jpl.pyre.spark.resources.DynamicsMonad
import gov.nasa.jpl.pyre.spark.resources.FullDynamics
import gov.nasa.jpl.pyre.spark.resources.MutableResource
import gov.nasa.jpl.pyre.spark.resources.Resource
import gov.nasa.jpl.pyre.spark.resources.ResourceEffect
import gov.nasa.jpl.pyre.spark.resources.ResourceMonad
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.tasks.ResourceScope
import gov.nasa.jpl.pyre.spark.tasks.TaskScope

object MutableResourceLens {
    fun <K, V> select(key: DiscreteResource<K>, selector: (K) -> MutableResource<V>): MutableResource<V> =
        object : MutableResource<V> {
            context(scope: TaskScope)
            override suspend fun emit(effect: ResourceEffect<V>) = selector(key.getValue()).emit(effect)

            context(scope: ResourceScope)
            override suspend fun getDynamics(): FullDynamics<V> = selector(key.getValue()).getDynamics()
        }

    /**
     * Construct a view of this resource, through the given lens.
     * Effects applied to the resulting view are mapped back to the base resource.
     */
    fun <D, E> MutableResource<D>.view(lens: InvertibleFunction<D, E>): MutableResource<E> =
        object : MutableResource<E>, Resource<E> by ResourceMonad.map(this, lens) {
            context(scope: TaskScope)
            override suspend fun emit(effect: ResourceEffect<E>) {
                this@view.emit({ d: FullDynamics<D> ->
                    DynamicsMonad.map(effect(DynamicsMonad.map(d, lens)), lens.inverse)
                } named { effect.toString() })
            }
        }
}