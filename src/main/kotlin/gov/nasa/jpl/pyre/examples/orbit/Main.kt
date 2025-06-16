package gov.nasa.jpl.pyre.examples.orbit

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.JsonValue.JsonString
import gov.nasa.jpl.pyre.flame.plans.PlanSimulation
import gov.nasa.jpl.pyre.flame.plans.PlanSimulation.PlanSimulationSetup

fun main(args: Array<String>) {
    val endTime = Duration.serializer().deserialize(JsonString(args[0]))

    val simulation = PlanSimulation(
        PlanSimulationSetup(
            { println(it) },
            null,
            ::EarthOrbit
        )
    )

    simulation.runUntil(endTime)
}
