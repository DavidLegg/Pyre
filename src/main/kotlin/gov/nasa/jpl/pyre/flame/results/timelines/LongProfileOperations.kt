package gov.nasa.jpl.pyre.flame.results.timelines

import gov.nasa.jpl.pyre.flame.results.timelines.DiscreteProfile.DiscreteProfileMonad.map

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

    fun LongProfile.toFloat(): FloatProfile = map(this, Long::toFloat)
    fun LongProfile.toDouble(): DoubleProfile = map(this, Long::toDouble)
}