package gov.nasa.jpl.pyre.examples.orbit

import gov.nasa.jpl.pyre.foundation.Simulator
import gov.nasa.jpl.pyre.general.plans.runStandardPlanSimulation
import gov.nasa.jpl.pyre.general.reporting.DuckDbReportHandler
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

    (DriverManager.getConnection("jdbc:duckdb:") as DuckDBConnection).use { connection ->
        DuckDbReportHandler(connection, EarthOrbit.JSON_FORMAT).use { handler ->
            val simulator = Simulator(
                handler,
                start,
                constructModel = ::EarthOrbit
            )
            simulator.runUntil(end)

            connection.prepareStatement(
                """
            COPY channels TO '$channels_file' (FORMAT parquet)
        """.trimIndent()
            ).execute()
            connection.prepareStatement(
                """
            COPY reports TO '$reports_file' (FORMAT parquet)
        """.trimIndent()
            ).execute()
        }
    }
}
