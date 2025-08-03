package gov.nasa.jpl.pyre.flame.resources.lens

import gov.nasa.jpl.pyre.spark.resources.FullDynamics
import gov.nasa.jpl.pyre.spark.resources.MutableResource
import gov.nasa.jpl.pyre.spark.resources.ResourceEffect
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.tasks.CellsReadableScope
import gov.nasa.jpl.pyre.spark.tasks.TaskScope

object MutableResourceLens {
    fun <K, V> select(key: DiscreteResource<K>, selector: (K) -> MutableResource<V>): MutableResource<V> =
        object : MutableResource<V> {
            context(scope: TaskScope)
            override suspend fun emit(effect: ResourceEffect<V>) = selector(key.getValue()).emit(effect)

            context(scope: CellsReadableScope)
            override suspend fun getDynamics(): FullDynamics<V> = selector(key.getValue()).getDynamics()
        }
}