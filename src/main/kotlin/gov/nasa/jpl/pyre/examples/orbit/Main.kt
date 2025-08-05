package gov.nasa.jpl.pyre.examples.orbit

import gov.nasa.jpl.pyre.coals.Closeable.Companion.closesWith
import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.coals.andThen
import gov.nasa.jpl.pyre.ember.Serialization.alias
import gov.nasa.jpl.pyre.examples.orbit.OrbitalSimulation.Vector
import gov.nasa.jpl.pyre.flame.plans.activitySerializersModule
import gov.nasa.jpl.pyre.flame.plans.runStandardPlanSimulation
import gov.nasa.jpl.pyre.flame.reporting.CsvReportHandler
import gov.nasa.jpl.pyre.flame.reporting.ReportHandling.assumeType
import gov.nasa.jpl.pyre.flame.reporting.ReportHandling.channels
import gov.nasa.jpl.pyre.flame.reporting.ReportHandling.reportAllTo
import gov.nasa.jpl.pyre.flame.reporting.ReportHandling.split
import gov.nasa.jpl.pyre.spark.resources.discrete.Discrete
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteMonad.map
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlin.time.Instant

// This is a simple setup, using mostly default choices for how to run a simulation.
// Most importantly, it chooses to read and write files for incon, plan, outputs, and fincon,
// and to use the default CSV event output format.
fun simpleMain(args: Array<String>) {
    runStandardPlanSimulation(
        args[0],
        ::EarthOrbit,
        Json {
            serializersModule = SerializersModule {
                contextual(
                    Instant::class, String.serializer().alias<String, Instant>(
                        InvertibleFunction.of(Instant::parse, Instant::toString)
                    )
                )
                include(activitySerializersModule<EarthOrbit> { })
            }
        }
    )
}

// This is a slightly more advanced setup.
// Here, we're accepting the same input files, but we're configuring a more condensed CSV output format.
fun csvMain(args: Array<String>) {
    val jsonFormat = Json {
        serializersModule = SerializersModule {
            contextual(
                Instant::class, String.serializer().alias<String, Instant>(
                    InvertibleFunction.of(Instant::parse, Instant::toString)
                )
            )
            include(activitySerializersModule<EarthOrbit> { })
        }
    }
    runStandardPlanSimulation(
        args[0],
        ::EarthOrbit,
        jsonFormat,
        buildReportHandler = { outputStream ->
            // val output = jsonlReportHandler(outputStream, jsonFormat)
            // val output = CSVReportHandler(outputStream, jsonFormat)
            val output = CsvReportHandler(outputStream, jsonFormat)
            val vectorHandler = assumeType<Discrete<Vector>>() andThen split(
                    map(Vector::x) to { "$it.x" },
                    map(Vector::y) to { "$it.y" },
                    map(Vector::z) to { "$it.z" },
                ) andThen reportAllTo(output)
            channels(
                "/earth_position" to vectorHandler,
                "/moon_position" to vectorHandler,
            )
                // .closesWith(output::close)
                .closesWith {}
        }
    )
}
