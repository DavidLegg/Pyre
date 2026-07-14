package parakeet_tutorials

import gov.nasa.jpl.parakeet.examples.scheduling.GroundedActivity
import gov.nasa.jpl.parakeet.foundation.Simulator
import gov.nasa.jpl.parakeet.foundation.plans.GroundedActivity
import gov.nasa.jpl.parakeet.foundation.plans.Plan
import gov.nasa.jpl.parakeet.general.results.MutableSimulationResults
import gov.nasa.jpl.parakeet.general.results.SimulationResultsOperations.reportHandler
import parakeet_tutorials.util.Output.dump
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

fun main() {
    val start = Instant.parse("2030-01-01T00:00:00Z")
    val end = start + 1.days
    val results = MutableSimulationResults(start, end)
    val simulator = Simulator(
        reportHandler = results.reportHandler(),
        startTime = start,
        constructModel = ::CommSystem
    )

    val plan = Plan(
        start,
        end,
        listOf(
            GroundedActivity(start + 1.hours, DownlinkFiles("text", 5, 2.seconds)),
            GroundedActivity(start + 2.hours, DownlinkFileGroups(listOf("image", "science data", "engineering data"), 1.seconds, 5, 3.seconds)),
        )
    )
    simulator.runPlan(plan)
    results.dump()
}
