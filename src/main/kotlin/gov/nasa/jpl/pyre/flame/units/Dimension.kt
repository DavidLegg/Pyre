package gov.nasa.jpl.pyre.flame.units

import gov.nasa.jpl.pyre.flame.units.Rational.Companion.ONE
import gov.nasa.jpl.pyre.flame.units.Rational.Companion.ZERO

class Dimension private constructor(
    private val definition: Map<BaseDimension, Rational>
) {
    // Use the standard identity equality for base dimensions, for performance
    private class BaseDimension(val name: String) {
        override fun toString(): String = name
    }

    // Derived dimensions are created by combining dimensions mathematically
    operator fun times(other: Dimension) = dimension((definition.keys + other.definition.keys).associateWith {
        definition.getOrDefault(it, ZERO) + other.definition.getOrDefault(it, ZERO)
    })

    operator fun div(other: Dimension) = dimension((definition.keys + other.definition.keys).associateWith {
        definition.getOrDefault(it, ZERO) - other.definition.getOrDefault(it, ZERO)
    })

    fun pow(power: Rational) = dimension(definition.mapValues { (_, p) -> p * power })
    fun pow(power: Int) = pow(Rational.of(power))

    override fun equals(other: Any?): Boolean =
        (this === other) || (other as? Dimension)?.let { definition == it.definition } ?: false

    override fun hashCode(): Int = definition.hashCode()

    override fun toString(): String = productString(definition)

    /**
     * Supports [Unit.toString()], not meant to be called directly in user code.
     */
    fun baseUnitString(): String = productString(definition.mapKeys { BASE_UNITS.getValue(it.key) })

    private fun productString(terms: Map<*, Rational>): String = terms
        .map { (x, p) ->
            when {
                p == ONE -> x.toString()
                p.denominator == 1 -> "$x^$p"
                else -> "$x^($p)"
            }
        }
        // Sort the resulting string terms for consistency
        .sorted()
        .joinToString(" ")

    companion object {
        /**
         * The dimension of pure scalars (numbers). Also known as "dimensionless" quantities.
         */
        val SCALAR = Dimension(emptyMap())

        private val BASE_UNITS = mutableMapOf<BaseDimension, Unit>()

        // Base dimensions can only be created through the base function
        /**
         * Create a base dimension. Base dimensions are incompatible with all other dimensions.
         *
         * Instead of calling this function directly, consider calling [Unit.base] to create the unit with its dimension.
         *
         * For example, SI base dimensions include mass, time, and length.
         * Non-base dimensions are derived by multiplying, dividing, and taking powers of, base dimensions.
         * For example, speed = length / time, and force = mass * length / time^2
         */
        fun baseUnit(dimensionName: String, unitConstructor: (Dimension) -> Unit): Unit {
            val baseDimension = BaseDimension(dimensionName)
            val dimension = Dimension(mapOf(baseDimension to ONE))
            return unitConstructor(dimension).also { BASE_UNITS[baseDimension] = it }
        }

        private fun dimension(definition: Map<BaseDimension, Rational>): Dimension {
            return Dimension(definition.filter { it.value != ZERO }.toMap())
        }
    }
}