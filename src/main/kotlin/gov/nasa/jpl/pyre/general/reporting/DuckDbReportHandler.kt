package gov.nasa.jpl.pyre.general.reporting

import gov.nasa.jpl.pyre.foundation.reporting.BaseChannelizedReportHandler
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport
import gov.nasa.jpl.pyre.utilities.Serialization.encodeToString
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.duckdb.DuckDBAppender
import org.duckdb.DuckDBConnection

class DuckDbReportHandler(
    private val connection: DuckDBConnection,
    private val json: Json
) : BaseChannelizedReportHandler(), AutoCloseable {
    private val channelAppender: DuckDBAppender
    private val reportAppender: DuckDBAppender

    init {
        connection.createStatement()
            .execute("""
                CREATE TABLE IF NOT EXISTS channels (
                    channel VARCHAR PRIMARY KEY,
                    metadata JSON
                )
            """.trimIndent())
        connection.createStatement()
            .execute("""
                CREATE TABLE IF NOT EXISTS reports (
                    time TIMESTAMP_NS,
                    channel VARCHAR,
                    data JSON,
                    FOREIGN KEY (channel) REFERENCES channels(channel)
                )""".trimIndent())

        channelAppender = checkNotNull(connection.createAppender("channels"))
        reportAppender = checkNotNull(connection.createAppender("reports"))
    }

    override fun <T> constructChannel(metadata: ChannelReport.ChannelMetadata<T>): (ChannelReport.ChannelData<T>) -> Unit {
        // TODO: Figure out how to append structs directly instead of through serialization like this
        val channelString = metadata.channel.toString()
        channelAppender.beginRow()
        channelAppender.append(channelString)
        channelAppender.append(json.encodeToString(metadata.metadata))
        channelAppender.endRow()
        // Eagerly flush channel appender to avoid foreign key violations in the table.
        channelAppender.flush()

        return { data ->
            reportAppender.beginRow()
            reportAppender.append(data.time.toLocalDateTime(TimeZone.UTC).toJavaLocalDateTime())
            reportAppender.append(channelString)
            reportAppender.append(json.encodeToString(metadata.dataType, data.data))
            reportAppender.endRow()
        }
    }

    override fun close() {
        channelAppender.close()
        reportAppender.close()
    }
}