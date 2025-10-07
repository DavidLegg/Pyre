package gov.nasa.jpl.pyre.flame.results.profiles.discrete

import gov.nasa.jpl.pyre.flame.results.profiles.discrete.DiscreteProfile.DiscreteProfileMonad.map

typealias LongProfile = DiscreteProfile<Long>

object LongProfileOperations {
    infix fun LongProfile.greaterThan(other: LongProfile): BooleanProfile =
        map(this, other) { x, y -> x > y }
    infix fun LongProfile.greaterThanOrEquals(other: LongProfile): BooleanProfile =
        map(this, other) { x, y -> x >= y }
    infix fun LongProfile.lessThan(other: LongProfile): BooleanProfile =
        map(this, other) { x, y -> x < y }
    infix fun LongProfile.lessThanOrEquals(other: LongProfile): BooleanProfile =
        map(this, other) { x, y -> x <= y }

    operator fun LongProfile.unaryPlus(): LongProfile = map(this) { +it }
    operator fun LongProfile.unaryMinus(): LongProfile = map(this) { -it }
    operator fun LongProfile.plus(other: LongProfile): LongProfile = map(this, other) { x, y -> x + y }
    operator fun LongProfile.minus(other: LongProfile): LongProfile = map(this, other) { x, y -> x - y }
    operator fun LongProfile.times(other: LongProfile): LongProfile = map(this, other) { x, y -> x * y }
    operator fun LongProfile.div(other: LongProfile): LongProfile = map(this, other) { x, y -> x / y }

    fun LongProfile.toFloat(): FloatProfile = map(this, Long::toFloat)
    fun LongProfile.toDouble(): DoubleProfile = map(this, Long::toDouble)
}