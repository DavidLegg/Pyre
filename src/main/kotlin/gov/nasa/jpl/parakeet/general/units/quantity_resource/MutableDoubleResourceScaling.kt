package gov.nasa.jpl.parakeet.general.units.quantity_resource

import gov.nasa.jpl.parakeet.foundation.resources.discrete.DiscreteMonad.map
import gov.nasa.jpl.parakeet.foundation.resources.discrete.MutableDoubleResource
import gov.nasa.jpl.parakeet.general.resources.lens.MutableResourceLens.view
import gov.nasa.jpl.parakeet.general.units.Scaling
import gov.nasa.jpl.parakeet.utilities.InvertibleFunction

object MutableDoubleResourceScaling : Scaling<MutableDoubleResource> {
    // Since scaling is invertible, we can scale a mutable double resource, preserving mutability, through a view.
    override fun Double.times(other: MutableDoubleResource): MutableDoubleResource =
        other.view(InvertibleFunction.of(map { this * it }, map { it / this }))
}