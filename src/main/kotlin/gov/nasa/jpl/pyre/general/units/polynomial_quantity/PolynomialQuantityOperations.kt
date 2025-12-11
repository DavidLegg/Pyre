package gov.nasa.jpl.pyre.general.units.polynomial_quantity

import gov.nasa.jpl.pyre.general.resources.polynomial.Polynomial
import gov.nasa.jpl.pyre.general.resources.polynomial.Polynomial.Companion.polynomial
import gov.nasa.jpl.pyre.general.units.quantity.Quantity
import gov.nasa.jpl.pyre.general.units.StandardUnits.SECOND
import gov.nasa.jpl.pyre.general.units.UnitAware
import gov.nasa.jpl.pyre.general.units.quantity.DoubleField

typealias PolynomialQuantity = UnitAware<Polynomial>

object PolynomialQuantityOperations {
    fun polynomial(vararg coefficients: Quantity): PolynomialQuantity = context(DoubleField) {
        require(coefficients.isNotEmpty()) {
            "Must provide at least one coefficient for a polynomial quantity"
        }
        val unit = coefficients.first().unit
        val scalarCoefficients = coefficients
            .mapIndexed { i, c -> c.valueIn(unit / SECOND.pow(i)) }
            .toDoubleArray()
        UnitAware(polynomial(*scalarCoefficients), unit)
    }
}