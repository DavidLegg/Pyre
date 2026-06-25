package gov.nasa.jpl.pyre.general.reporting

import gov.nasa.jpl.pyre.foundation.reporting.BaseChannelizedReportHandler
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport
import gov.nasa.jpl.pyre.utilities.Serialization.encodeToString
import kotlinx.serialization.json.Json
import org.duckdb.DuckDBConnection
import java.sql.PreparedStatement

class DuckDbReportHandler(
    private val connection: DuckDBConnection,
    private val json: Json
) : BaseChannelizedReportHandler() {
    private val insertChannelStmt: PreparedStatement
    private val insertReportStmt: PreparedStatement

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
                    FOREIGN KEY channel VARCHAR REFERENCES channels(channel),
                    data JSON
                )""".trimIndent())

        insertChannelStmt = checkNotNull(connection.prepareStatement("""
            INSERT INTO channels (channel, metadata) VALUES (?, ?)
        """.trimIndent()))
        insertReportStmt = checkNotNull(connection.prepareStatement("""
            INSERT INTO reports (time, channel, data) VALUES (?, ?, ?)
        """.trimIndent()))
    }

    override fun <T> constructChannel(metadata: ChannelReport.ChannelMetadata<T>): (ChannelReport.ChannelData<T>) -> Unit {
        insertChannelStmt.setString(1, metadata.channel.toString())
        insertChannelStmt.setString(2, json.encodeToString(metadata.metadata))
        insertChannelStmt.executeUpdate()
        insertChannelStmt.clearParameters()

        // TODO: Batch insert report statements
        return { data ->
            insertReportStmt.setString(1, data.time.toString())
            insertReportStmt.setString(2, data.channel.toString())
            insertReportStmt.setString(3, json.encodeToString(metadata.dataType, data.data))
            insertReportStmt.execute()
            insertReportStmt.clearParameters()
        }
    }
}