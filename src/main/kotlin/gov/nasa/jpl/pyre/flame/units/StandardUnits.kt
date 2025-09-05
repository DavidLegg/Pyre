package gov.nasa.jpl.pyre.flame.units

import gov.nasa.jpl.pyre.flame.units.UnitAware.Companion.times

object StandardUnits {
    // SI Base Units
    val SECOND = Unit.base("s", "time")
    val METER = Unit.base("m", "length")
    val KILOGRAM = Unit.base("kg", "mass")
    val AMPERE = Unit.base("A", "electric current")
    val KELVIN = Unit.base("K", "temperature")
    val MOLE = Unit.base("mol", "amount of substance")
    val CANDELA = Unit.base("cd", "luminous intensity")

    // Non-SI Base Units, found to be useful in practice
    // Some might balk at the idea of introducing new dimensions, especially for angles.
    // The choice of which quantities to consider "dimensionless" is fundamentally arbitrary.
    // To see this, consider the historical CGS system of units, with 3 base dimensions.
    // In this system, quantities like current are dimensionally equivalent to some mechanical quantity.
    // Dividing the mechanical quantity by current would result in a dimensionless quantity in CGS, but not in SI.
    // Choosing to add more dimensions to a unit system improves precision by reducing the possibility of conflating
    // quantities with the same dimension but different intents. This is balanced by the increased need to give
    // conversion factors as we increase the number of dimensions, where before a conflation may have been convenient.
    val RADIAN = Unit.base("rad", "angle")
    val BIT = Unit.base("b", "information")

    // Some common non-base units, in base dimensions
    val MICROSECOND = Unit.derived("μs", 1e-6 * SECOND)
    val MILLISECOND = Unit.derived("ms", 1e-3 * SECOND)
    val MINUTE = Unit.derived("min", 60.0 * SECOND)
    val HOUR = Unit.derived("hr", 60.0 * MINUTE)
    val DAY = Unit.derived("day", 24.0 * HOUR)

    val KILOMETER = Unit.derived("km", 1e3 * METER)

    val GRAM = Unit.derived("g", 1e-3 * KILOGRAM)

    val DEGREE = Unit.derived("deg", (180.0 / Math.PI) * RADIAN)
    val ROTATION = Unit.derived("rotation", 2.0 * Math.PI * RADIAN)

    val BYTE = Unit.derived("B", 8.0 * BIT)
    val KILOBYTE = Unit.derived("kB", 1e3 * BYTE)
    val MEGABYTE = Unit.derived("MB", 1e6 * BYTE)
    val GIGABYTE = Unit.derived("GB", 1e9 * BYTE)
    val TERABYTE = Unit.derived("TB", 1e12 * BYTE)
    val KIBIBYTE = Unit.derived("KiB", 1024.0 * BYTE)
    val MEBIBYTE = Unit.derived("MiB", 1024.0 * KIBIBYTE)
    val GIBIBYTE = Unit.derived("GiB", 1024.0 * MEBIBYTE)
    val TEBIBYTE = Unit.derived("TiB", 1024.0 * GIBIBYTE)

    // Some common non-base units, in non-base dimensions
    val METERS_PER_SECOND = Unit.derived("m/s", METER / SECOND)
    val KILOMETERS_PER_SECOND = Unit.derived("km/s", KILOMETER / SECOND)
    val METERS_PER_SECOND_SQUARED = Unit.derived("m/s", METER / SECOND.pow(2))
    val NEWTON = Unit.derived("N", KILOGRAM * METERS_PER_SECOND_SQUARED)
    val JOULE = Unit.derived("J", NEWTON * METER)
    val WATT = Unit.derived("W", JOULE / SECOND)
}