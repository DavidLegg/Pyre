package gov.nasa.jpl.pyre.general.units.polynomial_quantity

import gov.nasa.jpl.pyre.general.resources.polynomial.Polynomial
import gov.nasa.jpl.pyre.general.units.Ring

object PolynomialRing : Ring<Polynomial> {
    override val zero: Polynomial = Polynomial.polynomial(0.0)
    override val one: Polynomial = Polynomial.polynomial(1.0)

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    override fun Polynomial.plus(other: Polynomial): Polynomial = this + other

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    override fun Polynomial.minus(other: Polynomial): Polynomial = this - other

    override fun Double.times(other: Polynomial): Polynomial = other * this

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    override fun Polynomial.times(other: Polynomial): Polynomial = this * other
}