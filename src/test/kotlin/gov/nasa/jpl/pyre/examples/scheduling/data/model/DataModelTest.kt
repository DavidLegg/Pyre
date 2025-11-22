package gov.nasa.jpl.pyre.examples.scheduling.data.model

import gov.nasa.jpl.pyre.UnitAwareAssertions.assertEquals
import gov.nasa.jpl.pyre.kernel.Duration.Companion.SECOND
import gov.nasa.jpl.pyre.kernel.times
import gov.nasa.jpl.pyre.general.resources.discrete.unit_aware.MutableQuantityResource
import gov.nasa.jpl.pyre.general.resources.discrete.unit_aware.QuantityResourceOperations.getValue
import gov.nasa.jpl.pyre.general.resources.discrete.unit_aware.QuantityResourceOperations.quantityResource
import gov.nasa.jpl.pyre.general.resources.discrete.unit_aware.QuantityResourceOperations.set
import gov.nasa.jpl.pyre.general.resources.polynomial.unit_aware.PolynomialQuantityResourceOperations.getValue
import gov.nasa.jpl.pyre.general.testing.UnitTesting.runUnitTest
import gov.nasa.jpl.pyre.general.units.StandardUnits
import gov.nasa.jpl.pyre.general.units.StandardUnits.BIT
import gov.nasa.jpl.pyre.general.units.StandardUnits.GIGABYTE
import gov.nasa.jpl.pyre.general.units.StandardUnits.MEGABYTE
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.div
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.times
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskOperations.delay
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class DataModelTest {
    // This is a general pattern for testing subsystem models:
    // - The subsystem model should specify all its inputs as resources (not other models!)
    // - A "stub" model is created, which defines all those inputs as mutable resources.
    // - The test task can then manipulate those inputs to set up various test scenarios.
    // This fully decouples the tests of this subsystem from the rest of the system.
    private class TestModel(context: InitScope) {
        val dataRate: MutableQuantityResource
        val downlinkRate: MutableQuantityResource
        val model: DataModel

        init {
            with (context) {
                dataRate = quantityResource("data_rate", 0.0 * BITS_PER_SECOND)
                downlinkRate = quantityResource("downlink_rate", 0.0 * BITS_PER_SECOND)
                model = DataModel(this,
                    DataModel.Config(32.0 * GIGABYTE),
                    DataModel.Inputs(dataRate, downlinkRate))
            }
        }
    }

    private fun runUnitTest(testTask: suspend context(TaskScope) TestModel.() -> Unit) {
        // Specialize the general unit test format with some constants and a default config
        runUnitTest(
            Instant.parse("2020-01-01T00:00:00Z"),
            { TestModel(this) },
            testTask
        )
    }

    @Test
    fun testCollectingData() {
        runUnitTest {
            // Assert initial state
            assertEquals(0.0 * BITS_PER_SECOND, model.netDataRate.getValue())
            assertEquals(0.0 * BIT, model.storedData.getValue())

            // Start collecting some data
            dataRate.set(25.0 * BITS_PER_SECOND)
            // Wait some time to collect that data
            delay(10 * SECOND)
            // Check that the appropriate data was collected:
            assertEquals(25.0 * BITS_PER_SECOND, model.netDataRate.getValue())
            assertEquals(250.0 * BIT, model.storedData.getValue())

            // Stop collecting data
            dataRate.set(0.0 * BITS_PER_SECOND)
            // Wait a while to make sure we don't collect anything
            delay(10 * SECOND)
            // Check that the appropriate is still there.
            assertEquals(0.0 * BITS_PER_SECOND, model.netDataRate.getValue())
            assertEquals(250.0 * BIT, model.storedData.getValue())
        }
    }

    @Test
    fun testDownlinkingData() {
        runUnitTest {
            // Assert initial state
            assertEquals(0.0 * BITS_PER_SECOND, model.netDataRate.getValue())
            assertEquals(0.0 * BIT, model.storedData.getValue())
            assertEquals(0.0 * BIT, model.dataDownlinked.getValue())

            // Start collecting data fairly rapidly
            dataRate.set(10.0 * MEGABYTE / StandardUnits.SECOND)
            // Wait for the storage to fill up a bit
            delay(10 * SECOND)
            // Check that the appropriate data was collected:
            assertEquals(10.0 * MEGABYTE / StandardUnits.SECOND, model.netDataRate.getValue())
            assertEquals(100.0 * MEGABYTE, model.storedData.getValue())
            assertEquals(0.0 * BIT, model.dataDownlinked.getValue())

            // Switch from collecting data to downlinking data:
            dataRate.set(0.0 * MEGABYTE / StandardUnits.SECOND)
            downlinkRate.set(10.0 * MEGABYTE / StandardUnits.SECOND)
            // Wait for the storage to drain a bit
            delay(5 * SECOND)
            // Check that the appropriate data was downlinked:
            assertEquals(-10.0 * MEGABYTE / StandardUnits.SECOND, model.netDataRate.getValue())
            assertEquals(50.0 * MEGABYTE, model.storedData.getValue())
            assertEquals(50.0 * MEGABYTE, model.dataDownlinked.getValue())
        }
    }

    @Test
    fun testOverflow() {
        runUnitTest {
            // Assert initial state
            assertEquals(0.0 * BITS_PER_SECOND, model.netDataRate.getValue())
            assertEquals(0.0 * BIT, model.storedData.getValue())
            assertEquals(0.0 * BIT, model.dataDownlinked.getValue())
            assertEquals(0.0 * BIT, model.dataLost.getValue())

            // Start collecting data fairly rapidly
            dataRate.set(100.0 * MEGABYTE / StandardUnits.SECOND)
            // Wait for the storage to fill up completely
            delay(320 * SECOND)
            // Check that storage is full, but no data has been lost yet
            assertEquals(100.0 * MEGABYTE / StandardUnits.SECOND, model.netDataRate.getValue())
            assertEquals(32.0 * GIGABYTE, model.storedData.getValue())
            assertEquals(0.0 * BIT, model.dataDownlinked.getValue())
            assertEquals(0.0 * BIT, model.dataLost.getValue())

            // Wait a bit longer, letting the data overflow
            delay(10 * SECOND)
            // Check that no more data was stored, but some has overflowed
            assertEquals(100.0 * MEGABYTE / StandardUnits.SECOND, model.netDataRate.getValue())
            assertEquals(32.0 * GIGABYTE, model.storedData.getValue())
            assertEquals(0.0 * BIT, model.dataDownlinked.getValue())
            assertEquals(1.0 * GIGABYTE, model.dataLost.getValue())

            // Start downlinking some, but not all, the data we're producing
            downlinkRate.set(50.0 * MEGABYTE / StandardUnits.SECOND)
            // Wait a bit, downlinking and overflowing at the same time
            delay(10 * SECOND)
            // Check that no more data was stored, but some has overflowed and some has downlinked
            assertEquals(50.0 * MEGABYTE / StandardUnits.SECOND, model.netDataRate.getValue())
            assertEquals(32.0 * GIGABYTE, model.storedData.getValue())
            assertEquals(0.5 * GIGABYTE, model.dataDownlinked.getValue())
            assertEquals(1.5 * GIGABYTE, model.dataLost.getValue())

            // Now downlink more than we're storing, so storage starts to drain
            dataRate.set(50.0 * MEGABYTE / StandardUnits.SECOND)
            downlinkRate.set(100.0 * MEGABYTE / StandardUnits.SECOND)
            // Wait a bit, downlinking and draining the stored data
            delay(10 * SECOND)
            // Check that some of the stored data is downlinking, and no more is overflowing
            assertEquals(-50.0 * MEGABYTE / StandardUnits.SECOND, model.netDataRate.getValue())
            assertEquals(31.5 * GIGABYTE, model.storedData.getValue())
            assertEquals(1.5 * GIGABYTE, model.dataDownlinked.getValue())
            assertEquals(1.5 * GIGABYTE, model.dataLost.getValue())

            // Shut off data production
            dataRate.set(0.0 * MEGABYTE / StandardUnits.SECOND)
            // Wait for storage to drain
            delay(315 * SECOND)
            // Check that all the data was downlinked
            assertEquals(-100.0 * MEGABYTE / StandardUnits.SECOND, model.netDataRate.getValue())
            assertEquals(0.0 * GIGABYTE, model.storedData.getValue())
            assertEquals(33.0 * GIGABYTE, model.dataDownlinked.getValue())
            assertEquals(1.5 * GIGABYTE, model.dataLost.getValue())

            // Wait a while longer to ensure no additional data is downlinked
            delay(10 * SECOND)
            // Check that no additional data was downlinked
            assertEquals(-100.0 * MEGABYTE / StandardUnits.SECOND, model.netDataRate.getValue())
            assertEquals(0.0 * GIGABYTE, model.storedData.getValue())
            assertEquals(33.0 * GIGABYTE, model.dataDownlinked.getValue())
            assertEquals(1.5 * GIGABYTE, model.dataLost.getValue())

            // Turn on data again, but less than we're downlinking
            dataRate.set(50.0 * MEGABYTE / StandardUnits.SECOND)
            // Wait a little while to downlink some data
            delay(10 * SECOND)
            // Check that all the data produced was downlinked
            assertEquals(-50.0 * MEGABYTE / StandardUnits.SECOND, model.netDataRate.getValue())
            assertEquals(0.0 * GIGABYTE, model.storedData.getValue())
            assertEquals(33.5 * GIGABYTE, model.dataDownlinked.getValue())
            assertEquals(1.5 * GIGABYTE, model.dataLost.getValue())
        }
    }
}