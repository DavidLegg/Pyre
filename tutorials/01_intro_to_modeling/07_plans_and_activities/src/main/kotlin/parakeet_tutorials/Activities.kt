package parakeet_tutorials

import gov.nasa.jpl.pyre.examples.scheduling.GroundedActivity
import gov.nasa.jpl.pyre.foundation.Simulator
import gov.nasa.jpl.pyre.foundation.plans.Plan
import gov.nasa.jpl.pyre.general.results.MutableSimulationResults
import gov.nasa.jpl.pyre.general.results.SimulationResultsOperations.reportHandler
import parakeet_tutorials.util.Output.dump
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

fun main() {
    val start = Instant.parse("2030-01-01T00:00:00Z")
    val end = start + 1.days
    val results = MutableSimulationResults(start, end)
    val simulator = Simulator(
        reportHandler = results.reportHandler(),
        startTime = start,
        constructModel = ::Heater
    )

    val plan = Plan(
        start,
        end,
        listOf(
            GroundedActivity(start + 1.hours, TurnHeaterOn, "A"),
            GroundedActivity(start + 2.hours, TurnHeaterOff(5.minutes), "B"),
        )
    )
    simulator.runPlan(plan)
    results.dump()
}
