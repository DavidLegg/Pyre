package gov.nasa.jpl.pyre.general.units.polynomial_quantity_resource

import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.decrease
import gov.nasa.jpl.pyre.general.units.quantity.DoubleField
import gov.nasa.jpl.pyre.general.units.quantity.Quantity

object IntegralQuantityResourceOperations {
    context (scope: TaskScope)
    suspend fun IntegralQuantityResource.increase(amount: Quantity) = context (DoubleField, IntegralPolynomialResourceScaling) {
        valueIn(unit).increase(amount.valueIn(unit))
    }

    context (scope: TaskScope)
    suspend fun IntegralQuantityResource.decrease(amount: Quantity) = context (DoubleField, IntegralPolynomialResourceScaling) {
        valueIn(unit).decrease(amount.valueIn(unit))
    }

    context (scope: TaskScope)
    suspend operator fun IntegralQuantityResource.plusAssign(amount: Quantity) = increase(amount)

    context (scope: TaskScope)
    suspend operator fun IntegralQuantityResource.minusAssign(amount: Quantity) = decrease(amount)
}
