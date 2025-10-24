package gov.nasa.jpl.pyre.flame.resources.lens

import gov.nasa.jpl.pyre.utilities.InvertibleFunction
import gov.nasa.jpl.pyre.utilities.named
import gov.nasa.jpl.pyre.foundation.resources.DynamicsMonad
import gov.nasa.jpl.pyre.foundation.resources.FullDynamics
import gov.nasa.jpl.pyre.foundation.resources.MutableResource
import gov.nasa.jpl.pyre.foundation.resources.Resource
import gov.nasa.jpl.pyre.foundation.resources.ResourceEffect
import gov.nasa.jpl.pyre.foundation.resources.ResourceMonad
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.tasks.ResourceScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope

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