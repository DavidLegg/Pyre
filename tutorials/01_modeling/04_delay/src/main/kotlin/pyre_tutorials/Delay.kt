package pyre_tutorials

import gov.nasa.jpl.pyre.foundation.Simulator
import gov.nasa.jpl.pyre.foundation.reporting.Reporting.registered
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.IntResourceOperations.increment
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableIntResource
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.spawn
import gov.nasa.jpl.pyre.foundation.tasks.TaskOperations.delay
import gov.nasa.jpl.pyre.foundation.tasks.TaskOperations.delayUntil
import gov.nasa.jpl.pyre.foundation.tasks.task
import gov.nasa.jpl.pyre.general.results.MutableSimulationResults
import gov.nasa.jpl.pyre.general.results.SimulationResultsOperations.reportHandler
import kotlin.time.Instant
import pyre_tutorials.util.Output.dump
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

fun main() {
    val start = Instant.parse("2030-01-01T00:00:00Z")
    val end = start + 1.days
    val results = MutableSimulationResults(start, end)
    val simulator = Simulator(
        reportHandler = results.reportHandler(),
        startTime = start,
    ) {
        val counter: MutableIntResource = discreteResource("counter", 0).registered()

        spawn("Increment Counter",  task {
            // Within a task, we can wait for time to pass.
            // One of the simplest versions of this is "delay", which simply waits for a certain fixed duration.
            delay(1.hours)
            counter.increment()
        })

        // Out here, we're back in the initialization block - time here is at the start of the simulation, and can't advance.

        spawn("Increment Counter Again", task {
            // Another version of waiting is "delayUntil", which waits until a certain absolute time.
            delayUntil(start + 3.hours)
            counter.increment()
        })
    }

    simulator.runUntil(end)
    results.dump()
}
