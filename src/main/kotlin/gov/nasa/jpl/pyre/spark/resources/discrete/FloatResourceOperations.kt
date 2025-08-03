package gov.nasa.jpl.pyre.spark.resources.discrete

import gov.nasa.jpl.pyre.coals.named
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.pure
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.emit
import gov.nasa.jpl.pyre.spark.resources.named
import gov.nasa.jpl.pyre.spark.tasks.TaskScope

typealias FloatResource = DiscreteResource<Float>
typealias MutableFloatResource = MutableDiscreteResource<Float>

object FloatResourceOperations {
    operator fun FloatResource.plus(other: FloatResource): FloatResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x + y } named { "($this) + ($other)" }
    operator fun FloatResource.minus(other: FloatResource): FloatResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x - y } named { "($this) - ($other)" }
    operator fun FloatResource.times(other: FloatResource): FloatResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x * y } named { "($this) * ($other)" }
    operator fun FloatResource.div(other: FloatResource): FloatResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x / y } named { "($this) / ($other)" }
    operator fun FloatResource.rem(other: FloatResource): FloatResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x % y } named { "($this) % ($other)" }

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

    context(scope: TaskScope)
    suspend fun MutableFloatResource.increase(amount: Float) {
        emit({ n: Float -> n + amount } named { "Increase $this by $amount" })
    }

    context(scope: TaskScope)
    suspend fun MutableFloatResource.decrease(amount: Float) {
        emit({ n: Float -> n - amount } named { "Decrease $this by $amount" })
    }
}