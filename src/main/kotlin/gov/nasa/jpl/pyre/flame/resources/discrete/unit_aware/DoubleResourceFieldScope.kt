package gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware

import gov.nasa.jpl.pyre.flame.units.FieldScope
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.pure
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations

object DoubleResourceFieldScope : FieldScope<DoubleResource> {
    override val zero = pure(0.0)
    override val one = pure(1.0)

    override fun DoubleResource.div(other: DoubleResource): DoubleResource =
        with(DoubleResourceOperations) { this@div / other }

    override fun DoubleResource.times(other: DoubleResource): DoubleResource =
        with(DoubleResourceOperations) { this@times * other }

    override fun DoubleResource.plus(other: DoubleResource): DoubleResource =
        with(DoubleResourceOperations) { this@plus + other }

    override fun DoubleResource.minus(other: DoubleResource): DoubleResource =
        with(DoubleResourceOperations) { this@minus - other }

    override fun Double.times(other: DoubleResource): DoubleResource =
        with(DoubleResourceOperations) { this@times * other }
}