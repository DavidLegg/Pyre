package gov.nasa.jpl.pyre.examples.orbit

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.JsonValue.JsonString
import gov.nasa.jpl.pyre.flame.plans.PlanSimulation
import kotlin.time.Instant

fun main(args: Array<String>) {
    val endTime = Duration.serializer().deserialize(JsonString(args[0]))

    val simulation = PlanSimulation(
        reportHandler = { println(it) },
        simulationStart = Instant.parse("2020-01-01T00:00:00Z"),
        simulationEpoch = Instant.parse("2020-01-01T00:00:00Z"),
        constructModel = ::EarthOrbit,
    )

    simulation.runUntil(endTime)
}
