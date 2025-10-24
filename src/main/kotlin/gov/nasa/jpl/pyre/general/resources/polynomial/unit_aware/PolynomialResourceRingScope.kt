package gov.nasa.jpl.pyre.general.resources.polynomial.unit_aware

import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResource
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.constant
import gov.nasa.jpl.pyre.general.units.RingScope

object PolynomialResourceRingScope : RingScope<PolynomialResource> {
    override val zero: PolynomialResource = constant(0.0)
    override val one: PolynomialResource = constant(1.0)

    override fun PolynomialResource.plus(other: PolynomialResource): PolynomialResource =
        with(PolynomialResourceOperations) {
            this@plus + other
        }

    override fun PolynomialResource.minus(other: PolynomialResource): PolynomialResource =
        with(PolynomialResourceOperations) {
            this@minus - other
        }

    override fun Double.times(other: PolynomialResource): PolynomialResource =
        with(PolynomialResourceOperations) {
            this@times * other
        }

    override fun PolynomialResource.times(other: PolynomialResource): PolynomialResource =
        with(PolynomialResourceOperations) {
            this@times * other
        }
}