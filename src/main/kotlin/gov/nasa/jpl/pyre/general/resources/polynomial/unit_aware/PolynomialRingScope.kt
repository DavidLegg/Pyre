package gov.nasa.jpl.pyre.general.resources.polynomial.unit_aware

import gov.nasa.jpl.pyre.general.resources.polynomial.Polynomial
import gov.nasa.jpl.pyre.general.resources.polynomial.Polynomial.Companion.polynomial
import gov.nasa.jpl.pyre.general.units.RingScope

object PolynomialRingScope : RingScope<Polynomial> {
    override val zero: Polynomial = polynomial(0.0)
    override val one: Polynomial = polynomial(1.0)

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    override fun Polynomial.plus(other: Polynomial): Polynomial = this + other

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    override fun Polynomial.minus(other: Polynomial): Polynomial = this - other

    override fun Double.times(other: Polynomial): Polynomial = other * this

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    override fun Polynomial.times(other: Polynomial): Polynomial = this * other
}