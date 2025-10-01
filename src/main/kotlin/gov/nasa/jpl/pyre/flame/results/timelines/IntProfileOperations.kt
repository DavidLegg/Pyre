package gov.nasa.jpl.pyre.flame.results.timelines

import gov.nasa.jpl.pyre.flame.results.timelines.DiscreteProfile.DiscreteProfileMonad.map

typealias IntProfile = DiscreteProfile<Int>

object IntProfileOperations {
    infix fun IntProfile.greaterThan(other: IntProfile): BooleanProfile =
        map(this, other) { x, y -> x > y }
    infix fun IntProfile.greaterThanOrEquals(other: IntProfile): BooleanProfile =
        map(this, other) { x, y -> x >= y }
    infix fun IntProfile.lessThan(other: IntProfile): BooleanProfile =
        map(this, other) { x, y -> x < y }
    infix fun IntProfile.lessThanOrEquals(other: IntProfile): BooleanProfile =
        map(this, other) { x, y -> x <= y }

    operator fun IntProfile.unaryPlus(): IntProfile = map(this) { +it }
    operator fun IntProfile.unaryMinus(): IntProfile = map(this) { -it }
    operator fun IntProfile.plus(other: IntProfile): IntProfile = map(this, other) { x, y -> x + y }
    operator fun IntProfile.minus(other: IntProfile): IntProfile = map(this, other) { x, y -> x - y }
    operator fun IntProfile.times(other: IntProfile): IntProfile = map(this, other) { x, y -> x * y }
    operator fun IntProfile.div(other: IntProfile): IntProfile = map(this, other) { x, y -> x / y }

    fun IntProfile.toFloat(): FloatProfile = map(this, Int::toFloat)
    fun IntProfile.toDouble(): DoubleProfile = map(this, Int::toDouble)
    fun IntProfile.toLong(): LongProfile = map(this, Int::toLong)
}