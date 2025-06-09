package gov.nasa.jpl.pyre.spark.resources.discrete

import gov.nasa.jpl.pyre.ember.SimulationState.SimulationInitContext
import gov.nasa.jpl.pyre.spark.BasicSerializers.FLOAT_SERIALIZER
import gov.nasa.jpl.pyre.spark.resources.autoEffects
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.pure
import gov.nasa.jpl.pyre.spark.resources.discrete.IntResourceOperations.div
import gov.nasa.jpl.pyre.spark.resources.discrete.IntResourceOperations.minus
import gov.nasa.jpl.pyre.spark.resources.discrete.IntResourceOperations.plus
import gov.nasa.jpl.pyre.spark.resources.discrete.IntResourceOperations.rem
import gov.nasa.jpl.pyre.spark.resources.discrete.IntResourceOperations.times
import gov.nasa.jpl.pyre.spark.resources.resource
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope
import gov.nasa.jpl.pyre.spark.tasks.TaskScope
import kotlin.math.abs

typealias FloatResource = DiscreteResource<Float>
typealias MutableFloatResource = MutableDiscreteResource<Float>

object FloatResourceOperations {
    fun SimulationInitContext.discreteResource(name: String, value: Float) =
        resource(name, Discrete(value), discreteSerializer(FLOAT_SERIALIZER), autoEffects({ x, y ->
            x.expiry == y.expiry && abs(x.data.value - y.data.value) <= maxOf(abs(x.data.value), abs(y.data.value)) * 1e-6
        }))

    fun SparkInitContext.register(name: String, resource: DiscreteResource<Float>) {
        register(name, resource, FLOAT_SERIALIZER)
    }

    operator fun FloatResource.plus(other: FloatResource): FloatResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x + y }
    operator fun FloatResource.minus(other: FloatResource): FloatResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x - y }
    operator fun FloatResource.times(other: FloatResource): FloatResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x * y }
    operator fun FloatResource.div(other: FloatResource): FloatResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x / y }
    operator fun FloatResource.rem(other: FloatResource): FloatResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x % y }

    operator fun FloatResource.plus(other: Float): FloatResource = this + pure(other)
    operator fun FloatResource.minus(other: Float): FloatResource = this - pure(other)
    operator fun FloatResource.times(other: Float): FloatResource = this * pure(other)
    operator fun FloatResource.div(other: Float): FloatResource = this / pure(other)
    operator fun FloatResource.rem(other: Float): FloatResource = this % pure(other)

    operator fun Float.plus(other: FloatResource): FloatResource = pure(this) + other
    operator fun Float.minus(other: FloatResource): FloatResource = pure(this) - other
    operator fun Float.times(other: FloatResource): FloatResource = pure(this) * other
    operator fun Float.div(other: FloatResource): FloatResource = pure(this) / other
    operator fun Float.rem(other: FloatResource): FloatResource = pure(this) % other

    context(TaskScope<*>)
    suspend fun MutableFloatResource.increase(amount: Float) {
        emit { n: Float -> n + amount }
    }

    context(TaskScope<*>)
    suspend fun MutableFloatResource.decrease(amount: Float) {
        emit { n: Float -> n - amount }
    }
}