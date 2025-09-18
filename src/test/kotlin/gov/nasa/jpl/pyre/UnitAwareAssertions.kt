package gov.nasa.jpl.pyre

import gov.nasa.jpl.pyre.flame.units.Quantity
import gov.nasa.jpl.pyre.flame.units.QuantityOperations.abs
import gov.nasa.jpl.pyre.flame.units.QuantityOperations.times
import gov.nasa.jpl.pyre.flame.units.QuantityOperations.valueIn

object UnitAwareAssertions {
    fun assertEquals(
        expected: Quantity,
        actual: Quantity,
        absoluteTolerance: Quantity = abs(expected * 1e-10)
    ) = kotlin.test.assertEquals(
        expected.valueIn(expected.unit),
        actual.valueIn(expected.unit),
        absoluteTolerance.valueIn(expected.unit),
        "Expected $expected with absolute tolerance $absoluteTolerance, actual $actual",
    )
}