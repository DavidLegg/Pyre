package gov.nasa.jpl.pyre.spark.resources.discrete

import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.pure
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.emit
import gov.nasa.jpl.pyre.spark.tasks.TaskScope

typealias DoubleResource = DiscreteResource<Double>
typealias MutableDoubleResource = MutableDiscreteResource<Double>

object DoubleResourceOperations {
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