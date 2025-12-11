package gov.nasa.jpl.pyre.general.units.quantity_resource

import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad
import gov.nasa.jpl.pyre.foundation.resources.discrete.DoubleResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DoubleResourceOperations
import gov.nasa.jpl.pyre.general.units.Field

object DoubleResourceField : Field<DoubleResource> {
    override val zero: DoubleResource = DiscreteResourceMonad.pure(0.0)
    override val one: DoubleResource = DiscreteResourceMonad.pure(1.0)

    override fun DoubleResource.plus(other: DoubleResource): DoubleResource =
        with(DoubleResourceOperations) { this@plus + other }

    override fun DoubleResource.minus(other: DoubleResource): DoubleResource =
        with(DoubleResourceOperations) { this@minus - other }

    override fun Double.times(other: DoubleResource): DoubleResource =
        with(DoubleResourceOperations) { this@times * other }

    override fun DoubleResource.times(other: DoubleResource): DoubleResource =
        with(DoubleResourceOperations) { this@times * other }

    override fun DoubleResource.div(other: DoubleResource): DoubleResource =
        with(DoubleResourceOperations) { this@div / other }
}