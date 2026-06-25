package gov.nasa.jpl.pyre.examples.orbit

import gov.nasa.jpl.pyre.foundation.Simulator
import gov.nasa.jpl.pyre.foundation.reporting.ChannelizedReportHandler
import gov.nasa.jpl.pyre.general.plans.runStandardPlanSimulation
import gov.nasa.jpl.pyre.general.reporting.useParallelReportHandler
import gov.nasa.jpl.pyre.general.reporting.useReportHandler
import kotlinx.coroutines.runBlocking
import org.duckdb.DuckDBConnection
import java.sql.DriverManager
import kotlin.time.Instant

// This is a simple setup, using default choices for how to run a simulation.
// Most importantly, it chooses to read and write files for incon, plan, outputs, and fincon,
// and to use the default CSV event output format.
fun simpleMain(args: Array<String>) {
    runStandardPlanSimulation(args[0], ::EarthOrbit, EarthOrbit.JSON_FORMAT)
}

fun main(args: Array<String>) {
    val channels_file = "test_data/orbit/channels.parquet"
    val reports_file = "test_data/orbit/reports.parquet"
    val start = Instant.parse("2000-01-01T00:00:00Z")
    val end = Instant.parse("2200-01-01T00:00:00Z")
    val useParallel = true

    (DriverManager.getConnection("jdbc:duckdb:") as DuckDBConnection).use { connection ->
        val runSimulation: (ChannelizedReportHandler) -> Unit = { handler ->
            val simulator = Simulator(
                handler,
                start,
                constructModel = ::EarthOrbit
            )
            simulator.runUntil(end)
        }
        if (useParallel) {
            connection.useParallelReportHandler(EarthOrbit.JSON_FORMAT, runSimulation)
        } else {
            connection.useReportHandler(EarthOrbit.JSON_FORMAT, runSimulation)
        }

        connection.prepareStatement("COPY channels TO '$channels_file' (FORMAT parquet)").execute()
        connection.prepareStatement("COPY reports TO '$reports_file' (FORMAT parquet)").execute()
    }
}
