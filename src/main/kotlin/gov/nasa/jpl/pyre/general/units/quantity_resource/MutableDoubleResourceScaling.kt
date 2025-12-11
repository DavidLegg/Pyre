package gov.nasa.jpl.pyre.general.units.quantity_resource

import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteMonad.map
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDoubleResource
import gov.nasa.jpl.pyre.general.resources.lens.MutableResourceLens.view
import gov.nasa.jpl.pyre.general.units.Scaling
import gov.nasa.jpl.pyre.utilities.InvertibleFunction

object MutableDoubleResourceScaling : Scaling<MutableDoubleResource> {
    // Since scaling is invertible, we can scale a mutable double resource, preserving mutability, through a view.
    override fun Double.times(other: MutableDoubleResource): MutableDoubleResource =
        other.view(InvertibleFunction.of(map { this * it }, map { it / this }))
}