package gov.nasa.jpl.pyre.spark.resources.discrete

import gov.nasa.jpl.pyre.ember.SimulationState.SimulationInitContext
import gov.nasa.jpl.pyre.spark.BasicSerializers.LONG_SERIALIZER
import gov.nasa.jpl.pyre.spark.resources.resource
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope
import gov.nasa.jpl.pyre.spark.tasks.TaskScope

typealias LongResource = DiscreteResource<Long>
typealias MutableLongResource = MutableDiscreteResource<Long>

object LongResourceOperations {
    fun SimulationInitContext.discreteResource(name: String, value: Long) =
        discreteResource(name, value, LONG_SERIALIZER)

    fun SparkInitContext.register(name: String, resource: DiscreteResource<Long>) {
        register(name, resource, LONG_SERIALIZER)
    }

    operator fun LongResource.plus(other: LongResource): LongResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x + y }
    operator fun LongResource.minus(other: LongResource): LongResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x - y }
    operator fun LongResource.times(other: LongResource): LongResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x * y }
    operator fun LongResource.div(other: LongResource): LongResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x / y }
    operator fun LongResource.rem(other: LongResource): LongResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x % y }

    context(TaskScope<*>)
    suspend fun MutableLongResource.increment(amount: Long = 1) {
        emit { n: Long -> n + amount }
    }

    context(TaskScope<*>)
    suspend fun MutableLongResource.decrement(amount: Long = 1) {
        emit { n: Long -> n - amount }
    }
}