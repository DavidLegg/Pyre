package gov.nasa.jpl.pyre.flame.results.timelines

import gov.nasa.jpl.pyre.flame.results.timelines.DiscreteProfile.DiscreteProfileMonad.map

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

    fun FloatProfile.toDouble(): DoubleProfile = map(this, Float::toDouble)
}