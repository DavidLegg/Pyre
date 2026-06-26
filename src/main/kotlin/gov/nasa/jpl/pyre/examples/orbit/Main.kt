package gov.nasa.jpl.pyre.examples.orbit

import gov.nasa.jpl.pyre.foundation.Simulator
import gov.nasa.jpl.pyre.foundation.reporting.ChannelizedReportHandler
import gov.nasa.jpl.pyre.general.plans.runStandardPlanSimulation
import gov.nasa.jpl.pyre.general.reporting.CsvReportHandler
import gov.nasa.jpl.pyre.general.reporting.ParallelReportHandler.Companion.inParallel
import gov.nasa.jpl.pyre.general.reporting.useParallelReportHandler
import gov.nasa.jpl.pyre.general.reporting.useReportHandler
import org.duckdb.DuckDBConnection
import java.sql.DriverManager
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.outputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.measureTime

// This is a simple setup, using default choices for how to run a simulation.
// Most importantly, it chooses to read and write files for incon, plan, outputs, and fincon,
// and to use the default CSV event output format.
fun simpleMain(args: Array<String>) {
    runStandardPlanSimulation(args[0], ::EarthOrbit, EarthOrbit.JSON_FORMAT)
}

fun main(args: Array<String>) {
    val aggregateTestTiming = AggregateTestTiming()
    // Run 25 of each combination, in random order.
    // This should roughly average out the effects of JIT "warming"
    (1..20).shuffled().forEach {
        aggregateTestTiming.runTest(it)
    }

    fun List<TestTiming>.printAverage() {
        val averageOverall = fold(Duration.ZERO) { acc, timing -> acc + timing.overall } / size
        val averageSimulation = fold(Duration.ZERO) { acc, timing -> acc + timing.simulation } / size
        println("  Average overall time:    $averageOverall")
        println("  Average simulation time: $averageSimulation")
    }

    println("\n\nAggregate results:")
    println("\nSerial CSV:")
    aggregateTestTiming.serialCsvTimings.printAverage()
    println("\nSerial DuckDB:")
    aggregateTestTiming.serialDuckDbTimings.printAverage()
    println("\nParallel CSV:")
    aggregateTestTiming.parallelCsvTimings.printAverage()
    println("\nParallel DuckDB:")
    aggregateTestTiming.parallelDuckDbTimings.printAverage()
}

val out_dir = Path("test_data/orbit/")

data class AggregateTestTiming(
    val serialCsvTimings: MutableList<TestTiming> = mutableListOf(),
    val parallelCsvTimings: MutableList<TestTiming> = mutableListOf(),
    val serialDuckDbTimings: MutableList<TestTiming> = mutableListOf(),
    val parallelDuckDbTimings: MutableList<TestTiming> = mutableListOf(),
)

data class TestTiming(
    val overall: Duration,
    val simulation: Duration,
)

fun AggregateTestTiming.runTest(index: Int) {
    val useDuckDb = index % 2 == 1
    val useParallel = index % 4 < 2
    val resultList = if (useDuckDb) {
        if (useParallel) {
            parallelDuckDbTimings
        } else {
            serialDuckDbTimings
        }
    } else {
        if (useParallel) {
            parallelCsvTimings
        } else {
            serialCsvTimings
        }
    }
    resultList += runTest(useDuckDb, useParallel)
}

fun runTest(useDuckDb: Boolean, useParallel: Boolean): TestTiming {
    println("--- Test: ${if (useDuckDb) "DuckDB" else "CSV"} ${if (useParallel) "parallel" else "serial"} ---")

    val start = Instant.parse("2000-01-01T00:00:00Z")
    val end = Instant.parse("2200-01-01T00:00:00Z")
    val parallelString = if (useParallel) "parallel" else "serial"
    val channels_file = out_dir / "channels.$parallelString.parquet"
    val reports_file = out_dir / "reports.$parallelString.parquet"
    val csv_file = out_dir / "reports.$parallelString.csv"

    var simulationDuration: Duration = 0.seconds
    val runSimulation: (ChannelizedReportHandler) -> Unit = { handler ->
        simulationDuration = measureTime {
            val simulator = Simulator(
                handler,
                start,
                constructModel = ::EarthOrbit
            )
            simulator.runUntil(end)
        }
    }

    val overallDuration = measureTime {
        if (useDuckDb) {
            (DriverManager.getConnection("jdbc:duckdb:") as DuckDBConnection).use { connection ->
                if (useParallel) {
                    connection.useParallelReportHandler(EarthOrbit.JSON_FORMAT, runSimulation)
                } else {
                    connection.useReportHandler(EarthOrbit.JSON_FORMAT, runSimulation)
                }

                // Testing shows that the time to write the parquet file out of memory is negligible.
                connection.prepareStatement("COPY channels TO '$channels_file' (FORMAT parquet)").execute()
                connection.prepareStatement("COPY reports TO '$reports_file' (FORMAT parquet)").execute()
            }
        } else {
            csv_file.outputStream().use { outputStream ->
                CsvReportHandler(outputStream, EarthOrbit.JSON_FORMAT).use {
                    if (useParallel) {
                        it.inParallel(runSimulation)
                    } else {
                        runSimulation(it)
                    }
                }
            }
        }
    }

    println("Overall time:    $overallDuration")
    println("Simulation time: $simulationDuration")
    return TestTiming(overallDuration, simulationDuration)
}
