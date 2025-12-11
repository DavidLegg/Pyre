package gov.nasa.jpl.pyre.general.units.polynomial_quantity_resource

import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.general.resources.polynomial.IntegralResource
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResource
import gov.nasa.jpl.pyre.general.units.Scaling

object IntegralPolynomialResourceScaling : Scaling<IntegralResource> {
    override fun Double.times(other: IntegralResource): IntegralResource =
        object : IntegralResource, PolynomialResource by this@times * other {
            context(scope: TaskScope)
            override suspend fun increase(amount: Double) = other.increase(amount / this@times)

            context(scope: TaskScope)
            override suspend fun set(amount: Double) = other.set(amount / this@times)
        }
}