package gov.nasa.jpl.pyre.general.reporting

import gov.nasa.jpl.pyre.foundation.reporting.BaseChannelizedReportHandler
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport
import gov.nasa.jpl.pyre.foundation.reporting.ChannelizedReportHandler
import gov.nasa.jpl.pyre.general.reporting.ParallelReportHandler.Companion.inParallel
import gov.nasa.jpl.pyre.utilities.Serialization.encodeToString
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.duckdb.DuckDBAppender
import org.duckdb.DuckDBConnection

/**
 * Use a [ChannelizedReportHandler] that writes to this [DuckDBConnection].
 *
 * See [DuckDbReportHandler] for details on how it writes.
 *
 * @see [useParallelReportHandler] for a version that writes in parallel with simulation.
 */
fun DuckDBConnection.useReportHandler(json: Json, block: (ChannelizedReportHandler) -> Unit) =
    block(DuckDbReportHandler(this, json, false))

/**
 * Use a [ChannelizedReportHandler] that writes to this [DuckDBConnection] in parallel with simulation.
 *
 * See [DuckDbReportHandler] for details on how it writes.
 */
fun DuckDBConnection.useParallelReportHandler(json: Json, block: (ChannelizedReportHandler) -> Unit) = runBlocking {
    DuckDbReportHandler(this@useParallelReportHandler, json, true).inParallel(block)
}

/**
 * Write reports to a [DuckDBConnection].
 *
 * Creates and populates two tables, "channels" and "reports".
 */
class DuckDbReportHandler(
    private val connection: DuckDBConnection,
    private val json: Json,
    breakThreadConfinement: Boolean,
) : BaseChannelizedReportHandler(), AutoCloseable {
    private val channelAppender: DuckDBAppender
    private val reportAppender: DuckDBAppender

    init {
        connection.createStatement()
            .execute("""
                CREATE TABLE IF NOT EXISTS channels (
                    channel VARCHAR,
                    metadata JSON
                )
            """.trimIndent())
        connection.createStatement()
            .execute("""
                CREATE TABLE IF NOT EXISTS reports (
                    time TIMESTAMP_NS,
                    channel VARCHAR,
                    data JSON,
                )""".trimIndent())

        channelAppender = checkNotNull(connection.createAppender("channels"))
        reportAppender = checkNotNull(connection.createAppender("reports"))

        if (breakThreadConfinement) {
            // The simulator ensures that the report handler is run in a functionally-single-threaded way.
            // We can ask the appenders to simply trust us, and even discard the lock they provide.
            channelAppender.unsafeBreakThreadConfinement()
            reportAppender.unsafeBreakThreadConfinement()
        }
    }

    override fun <T> constructChannel(metadata: ChannelReport.ChannelMetadata<T>): (ChannelReport.ChannelData<T>) -> Unit {
        // TODO: Figure out how to append structs directly instead of through serialization like this
        val channelString = metadata.channel.toString()
        channelAppender.beginRow()
            .append(channelString)
            .append(json.encodeToString(metadata.metadata))
            .endRow()
            // Eagerly flush channel appender to avoid foreign key violations in the table.
            .flush()

        return { data ->
            reportAppender.beginRow()
                .append(data.time.toLocalDateTime(TimeZone.UTC).toJavaLocalDateTime())
                .append(channelString)
                .append(json.encodeToString(metadata.dataType, data.data))
                .endRow()
        }
    }

    override fun close() {
        channelAppender.close()
        reportAppender.close()
    }
}
