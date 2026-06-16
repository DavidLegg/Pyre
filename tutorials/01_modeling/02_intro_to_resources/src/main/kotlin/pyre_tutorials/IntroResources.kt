package pyre_tutorials

import gov.nasa.jpl.pyre.foundation.Simulator
import gov.nasa.jpl.pyre.foundation.reporting.Reporting.registered
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableIntResource
import gov.nasa.jpl.pyre.general.results.MutableSimulationResults
import gov.nasa.jpl.pyre.general.results.SimulationResultsOperations.reportHandler
import kotlin.time.Instant
import pyre_tutorials.util.Output.dump
import kotlin.time.Duration.Companion.days

fun main() {
    println("Building simulator...")
    val start = Instant.parse("2030-01-01T00:00:00Z")
    val end = start + 1.days
    val results = MutableSimulationResults(start, end)
    val simulator = Simulator(
        reportHandler = results.reportHandler(),
        startTime = start,
    ) {
        // Let's build our first resource. This will be a simple integer counter.
        // When we build it, we'll also register it, so the value is reported in our output.
        val counter: MutableIntResource = discreteResource("counter", 0).registered()
        // Notice that the type of counter is MutableIntResource, but we construct it with a function that doesn't mention ints.
        // This is because MutableIntResource is a type alias for MutableDiscreteResource<Int>.
        // In general, we can construct any MutableDiscreteResource with the discreteResource function.
        // The type of the initial value determines the type of the resource.
        // We could also use MutableDiscreteResource<Int> instead of MutableIntResource for the type of counter, they're equivalent.
    }

    println("Running simulator...")
    simulator.runUntil(end)

    // Finally, we can dump the results.
    println("Reading results...")
    results.dump()

    // Notice that now our output includes a new section labeled "counter".
    // This happened because we registered a resource named "counter".
    // When we did this, we created a new output channel, also called "counter".
    // Most of the output channels we create will be created by registering a resource like this.
    // Some exceptions to this rule are "stdout" and "stderr", which are created for us in every simulation.
    // Every channel has a type - stdout and stderr are string channels, so each value on them must be a string.
    // The "counter" channel above is an integer channel, so each value must be an integer.
    // Channels can be any type, not just primitives.
}
