package gov.nasa.jpl.pyre.general.resources.polynomial.unit_aware

import gov.nasa.jpl.pyre.general.resources.polynomial.Polynomial
import gov.nasa.jpl.pyre.general.resources.polynomial.Polynomial.Companion.polynomial
import gov.nasa.jpl.pyre.general.units.Quantity
import gov.nasa.jpl.pyre.general.units.QuantityOperations.valueIn
import gov.nasa.jpl.pyre.general.units.StandardUnits.SECOND
import gov.nasa.jpl.pyre.general.units.UnitAware

typealias PolynomialQuantity = UnitAware<Polynomial>

object PolynomialQuantityOperations {
    fun polynomial(vararg coefficients: Quantity): PolynomialQuantity {
        require(coefficients.isNotEmpty()) {
            "Must provide at least one coefficient for a polynomial quantity"
        }
        val unit = coefficients.first().unit
        val scalarCoefficients = coefficients
            .mapIndexed { i, c -> c.valueIn(unit / SECOND.pow(i)) }
            .toDoubleArray()
        return UnitAware(polynomial(*scalarCoefficients), unit)
    }

    operator fun PolynomialQuantity.plus(other: PolynomialQuantity): PolynomialQuantity =
        with (PolynomialRingScope) {
            with (UnitAware.Companion) {
                this@plus + other
            }
        }

    operator fun PolynomialQuantity.minus(other: PolynomialQuantity): PolynomialQuantity =
        with (PolynomialRingScope) {
            with (UnitAware.Companion) {
                this@minus - other
            }
        }

    operator fun PolynomialQuantity.times(other: PolynomialQuantity): PolynomialQuantity =
        with (PolynomialRingScope) {
            with (UnitAware.Companion) {
                this@times * other
            }
        }

    operator fun Double.times(other: PolynomialQuantity): PolynomialQuantity =
        with (PolynomialRingScope) {
            with (UnitAware.Companion) {
                this@times * other
            }
        }

    operator fun PolynomialQuantity.times(other: Double): PolynomialQuantity =
        with (PolynomialRingScope) {
            with (UnitAware.Companion) {
                this@times * other
            }
        }

    operator fun PolynomialQuantity.div(other: Double): PolynomialQuantity =
        with (PolynomialRingScope) {
            with (UnitAware.Companion) {
                this@div / other
            }
        }
}