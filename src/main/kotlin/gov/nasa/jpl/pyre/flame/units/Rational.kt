package gov.nasa.jpl.pyre.flame.units

import kotlin.math.abs
import kotlin.math.sign

class Rational private constructor(val numerator: Int, val denominator: Int) : Comparable<Rational> {
    operator fun unaryPlus() = this
    operator fun unaryMinus() = Rational(-numerator, denominator)
    operator fun plus(other: Rational) =
        Rational.of(numerator * other.denominator + denominator * other.numerator, denominator * other.denominator)
    operator fun minus(other: Rational) =
        Rational.of(numerator * other.denominator - denominator * other.numerator, denominator * other.denominator)
    operator fun times(other: Rational) =
        Rational.of(numerator * other.numerator, denominator * other.denominator)
    operator fun div(other: Rational) =
        Rational.of(numerator * other.denominator, denominator * other.numerator)

    override fun compareTo(other: Rational): Int =
        (numerator * other.denominator).compareTo(other.numerator * denominator) * denominator.sign * other.denominator.sign

    override fun equals(other: Any?): Boolean {
        return (this === other) || (other as? Rational)?.let {
            numerator == it.numerator && denominator == it.denominator
        } ?: false
    }

    override fun hashCode(): Int {
        // Java / IntelliJ default hash function:
        return 31 * numerator + denominator
    }

    companion object {
        fun of(numerator: Int, denominator: Int = 1): Rational {
            // Special case: infinity immediately returns their normalized forms
            if (denominator == 0) return Rational(numerator.sign, 0)

            var a = abs(numerator)
            var b = abs(denominator)
            if (b > a) {
                val temp = a
                a = b
                b = temp
            }
            while (b != 0) {
                val temp = b
                b = a % b
                a = temp
            }
            val lcd = a
            return Rational(denominator.sign * numerator / lcd, abs(denominator) / lcd)
        }

        val ZERO = Rational.of(0)
        val ONE = Rational.of(1)
    }
}