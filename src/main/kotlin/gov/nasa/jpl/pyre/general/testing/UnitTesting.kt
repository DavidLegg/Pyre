package gov.nasa.jpl.pyre.general.testing

import gov.nasa.jpl.pyre.foundation.plans.PlanSimulation
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.spawn
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.foundation.tasks.task
import gov.nasa.jpl.pyre.general.results.MutableSimulationResults
import gov.nasa.jpl.pyre.general.results.SimulationResults
import gov.nasa.jpl.pyre.general.results.SimulationResultsOperations.reportHandler
import gov.nasa.jpl.pyre.general.results.SimulationResultsOperations.toSimulationResults
import kotlin.also
import kotlin.time.Instant

object UnitTesting {
    /**
     * Run a simulation as a unit test.
     * Test ends when [testTask] completes.
     *
     * Activity spans and resource reports are collected in memory.
     *
     * [testTask] may spawn activities to simulate a plan, or produce effects directly.
     * Assertions may be made directly by [testTask] during simulation, or afterward on the results.
     */
    inline fun <reified M> runUnitTest(
        simulationStart: Instant,
        noinline constructModel: context (InitScope) () -> M,
        noinline testTask: suspend context (TaskScope) (M) -> Unit
    ): SimulationResults {
        val results = MutableSimulationResults(simulationStart, simulationStart, )

        // Leak a test variable out of the simulation as a shortcut to report when the simulation is finished.
        var testTaskComplete = false
        val simulation = PlanSimulation(results.reportHandler(), simulationStart) {
            // Build the model and add a task to run the test code.
            constructModel().also {
                spawn("Test Task", task {
                    testTask(it)
                    testTaskComplete = true
                })
            }
        }

        while (!testTaskComplete) simulation.stepTo(Instant.DISTANT_FUTURE)
        return results.toSimulationResults()
    }
}