package gov.nasa.jpl.pyre.spark.resources.discrete

import gov.nasa.jpl.pyre.ember.SimulationState.SimulationInitContext
import gov.nasa.jpl.pyre.spark.reporting.BasicSerializers.DOUBLE_SERIALIZER
import gov.nasa.jpl.pyre.spark.resources.autoEffects
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.pure
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.discreteSerializer
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.emit
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.register
import gov.nasa.jpl.pyre.spark.resources.resource
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
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

    fun SparkInitContext.registeredDiscreteResource(name: String, value: Double) =
        discreteResource(name, value).also { register(name, it) }

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

    operator fun DoubleResource.plus(other: Double): DoubleResource = this + pure(other)
    operator fun DoubleResource.minus(other: Double): DoubleResource = this - pure(other)
    operator fun DoubleResource.times(other: Double): DoubleResource = this * pure(other)
    operator fun DoubleResource.div(other: Double): DoubleResource = this / pure(other)
    operator fun DoubleResource.rem(other: Double): DoubleResource = this % pure(other)

    operator fun Double.plus(other: DoubleResource): DoubleResource = pure(this) + other
    operator fun Double.minus(other: DoubleResource): DoubleResource = pure(this) - other
    operator fun Double.times(other: DoubleResource): DoubleResource = pure(this) * other
    operator fun Double.div(other: DoubleResource): DoubleResource = pure(this) / other
    operator fun Double.rem(other: DoubleResource): DoubleResource = pure(this) % other

    context(TaskScope<*>)
    suspend fun MutableDoubleResource.increase(amount: Double) {
        emit { n: Double -> n + amount }
    }

    context(TaskScope<*>)
    suspend fun MutableDoubleResource.decrease(amount: Double) {
        emit { n: Double -> n - amount }
    }
}