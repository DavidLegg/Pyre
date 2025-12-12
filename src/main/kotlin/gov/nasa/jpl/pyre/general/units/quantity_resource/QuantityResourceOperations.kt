package gov.nasa.jpl.pyre.general.units.quantity_resource

import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.foundation.resources.discrete.DoubleResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DoubleResourceOperations.decrease
import gov.nasa.jpl.pyre.foundation.resources.discrete.DoubleResourceOperations.increase
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDoubleResource
import gov.nasa.jpl.pyre.foundation.resources.fullyNamed
import gov.nasa.jpl.pyre.foundation.resources.named
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.general.units.StandardUnits
import gov.nasa.jpl.pyre.general.units.UnitAware
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.name
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.times
import gov.nasa.jpl.pyre.general.units.quantity.Quantity
import gov.nasa.jpl.pyre.general.units.unit_aware_resource.UnitAwareResourceOperations.unitAware
import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.ratioOver
import gov.nasa.jpl.pyre.kernel.roundTimes

typealias QuantityResource = UnitAware<DoubleResource>
typealias MutableQuantityResource = UnitAware<MutableDoubleResource>

/**
 * Convenience functions for working with [QuantityResource] and [MutableQuantityResource]
 * Mostly, these are just the functions on [UnitAware], but with [DoubleResourceField] baked into the context for you.
 */
object QuantityResourceOperations {
    context (scope: TaskScope)
    fun MutableQuantityResource.increase(amount: Quantity) {
        unitAware { valueIn(unit).increase(amount.valueIn(unit)) }
    }

    context (scope: TaskScope)
    fun MutableQuantityResource.decrease(amount: Quantity) {
        unitAware { valueIn(unit).decrease(amount.valueIn(unit)) }
    }

    context (scope: TaskScope)
    operator fun MutableQuantityResource.plusAssign(amount: Quantity) = increase(amount)

    context (scope: TaskScope)
    operator fun MutableQuantityResource.minusAssign(amount: Quantity) = decrease(amount)

    // Do the unit-awareness conversions at the resource level, so dimension checking happens only once
    fun DiscreteResource<Duration>.asQuantity(): QuantityResource =
        (map(this) { it ratioOver Duration.SECOND }.fullyNamed { name }) * StandardUnits.SECOND
    fun QuantityResource.asDuration(): DiscreteResource<Duration> = context (DoubleResourceField) {
        map(this.valueIn(StandardUnits.SECOND)) { it roundTimes Duration.SECOND }.fullyNamed { name }
    }
}
