package gov.nasa.jpl.pyre.examples.orbit

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.Serialization.alias
import gov.nasa.jpl.pyre.flame.plans.PlanSimulation
import gov.nasa.jpl.pyre.flame.reporting.StreamReportHandler
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlin.time.Instant

@OptIn(ExperimentalSerializationApi::class)
fun main(args: Array<String>) {
    val jsonFormat = Json {
        serializersModule = SerializersModule {
            contextual(Instant::class, String.serializer().alias(
                InvertibleFunction.of(Instant::parse, Instant::toString)
            ))
        }
    }

    val endTime: Instant = Instant.parse(args[0])

    val simulation = PlanSimulation.withoutIncon(
        reportHandler = StreamReportHandler(jsonFormat=jsonFormat),
        simulationStart = Instant.parse("2020-01-01T00:00:00Z"),
        simulationEpoch = Instant.parse("2020-01-01T00:00:00Z"),
        constructModel = ::EarthOrbit,
    )

    simulation.runUntil(endTime)
}
