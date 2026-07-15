package gov.nasa.jpl.parakeet.general.testing

import gov.nasa.jpl.parakeet.foundation.Simulator
import gov.nasa.jpl.parakeet.foundation.tasks.InitScope
import gov.nasa.jpl.parakeet.foundation.tasks.InitScope.Companion.spawn
import gov.nasa.jpl.parakeet.foundation.tasks.TaskScope
import gov.nasa.jpl.parakeet.foundation.tasks.task
import gov.nasa.jpl.parakeet.general.results.MutableSimulationResults
import gov.nasa.jpl.parakeet.general.results.SimulationResults
import gov.nasa.jpl.parakeet.general.results.SimulationResultsOperations.reportHandler
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
    fun <M : Any> runUnitTest(
        simulationStart: Instant,
        constructModel: context (InitScope) () -> M,
        testTask: suspend context (TaskScope) (M) -> Unit
    ): SimulationResults {
        val results = MutableSimulationResults(simulationStart, simulationStart, )

        // Leak a test variable out of the simulation as a shortcut to report when the simulation is finished.
        var testTaskComplete = false
        val simulation = Simulator(results.reportHandler(), simulationStart) {
            // Build the model and add a task to run the test code.
            constructModel().also {
                spawn("Test Task", task {
                    try {
                        testTask(it)
                    } catch (e: AssertionError) {
                        throw e
                    } catch (e: Throwable) {
                        throw AssertionError("Unexpected exception during test task", e)
                    }
                    testTaskComplete = true
                })
            }
        }

        while (!testTaskComplete) simulation.stepTo(Instant.DISTANT_FUTURE)
        return results
    }
}