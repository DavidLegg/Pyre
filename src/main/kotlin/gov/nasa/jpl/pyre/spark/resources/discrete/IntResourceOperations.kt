package gov.nasa.jpl.pyre.spark.resources.discrete

import gov.nasa.jpl.pyre.ember.SimulationState.SimulationInitContext
import gov.nasa.jpl.pyre.spark.BasicSerializers
import gov.nasa.jpl.pyre.spark.BasicSerializers.INT_SERIALIZER
import gov.nasa.jpl.pyre.spark.resources.resource
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope
import gov.nasa.jpl.pyre.spark.tasks.TaskScope

typealias IntResource = DiscreteResource<Int>
typealias MutableIntResource = MutableDiscreteResource<Int>

object IntResourceOperations {
    fun SimulationInitContext.discreteResource(name: String, value: Int) =
        discreteResource(name, value, INT_SERIALIZER)

    fun SparkInitContext.register(name: String, resource: DiscreteResource<Int>) {
        register(name, resource, BasicSerializers.INT_SERIALIZER)
    }

    operator fun IntResource.plus(other: IntResource): IntResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x + y }
    operator fun IntResource.minus(other: IntResource): IntResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x - y }
    operator fun IntResource.times(other: IntResource): IntResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x * y }
    operator fun IntResource.div(other: IntResource): IntResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x / y }
    operator fun IntResource.rem(other: IntResource): IntResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x % y }

    context(TaskScope<*>)
    suspend fun MutableIntResource.increment(amount: Int = 1) {
        emit { n: Int -> n + amount }
    }

    context(TaskScope<*>)
    suspend fun MutableIntResource.decrement(amount: Int = 1) {
        emit { n: Int -> n - amount }
    }
}