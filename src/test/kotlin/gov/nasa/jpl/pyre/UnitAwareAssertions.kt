package gov.nasa.jpl.pyre

import gov.nasa.jpl.pyre.examples.scheduling.geometry.utils.unitAware
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.times
import gov.nasa.jpl.pyre.general.units.quantity.Quantity
import gov.nasa.jpl.pyre.general.units.quantity.QuantityOperations.abs

object UnitAwareAssertions {
    fun assertEquals(
        expected: Quantity,
        actual: Quantity,
        absoluteTolerance: Quantity = unitAware { abs(expected * 1e-10) }
    ) = unitAware {
        kotlin.test.assertEquals(
            expected.valueIn(expected.unit),
            actual.valueIn(expected.unit),
            absoluteTolerance.valueIn(expected.unit),
            "Expected $expected with absolute tolerance $absoluteTolerance, actual $actual",
        )
    }
}