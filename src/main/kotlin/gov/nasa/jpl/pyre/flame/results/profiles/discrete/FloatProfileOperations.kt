package gov.nasa.jpl.pyre.flame.results.profiles.discrete

import gov.nasa.jpl.pyre.flame.results.profiles.discrete.DiscreteProfile.DiscreteProfileMonad.map

typealias FloatProfile = DiscreteProfile<Float>

object FloatProfileOperations {
    infix fun FloatProfile.greaterThan(other: FloatProfile): BooleanProfile =
        map(this, other) { x, y -> x > y }
    infix fun FloatProfile.greaterThanOrEquals(other: FloatProfile): BooleanProfile =
        map(this, other) { x, y -> x >= y }
    infix fun FloatProfile.lessThan(other: FloatProfile): BooleanProfile =
        map(this, other) { x, y -> x < y }
    infix fun FloatProfile.lessThanOrEquals(other: FloatProfile): BooleanProfile =
        map(this, other) { x, y -> x <= y }

    operator fun FloatProfile.unaryPlus(): FloatProfile = map(this) { +it }
    operator fun FloatProfile.unaryMinus(): FloatProfile = map(this) { -it }
    operator fun FloatProfile.plus(other: FloatProfile): FloatProfile = map(this, other) { x, y -> x + y }
    operator fun FloatProfile.minus(other: FloatProfile): FloatProfile = map(this, other) { x, y -> x - y }
    operator fun FloatProfile.times(other: FloatProfile): FloatProfile = map(this, other) { x, y -> x * y }
    operator fun FloatProfile.div(other: FloatProfile): FloatProfile = map(this, other) { x, y -> x / y }

    fun FloatProfile.toDouble(): DoubleProfile = map(this, Float::toDouble)
}