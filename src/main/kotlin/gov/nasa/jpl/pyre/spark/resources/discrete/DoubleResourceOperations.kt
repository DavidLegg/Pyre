package gov.nasa.jpl.pyre.spark.resources.discrete

import gov.nasa.jpl.pyre.ember.SimulationState.SimulationInitContext
import gov.nasa.jpl.pyre.spark.BasicSerializers.DOUBLE_SERIALIZER
import gov.nasa.jpl.pyre.spark.resources.autoEffects
import gov.nasa.jpl.pyre.spark.resources.resource
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope
import gov.nasa.jpl.pyre.spark.tasks.TaskScope
import kotlin.math.abs

typealias DoubleResource = DiscreteResource<Double>
typealias MutableDoubleResource = MutableDiscreteResource<Double>

object DoubleResourceOperations {
    fun SimulationInitContext.discreteResource(name: String, value: Double) =
        resource(name, Discrete(value), discreteSerializer(DOUBLE_SERIALIZER), autoEffects({ x, y ->
            x.expiry == y.expiry && abs(x.data.value - y.data.value) <= maxOf(abs(x.data.value), abs(y.data.value)) * 1e-14
        }))

    fun SparkInitContext.register(name: String, resource: DiscreteResource<Double>) {
        register(name, resource, DOUBLE_SERIALIZER)
    }

    operator fun DoubleResource.plus(other: DoubleResource): DoubleResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x + y }
    operator fun DoubleResource.minus(other: DoubleResource): DoubleResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x - y }
    operator fun DoubleResource.times(other: DoubleResource): DoubleResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x * y }
    operator fun DoubleResource.div(other: DoubleResource): DoubleResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x / y }
    operator fun DoubleResource.rem(other: DoubleResource): DoubleResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x % y }

    context(TaskScope<*>)
    suspend fun MutableDoubleResource.increase(amount: Double) {
        emit { n: Double -> n + amount }
    }

    context(TaskScope<*>)
    suspend fun MutableDoubleResource.decrease(amount: Double) {
        emit { n: Double -> n - amount }
    }
}