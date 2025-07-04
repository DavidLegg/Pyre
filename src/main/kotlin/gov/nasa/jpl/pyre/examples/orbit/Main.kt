package gov.nasa.jpl.pyre.examples.orbit

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.flame.plans.GroundedActivity
import gov.nasa.jpl.pyre.flame.plans.PlanSimulation
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer
import kotlin.time.Instant

fun main(args: Array<String>) {
    val endTime: Duration = Json.decodeFromString(args[0])

    val activitySerializersModule = SerializersModule {
        polymorphic(GroundedActivity::class) {
        }
    }
    val simulation = PlanSimulation(
        reportHandler = { println(it) },
        simulationStart = Instant.parse("2020-01-01T00:00:00Z"),
        simulationEpoch = Instant.parse("2020-01-01T00:00:00Z"),
        constructModel = ::EarthOrbit,
        activitySerializer = activitySerializersModule.serializer(),
    )

    simulation.runUntil(endTime)
}
