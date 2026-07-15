package parakeet_tutorials

import gov.nasa.jpl.parakeet.foundation.Simulator
import gov.nasa.jpl.parakeet.foundation.reporting.Reporting.registered
import gov.nasa.jpl.parakeet.foundation.resources.discrete.BooleanResource
import gov.nasa.jpl.parakeet.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.parakeet.foundation.resources.discrete.DiscreteResourceOperations.greaterThan
import gov.nasa.jpl.parakeet.foundation.resources.discrete.MutableIntResource
import gov.nasa.jpl.parakeet.foundation.resources.named
import gov.nasa.jpl.parakeet.general.results.MutableSimulationResults
import gov.nasa.jpl.parakeet.general.results.SimulationResultsOperations.reportHandler
import kotlin.time.Instant
import parakeet_tutorials.util.Output.dump
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

        // Besides mutable resources, which hold our state, we can also define "derived" resources.
        // These are functions of other resources. Like all resources, they describe some aspect of the model.
        // Unlike mutable resources, which can be changed independently of one another,
        // derived resources change as a consequence of changing the resources they're derived from.
        // Just like mutable resources, we can register derived resources, and they will be reported in the output.
        // Unlike mutable resources, derived resources don't have a name.
        // Instead, we give it a name explicitly before we register it.
        val counterIsLarge: BooleanResource = (counter greaterThan 5).named { "counterIsLarge" }.registered()
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
