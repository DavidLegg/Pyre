package gov.nasa.jpl.pyre.general.units.polynomial_quantity_resource

import gov.nasa.jpl.pyre.general.resources.lens.MutableResourceLens.view
import gov.nasa.jpl.pyre.general.resources.polynomial.MutablePolynomialResource
import gov.nasa.jpl.pyre.general.resources.polynomial.times
import gov.nasa.jpl.pyre.general.units.Scaling
import gov.nasa.jpl.pyre.utilities.InvertibleFunction

object MutablePolynomialResourceScaling : Scaling<MutablePolynomialResource> {
    // Since scaling is invertible, we can scale a mutable polynomial resource, preserving mutability, through a view.
    override fun Double.times(other: MutablePolynomialResource): MutablePolynomialResource =
        other.view(InvertibleFunction.Companion.of({ this * it }, { it / this }))
}