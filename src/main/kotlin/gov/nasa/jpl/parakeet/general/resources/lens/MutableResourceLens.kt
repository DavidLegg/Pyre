package gov.nasa.jpl.parakeet.general.resources.lens

import gov.nasa.jpl.parakeet.utilities.InvertibleFunction
import gov.nasa.jpl.parakeet.utilities.named
import gov.nasa.jpl.parakeet.foundation.resources.DynamicsMonad
import gov.nasa.jpl.parakeet.foundation.resources.FullDynamics
import gov.nasa.jpl.parakeet.foundation.resources.MutableResource
import gov.nasa.jpl.parakeet.foundation.resources.Resource
import gov.nasa.jpl.parakeet.foundation.resources.ResourceEffect
import gov.nasa.jpl.parakeet.foundation.resources.ResourceMonad
import gov.nasa.jpl.parakeet.foundation.resources.discrete.DiscreteResource
import gov.nasa.jpl.parakeet.foundation.resources.getValue
import gov.nasa.jpl.parakeet.foundation.tasks.ResourceScope
import gov.nasa.jpl.parakeet.foundation.tasks.TaskScope

object MutableResourceLens {
    fun <K, V> select(key: DiscreteResource<K>, selector: (K) -> MutableResource<V>): MutableResource<V> =
        object : MutableResource<V> {
            context(scope: TaskScope)
            override fun emit(effect: ResourceEffect<V>) = selector(key.getValue()).emit(effect)

            context(scope: ResourceScope)
            override fun getDynamics(): FullDynamics<V> = selector(key.getValue()).getDynamics()
        }

    /**
     * Construct a view of this resource, through the given lens.
     * Effects applied to the resulting view are mapped back to the base resource.
     */
    fun <D, E> MutableResource<D>.view(lens: InvertibleFunction<D, E>): MutableResource<E> =
        object : MutableResource<E>, Resource<E> by ResourceMonad.map(this, lens) {
            context(scope: TaskScope)
            override fun emit(effect: ResourceEffect<E>) {
                this@view.emit({ d: Result<FullDynamics<D>> ->
                    // Errors in the lensing operations fault the resource
                    effect(d.mapCatching(DynamicsMonad.map(lens)))
                        .mapCatching(DynamicsMonad.map(lens.inverse))
                }.named(effect::toString))
            }
        }
}