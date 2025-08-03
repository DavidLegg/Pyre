package gov.nasa.jpl.pyre.flame.plans

import gov.nasa.jpl.pyre.coals.Closeable
import gov.nasa.jpl.pyre.coals.Closeable.Companion.closesWith
import gov.nasa.jpl.pyre.coals.Closeable.Companion.use
import gov.nasa.jpl.pyre.ember.JsonConditions
import gov.nasa.jpl.pyre.ember.JsonConditions.Companion.toFile
import gov.nasa.jpl.pyre.ember.ReportHandler
import gov.nasa.jpl.pyre.flame.reporting.ParallelReportHandler.Companion.inParallel
import gov.nasa.jpl.pyre.flame.reporting.ReportHandling.jsonlReportHandler
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.OutputStream
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

@Serializable
data class StandardPlanSimulationSetup<M>(
    @SerialName("initial_conditions")
    val inconFile: String? = null,
    @SerialName("plan")
    val planFile: String,
    @SerialName("final_conditions")
    val finconFile: String? = null,
    @SerialName("output")
    val outputFile: String? = null,
)

/**
 * Baseline way to set up and run a [PlanSimulation].
 *
 * A JSON setup file is read from disk.
 * The setup file indicates a plan and (optionally) an incon, as paths relative to the location of the setup file.
 * The setup file also optionally indicates an output file and fincon file, also as paths relative to the location of the setup file.
 * If no output file is requested, output is written to stdout.
 *
 * Missions looking to deviate from this baseline should copy this function and modify it to suit their needs.
 *
 * @param setupFile
 * The path of the [StandardPlanSimulationSetup] file, relative to the working directory.
 *
 * @param constructModel
 * Model constructor, usually the constructor method of a top-level model class.
 *
 * @param jsonFormat
 * The [Json] format to use everywhere, including plan deserialization, reports, and incon/fincon handling.
 *
 * @param buildReportHandler
 * Given the output stream to write to, constructs a [ReportHandler] and gives it to the callback.
 * Defaults to using [jsonlReportHandler], writing the output in JSON Lines format.
 * The callback pattern permits [AutoCloseable] report handlers, which may call the callback inside [AutoCloseable.use].
 */
@OptIn(ExperimentalSerializationApi::class)
inline fun <reified M> runStandardPlanSimulation(
    setupFile: String,
    noinline constructModel: SparkInitContext.() -> M,
    jsonFormat: Json = Json,
    buildReportHandler: (OutputStream) -> Closeable<ReportHandler> =
        { jsonlReportHandler(it, jsonFormat).closesWith {} },
) {
    val setupPath = Path(setupFile).absolute()
    val setup = setupPath.inputStream().use {
        jsonFormat.decodeFromStream<StandardPlanSimulationSetup<M>>(it)
    }
    // Set up output to go to the output file if specified, or stdout if not.
    val println: (String) -> Unit
    val outputStream: OutputStream
    if (setup.outputFile == null) {
        println = {}
        outputStream = System.out
    } else {
        println = { kotlin.io.println(it) }
        println("Read setup file $setupPath")
        val outputPath = setupPath.resolveSibling(setup.outputFile)
        println("Writing output to $outputPath")
        outputStream = outputPath.outputStream()
    }

    val planPath = setupPath.resolveSibling(setup.planFile)
    println("Reading plan $planPath")
    val plan = planPath.inputStream().use {
        jsonFormat.decodeFromStream<Plan<M>>(it)
    }
    outputStream.use { out ->
        buildReportHandler(out).use { baseReportHandler ->
            runBlocking {
                // Write output in parallel with simulation
                baseReportHandler.inParallel { reportHandler ->
                    // Initialize the simulation from an incon, if available.
                    val simulation = if (setup.inconFile == null) {
                        println("No initial conditions given")
                        PlanSimulation.withoutIncon<M>(
                            reportHandler,
                            plan.startTime,
                            plan.startTime,
                            constructModel
                        )
                    } else {
                        val inconPath = setupPath.resolveSibling(setup.inconFile)
                        println("Reading initial conditions $inconPath")
                        PlanSimulation.withIncon<M>(
                            reportHandler,
                            JsonConditions.fromFile(inconPath, jsonFormat),
                            constructModel
                        )
                    }

                    // Run the plan itself
                    println("Running plan")
                    simulation.runPlan(plan)

                    // Write a fincon if requested
                    if (setup.finconFile != null) {
                        val finconPath = setupPath.resolveSibling(setup.finconFile)
                        println("Writing final conditions to $finconPath")
                        JsonConditions(jsonFormat)
                            .also(simulation::save)
                            .toFile(finconPath)
                    } else {
                        println("No final conditions requested")
                    }
                }
            }
        }
    }
}
