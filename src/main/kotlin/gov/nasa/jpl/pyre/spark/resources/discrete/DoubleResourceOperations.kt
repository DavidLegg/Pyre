package gov.nasa.jpl.pyre.spark.resources.discrete

import gov.nasa.jpl.pyre.coals.named
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.pure
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.emit
import gov.nasa.jpl.pyre.spark.resources.named
import gov.nasa.jpl.pyre.spark.tasks.TaskScope

typealias DoubleResource = DiscreteResource<Double>
typealias MutableDoubleResource = MutableDiscreteResource<Double>

object DoubleResourceOperations {
    operator fun DoubleResource.plus(other: DoubleResource): DoubleResource =
        map(this, other) { x, y -> x + y } named { "($this) + ($other)" }
    operator fun DoubleResource.minus(other: DoubleResource): DoubleResource =
        map(this, other) { x, y -> x - y } named { "($this) - ($other)" }
    operator fun DoubleResource.times(other: DoubleResource): DoubleResource =
        map(this, other) { x, y -> x * y } named { "($this) * ($other)" }
    operator fun DoubleResource.div(other: DoubleResource): DoubleResource =
        map(this, other) { x, y -> x / y } named { "($this) / ($other)" }
    operator fun DoubleResource.rem(other: DoubleResource): DoubleResource =
        map(this, other) { x, y -> x % y } named { "($this) % ($other)" }

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

    infix fun DoubleResource.greaterThan(other: DoubleResource): BooleanResource =
        map(this, other) { x, y -> x > y } named { "($this) > ($other)" }
    infix fun DoubleResource.greaterThanOrEquals(other: DoubleResource): BooleanResource =
        map(this, other) { x, y -> x >= y } named { "($this) >= ($other)" }
    infix fun DoubleResource.lessThan(other: DoubleResource): BooleanResource =
        map(this, other) { x, y -> x < y } named { "($this) < ($other)" }
    infix fun DoubleResource.lessThanOrEquals(other: DoubleResource): BooleanResource =
        map(this, other) { x, y -> x <= y } named { "($this) <= ($other)" }

    infix fun DoubleResource.greaterThan(other: Double): BooleanResource =
        map(this) { it > other } named { "($this) > ($other)" }
    infix fun DoubleResource.greaterThanOrEquals(other: Double): BooleanResource =
        map(this) { it >= other } named { "($this) >= ($other)" }
    infix fun DoubleResource.lessThan(other: Double): BooleanResource =
        map(this) { it < other } named { "($this) < ($other)" }
    infix fun DoubleResource.lessThanOrEquals(other: Double): BooleanResource =
        map(this) { it <= other } named { "($this) <= ($other)" }

    infix fun Double.greaterThan(other: DoubleResource): BooleanResource =
        map(other) { this > it } named { "($this) > ($other)" }
    infix fun Double.greaterThanOrEquals(other: DoubleResource): BooleanResource =
        map(other) { this >= it } named { "($this) >= ($other)" }
    infix fun Double.lessThan(other: DoubleResource): BooleanResource =
        map(other) { this < it } named { "($this) < ($other)" }
    infix fun Double.lessThanOrEquals(other: DoubleResource): BooleanResource =
        map(other) { this <= it } named { "($this) <= ($other)" }

    context(scope: TaskScope)
    suspend fun MutableDoubleResource.increase(amount: Double) =
        emit({ n: Double -> n + amount } named { "Increase $this by $amount" })

    context(scope: TaskScope)
    suspend fun MutableDoubleResource.decrease(amount: Double) =
        emit({ n: Double -> n - amount } named { "Decrease $this by $amount" })

    context (scope: TaskScope)
    suspend operator fun MutableDoubleResource.plusAssign(amount: Double) = increase(amount)

    context (scope: TaskScope)
    suspend operator fun MutableDoubleResource.minusAssign(amount: Double) = decrease(amount)
}