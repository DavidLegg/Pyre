package gov.nasa.jpl.pyre.examples.orbit

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.coals.andThen
import gov.nasa.jpl.pyre.ember.JsonConditions
import gov.nasa.jpl.pyre.ember.JsonConditions.Companion.toFile
import gov.nasa.jpl.pyre.ember.Serialization.alias
import gov.nasa.jpl.pyre.examples.orbit.OrbitalSimulation.Vector
import gov.nasa.jpl.pyre.flame.plans.PlanSimulation
import gov.nasa.jpl.pyre.flame.plans.activitySerializersModule
import gov.nasa.jpl.pyre.flame.plans.runStandardPlanSimulation
import gov.nasa.jpl.pyre.flame.reporting.CSVReportHandler
import gov.nasa.jpl.pyre.flame.reporting.ParallelReportHandler.Companion.inParallel
import gov.nasa.jpl.pyre.flame.reporting.ReportHandling.channelHandler
import gov.nasa.jpl.pyre.flame.reporting.ReportHandling.channels
import gov.nasa.jpl.pyre.flame.reporting.ReportHandling.reportAllTo
import gov.nasa.jpl.pyre.flame.reporting.ReportHandling.split
import gov.nasa.jpl.pyre.spark.resources.discrete.Discrete
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteMonad.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.time.Instant

// This is a simple setup, using mostly default choices for how to run a simulation.
// Most importantly, it chooses to read and write files for incon, plan, outputs, and fincon.
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

@OptIn(ExperimentalSerializationApi::class)
fun main(args: Array<String>) {
    val jsonFormat = Json {
        serializersModule = SerializersModule {
            contextual(Instant::class, String.serializer().alias(
                InvertibleFunction.of(Instant::parse, Instant::toString)
            ))
            include(activitySerializersModule<EarthOrbit> { })
        }
    }

    // Parse CL args
    val remainingArgs = args.toMutableList()
    var endTime: Instant? = null
    var inconFile: Path? = null
    var finconFile: Path? = null
    var outputStream: OutputStream = System.out

    var println: (Any?) -> Unit = { /* don't print messages if dumping output to stdout */ }

    while (remainingArgs.isNotEmpty()) {
        val it = remainingArgs.removeFirst()
        when (it) {
            "-f", "--fincon" -> finconFile = Path(remainingArgs.removeFirst())
            "-i", "--incon" -> inconFile = Path(remainingArgs.removeFirst())
            "-e", "--end" -> endTime = Instant.parse(remainingArgs.removeFirst())
            "-o", "--out" -> {
                outputStream = FileOutputStream(remainingArgs.removeFirst())
                // Since we're dumping output to a file, we can safely print messages to stdout
                println = { kotlin.io.println(it) }
            }
            "-h", "--help" -> {
                println("Options:")
                println("  -e, --end       End time for simulation (REQUIRED)")
                println("  -i, --incon     Read initial conditions file")
                println("  -f, --fincon    Write final conditions file")
                println("  -o, --out       Write output to this file (default: stdout)")
                println("  -h, --help      Write this help message and quit")
                return
            }
            else -> throw IllegalArgumentException("Unrecognized argument $it")
        }
    }
    requireNotNull(endTime) { "End time (-e) is required" }
    finconFile = finconFile?.resolve("EarthOrbit-${endTime}.json")

    // Basic JSON output:
    /*
    outputStream.use { outputStream ->
        val outputHandler = streamReportHandler(outputStream, jsonFormat)

        val simulation = if (inconFile == null) {
            println("Starting without incon file")
            val epoch = Instant.parse("2020-01-01T00:00:00Z")
            PlanSimulation.withoutIncon(outputHandler, epoch, epoch, ::EarthOrbit)
        } else {
            println("Starting from initial conditions file $inconFile")
            val incon = JsonConditions.fromFile(inconFile, jsonFormat)
            PlanSimulation.withIncon(outputHandler, incon, ::EarthOrbit)
        }

        simulation.runUntil(endTime)

        if (finconFile != null) {
            println("Writing final conditions file $finconFile")
            JsonConditions(jsonFormat)
                .also(simulation::save)
                .toFile(finconFile)
        }
    }
     */

    // Filtered CSV output, run in the background:
    outputStream.use { outputStream ->
        CSVReportHandler(
            listOf(
                "earth_position.x",
                "earth_position.y",
                "earth_position.z",
                "moon_position.x",
                "moon_position.y",
                "moon_position.z",
            ),
            outputStream,
            jsonFormat,
        ).use { csvHandler ->
            val vectorHandler = channelHandler<Discrete<Vector>>(
                split(
                    map(Vector::x) to { "$it.x" },
                    map(Vector::y) to { "$it.y" },
                    map(Vector::z) to { "$it.z" },
                ) andThen reportAllTo(csvHandler)
            )
            val outputHandler = channels(
                "earth_position" to vectorHandler,
                "moon_position" to vectorHandler,
            )

            runBlocking {
                outputHandler.inParallel { bgOutputHandler ->
                    val simulation = if (inconFile == null) {
                        println("Starting without incon file")
                        val epoch = Instant.parse("2020-01-01T00:00:00Z")
                        PlanSimulation.withoutIncon(bgOutputHandler, epoch, epoch, ::EarthOrbit)
                    } else {
                        println("Starting from initial conditions file $inconFile")
                        val incon = JsonConditions.fromFile(inconFile, jsonFormat)
                        PlanSimulation.withIncon(bgOutputHandler, incon, ::EarthOrbit)
                    }

                    simulation.runUntil(endTime)

                    if (finconFile != null) {
                        println("Writing final conditions file $finconFile")
                        JsonConditions(jsonFormat)
                            .also(simulation::save)
                            .toFile(finconFile)
                    }
                }
            }
        }
    }
}
