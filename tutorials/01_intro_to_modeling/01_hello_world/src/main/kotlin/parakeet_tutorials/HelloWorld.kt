package parakeet_tutorials

import gov.nasa.jpl.parakeet.foundation.Simulator
import gov.nasa.jpl.parakeet.foundation.tasks.ReportScope.Companion.report
import gov.nasa.jpl.parakeet.foundation.tasks.SimulationScope.Companion.stdout
import gov.nasa.jpl.parakeet.general.results.MutableSimulationResults
import gov.nasa.jpl.parakeet.general.results.SimulationResultsOperations.reportHandler
import kotlin.time.Instant
import parakeet_tutorials.util.Output.dump
import kotlin.time.Duration.Companion.days

fun main() {
    println("Building simulator...")

    // To define a simulation, we first need to define what to do with the results.
    // The simplest thing to do with simulation results is to just keep them in memory.
    // We can do that by creating a MutableSimulationResults to hold those results.
    val start = Instant.parse("2030-01-01T00:00:00Z")
    val end = start + 1.days
    val results = MutableSimulationResults(start, end)

    // Now we create the simulator
    val simulator = Simulator(
        // this report handler which will direct output from the simulator into the results object
        reportHandler = results.reportHandler(),
        startTime = start,
    ) {
        // This block is the "initialization" phase of the model.
        // We'll do more in this block in later tutorials.
        // For now, we'll directly report a message to the simulation's stdout.
        stdout.report("Hello, world!")
    }

    // Next, we'll run the simulator to the end time we chose above.
    // In this tutorial, nothing much will happen, since our output was written during initialization above.
    println("Running simulator...")
    simulator.runUntil(end)

    // Finally, we can dump the results.
    println("Reading results...")
    results.dump()

    // Notice that the "Hello, world!" message was written during the results dump, not during initialization.
    // That's because it was written to the simulation's stdout, which is captured as part of the simulation results.
    // This is completely separate from the program's stdout.
    // When we want to print a message from the simulation, we should prefer to write to the simulation stdout,
    // so that the message is captured as part of the simulation results, and kept alongside other simulation results.
    // This is especially important when we're using checkpoints or incremental simulation, as those features
    // can re-run our model code many times and need to suppress some output.
    // Those features will be covered in a future tutorial though.
}
