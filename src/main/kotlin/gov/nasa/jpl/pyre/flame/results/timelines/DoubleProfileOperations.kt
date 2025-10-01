package gov.nasa.jpl.pyre.flame.results.timelines

import gov.nasa.jpl.pyre.flame.results.timelines.DiscreteProfile.DiscreteProfileMonad.map

typealias DoubleProfile = DiscreteProfile<Double>

object DoubleProfileOperations {
    infix fun DoubleProfile.greaterThan(other: DoubleProfile): BooleanProfile =
        map(this, other) { x, y -> x > y }
    infix fun DoubleProfile.greaterThanOrEquals(other: DoubleProfile): BooleanProfile =
        map(this, other) { x, y -> x >= y }
    infix fun DoubleProfile.lessThan(other: DoubleProfile): BooleanProfile =
        map(this, other) { x, y -> x < y }
    infix fun DoubleProfile.lessThanOrEquals(other: DoubleProfile): BooleanProfile =
        map(this, other) { x, y -> x <= y }
}