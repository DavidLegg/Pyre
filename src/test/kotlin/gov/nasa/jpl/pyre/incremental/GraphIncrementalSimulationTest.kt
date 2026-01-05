package gov.nasa.jpl.pyre.incremental

import gov.nasa.jpl.pyre.kernel.BasicInitScope
import gov.nasa.jpl.pyre.kernel.Duration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertDoesNotThrow

class GraphIncrementalSimulationTest {
    private class IncrementalSimulationTester(
        endTime: Duration,
        initializeModel: context (BasicInitScope) () -> Unit,
    ) {
        private val baselineSimulator = NonIncrementalKernelSimulation(endTime, initializeModel)
        private val testSimulator = GraphIncrementalSimulation()

        fun run(planEdits: KernelPlanEdits) {
            baselineSimulator.run(planEdits)
            assertDoesNotThrow { testSimulator.run(planEdits) }
            assertEquals(baselineSimulator.plan, testSimulator.plan)
            assertEquals(baselineSimulator.reports, testSimulator.reports)
        }
    }
}