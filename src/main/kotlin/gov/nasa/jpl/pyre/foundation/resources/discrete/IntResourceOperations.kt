package gov.nasa.jpl.pyre.foundation.resources.discrete

import gov.nasa.jpl.pyre.utilities.named
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad.pure
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.emit
import gov.nasa.jpl.pyre.foundation.resources.named
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope

typealias IntResource = DiscreteResource<Int>
typealias MutableIntResource = MutableDiscreteResource<Int>

object IntResourceOperations {
    operator fun IntResource.unaryPlus(): IntResource =
        map(this) { +it } named { "(+$this)"}
    operator fun IntResource.unaryMinus(): IntResource =
        map(this) { -it } named { "(-$this)"}
    operator fun IntResource.plus(other: IntResource): IntResource =
        map(this, other) { x, y -> x + y } named { "($this) + ($other)" }
    operator fun IntResource.minus(other: IntResource): IntResource =
        map(this, other) { x, y -> x - y } named { "($this) - ($other)" }
    operator fun IntResource.times(other: IntResource): IntResource =
        map(this, other) { x, y -> x * y } named { "($this) * ($other)" }
    operator fun IntResource.div(other: IntResource): IntResource =
        map(this, other) { x, y -> x / y } named { "($this) / ($other)" }
    operator fun IntResource.rem(other: IntResource): IntResource =
        map(this, other) { x, y -> x % y } named { "($this) % ($other)" }

    operator fun IntResource.plus(other: Int): IntResource = this + pure(other)
    operator fun IntResource.minus(other: Int): IntResource = this - pure(other)
    operator fun IntResource.times(other: Int): IntResource = this * pure(other)
    operator fun IntResource.div(other: Int): IntResource = this / pure(other)
    operator fun IntResource.rem(other: Int): IntResource = this % pure(other)

    operator fun Int.plus(other: IntResource): IntResource = pure(this) + other
    operator fun Int.minus(other: IntResource): IntResource = pure(this) - other
    operator fun Int.times(other: IntResource): IntResource = pure(this) * other
    operator fun Int.div(other: IntResource): IntResource = pure(this) / other
    operator fun Int.rem(other: IntResource): IntResource = pure(this) % other

    context(scope: TaskScope)
    suspend fun MutableIntResource.increment(amount: Int = 1) {
        emit({ n: Int -> n + amount } named { "Increase $this by $amount" })
    }

    context(scope: TaskScope)
    suspend fun MutableIntResource.decrement(amount: Int = 1) {
        emit({ n: Int -> n - amount } named { "Decrease $this by $amount" })
    }
}