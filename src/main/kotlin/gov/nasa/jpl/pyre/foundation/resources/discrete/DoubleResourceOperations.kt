package gov.nasa.jpl.pyre.foundation.resources.discrete

import gov.nasa.jpl.pyre.utilities.named
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad.pure
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.emit
import gov.nasa.jpl.pyre.foundation.resources.fullyNamed
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.kernel.Name

typealias DoubleResource = DiscreteResource<Double>
typealias MutableDoubleResource = MutableDiscreteResource<Double>

object DoubleResourceOperations {
    operator fun DoubleResource.unaryPlus(): DoubleResource =
        map(this) { +it }.fullyNamed { Name("(+$this)") }
    operator fun DoubleResource.unaryMinus(): DoubleResource =
        map(this) { -it }.fullyNamed { Name("(-$this)") }
    operator fun DoubleResource.plus(other: DoubleResource): DoubleResource =
        map(this, other) { x, y -> x + y }.fullyNamed { Name("($this) + ($other)") }
    operator fun DoubleResource.minus(other: DoubleResource): DoubleResource =
        map(this, other) { x, y -> x - y }.fullyNamed { Name("($this) - ($other)") }
    operator fun DoubleResource.times(other: DoubleResource): DoubleResource =
        map(this, other) { x, y -> x * y }.fullyNamed { Name("($this) * ($other)") }
    operator fun DoubleResource.div(other: DoubleResource): DoubleResource =
        map(this, other) { x, y -> x / y }.fullyNamed { Name("($this) / ($other)") }
    operator fun DoubleResource.rem(other: DoubleResource): DoubleResource =
        map(this, other) { x, y -> x % y }.fullyNamed { Name("($this) % ($other)") }

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

    context(scope: TaskScope)
    fun MutableDoubleResource.increase(amount: Double) =
        emit({ n: Double -> n + amount } named { "Increase $this by $amount" })

    context(scope: TaskScope)
    fun MutableDoubleResource.decrease(amount: Double) =
        emit({ n: Double -> n - amount } named { "Decrease $this by $amount" })

    context (scope: TaskScope)
    operator fun MutableDoubleResource.plusAssign(amount: Double) = increase(amount)

    context (scope: TaskScope)
    operator fun MutableDoubleResource.minusAssign(amount: Double) = decrease(amount)
}