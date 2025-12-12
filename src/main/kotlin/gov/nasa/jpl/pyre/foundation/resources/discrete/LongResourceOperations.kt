package gov.nasa.jpl.pyre.foundation.resources.discrete

import gov.nasa.jpl.pyre.utilities.named
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad.pure
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.emit
import gov.nasa.jpl.pyre.foundation.resources.fullyNamed
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.kernel.Name

typealias LongResource = DiscreteResource<Long>
typealias MutableLongResource = MutableDiscreteResource<Long>

object LongResourceOperations {
    operator fun LongResource.unaryPlus(): LongResource =
        map(this) { +it }.fullyNamed { Name("(+$this)") }
    operator fun LongResource.unaryMinus(): LongResource =
        map(this) { -it }.fullyNamed { Name("(-$this)") }
    operator fun LongResource.plus(other: LongResource): LongResource =
        map(this, other) { x, y -> x + y }.fullyNamed { Name("($this) + ($other)") }
    operator fun LongResource.minus(other: LongResource): LongResource =
        map(this, other) { x, y -> x - y }.fullyNamed { Name("($this) - ($other)") }
    operator fun LongResource.times(other: LongResource): LongResource =
        map(this, other) { x, y -> x * y }.fullyNamed { Name("($this) * ($other)") }
    operator fun LongResource.div(other: LongResource): LongResource =
        map(this, other) { x, y -> x / y }.fullyNamed { Name("($this) / ($other)") }
    operator fun LongResource.rem(other: LongResource): LongResource =
        map(this, other) { x, y -> x % y }.fullyNamed { Name("($this) % ($other)") }

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
    fun MutableLongResource.increment(amount: Long = 1) {
        emit({ n: Long -> n + amount } named { "Increase $this by $amount" })
    }

    context(scope: TaskScope)
    fun MutableLongResource.decrement(amount: Long = 1) {
        emit({ n: Long -> n - amount } named { "Decrease $this by $amount" })
    }
}