package gov.nasa.jpl.pyre.general.units

import gov.nasa.jpl.pyre.general.units.quantity.DoubleField
import kotlin.math.pow

/**
 * A simplified "linear" unit, for efficient unit-aware modeling.
 * This can only represent a "linear" unit, ones where all conversion is just a scaling factor.
 * Units like absolute degrees Fahrenheit or absolute degrees Celsius are not representable, because they require shifting.
 * Relative degrees Fahrenheit and relative degrees Celsius *are* representable, but the distinction between
 * relative and absolute units is often neglected.
 */
class Unit private constructor(
    val name: String?,
    val scale: Double,
    val dimension: Dimension,
) {
    operator fun times(other: Unit) = Unit(null, scale * other.scale, dimension * other.dimension)

    operator fun div(other: Unit) = Unit(null, scale / other.scale, dimension / other.dimension)

    fun pow(power: Rational) = Unit(null, scale.pow(power.numerator.toDouble() / power.denominator.toDouble()), dimension.pow(power))
    fun pow(power: Int) = Unit(null, scale.pow(power), dimension.pow(power))

    override fun toString(): String = name ?: dimension.baseUnitString().let {
        if (scale == 1.0) it else "($scale $it)"
    }

    companion object {
        /**
         * The "unit" of unitless, dimensionless scalars (numbers).
         * Note that dimensionless quantities can still have units; for example, "dozens", "millions", or "percent".
         */
        val SCALAR = Unit("(scalar)", 1.0, Dimension.SCALAR)

        /**
         * Define a new base unit and base dimension. For example:
         * ```
         * val KILOGRAM = Unit.base("kg", "mass")
         * ```
         */
        fun base(name: String, dimensionName: String): Unit =
            Dimension.baseUnit(dimensionName) { Unit(name, 1.0, it) }

        /**
         * Define a named unit derived from a quantity, i.e. a (potentially anonymous) unit and a scale.
         */
        fun derived(name: String, definition: UnitAware<Double>): Unit = with (DoubleField) {
            Unit(name, definition.valueIn(definition.unit) * definition.unit.scale, definition.unit.dimension)
        }

        /**
         * Define a named unit derived from another, usually anonymous, unit.
         */
        fun derived(name: String, definition: Unit) = Unit(name, definition.scale, definition.dimension)
    }
}