package gov.nasa.jpl.pyre.spark.resources.discrete

import gov.nasa.jpl.pyre.coals.named
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.pure
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.emit
import gov.nasa.jpl.pyre.spark.resources.named
import gov.nasa.jpl.pyre.spark.tasks.TaskScope

typealias LongResource = DiscreteResource<Long>
typealias MutableLongResource = MutableDiscreteResource<Long>

object LongResourceOperations {
    operator fun LongResource.plus(other: LongResource): LongResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x + y } named { "($this) + ($other)" }
    operator fun LongResource.minus(other: LongResource): LongResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x - y } named { "($this) - ($other)" }
    operator fun LongResource.times(other: LongResource): LongResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x * y } named { "($this) * ($other)" }
    operator fun LongResource.div(other: LongResource): LongResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x / y } named { "($this) / ($other)" }
    operator fun LongResource.rem(other: LongResource): LongResource =
        DiscreteResourceMonad.map(this, other) { x, y -> x % y } named { "($this) % ($other)" }

    operator fun LongResource.plus(other: Long): LongResource = this + pure(other)
    operator fun LongResource.minus(other: Long): LongResource = this - pure(other)
    operator fun LongResource.times(other: Long): LongResource = this * pure(other)
    operator fun LongResource.div(other: Long): LongResource = this / pure(other)
    operator fun LongResource.rem(other: Long): LongResource = this % pure(other)

    operator fun Long.plus(other: LongResource): LongResource = pure(this) + other
    operator fun Long.minus(other: LongResource): LongResource = pure(this) - other
    operator fun Long.times(other: LongResource): LongResource = pure(this) * other
    operator fun Long.div(other: LongResource): LongResource = pure(this) / other
    operator fun Long.rem(other: LongResource): LongResource = pure(this) % other

    context(scope: TaskScope)
    suspend fun MutableLongResource.increment(amount: Long = 1) {
        emit({ n: Long -> n + amount } named { "Increase $this by $amount" })
    }

    context(scope: TaskScope)
    suspend fun MutableLongResource.decrement(amount: Long = 1) {
        emit({ n: Long -> n - amount } named { "Decrease $this by $amount" })
    }
}