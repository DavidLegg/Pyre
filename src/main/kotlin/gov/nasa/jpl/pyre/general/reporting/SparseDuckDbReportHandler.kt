package gov.nasa.jpl.pyre.general.reporting

import gov.nasa.jpl.pyre.foundation.reporting.BaseChannelizedReportHandler
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport
import gov.nasa.jpl.pyre.utilities.Serialization.encodeToString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.duckdb.DuckDBAppender
import org.duckdb.DuckDBColumnType
import org.duckdb.DuckDBConnection
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.sql.Types
import kotlin.time.toJavaInstant

class SparseDuckDbReportHandler(
    private val connection: DuckDBConnection,
    private val json: Json,
) : BaseChannelizedReportHandler(), AutoCloseable {
    private val reportSqlTypes = mutableListOf(
        Types.TIMESTAMP,
        Types.VARCHAR,
    )
    private val channelAppender: DuckDBAppender
    private var reportsAppender: PreparedStatement
    private var pendingReports = 0
    private val reportsBatchSize = 100_000

    init {
        connection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS channels (
                    channel VARCHAR PRIMARY KEY,
                    metadata JSON
                );
                CREATE TABLE reports (
                    time TIMESTAMP,
                    channel VARCHAR,
                    FOREIGN KEY (channel) REFERENCES channels(channel)
                );
            """.trimIndent())
        channelAppender = connection.createAppender("channels")
        reportsAppender = buildReportsAppender()
    }

    override fun <T> constructChannel(metadata: ChannelReport.ChannelMetadata<T>): (ChannelReport.ChannelData<T>) -> Unit {
        val channelString = metadata.channel.toString()
        channelAppender.beginRow()
            .append(channelString)
            .append(json.encodeToString(metadata.metadata))
            .endRow()
            .flush()

        // Flush any reports we already built
        if (pendingReports > 0) reportsAppender.executeBatch()
        // Close the prepared statements now that we're done with it.
        reportsAppender.close()

        @Suppress("UNCHECKED_CAST")
        val serializer = json.serializersModule.serializer(metadata.dataType) as KSerializer<T>
        val reportColumn = reportSqlTypes.size
        reportSqlTypes.addLast(buildSqlType(serializer.descriptor))
        connection.prepareStatement("""
                ALTER TABLE reports ADD COLUMN "$channelString" ${buildTypeString(serializer.descriptor)}
            """.trimIndent()).executeUpdate()

        // Build a new appender for the altered reports table
        reportsAppender = buildReportsAppender()

        return {
            reportsAppender.setTimestamp(1, Timestamp.from(it.time.toJavaInstant()))
            reportsAppender.setString(2, channelString)
            for (i in 2..<reportColumn) {
                reportsAppender.setNull(i, reportSqlTypes[i])
            }
            reportsAppender.setString(reportColumn, json.encodeToString(metadata.dataType, it.data))
            for (i in (reportColumn + 1)..<reportSqlTypes.size) {
                reportsAppender.setNull(i, reportSqlTypes[i])
            }
            reportsAppender.addBatch()
            pendingReports++
            if (pendingReports >= reportsBatchSize) {
                reportsAppender.executeBatch()
                reportsAppender.clearBatch()
                pendingReports = 0
            }
        }
    }

    private fun buildSqlType(descriptor: SerialDescriptor): Int = when (descriptor.kind) {
        PrimitiveKind.BOOLEAN -> Types.BOOLEAN
        PrimitiveKind.BYTE -> Types.TINYINT
        PrimitiveKind.CHAR -> Types.VARCHAR
        PrimitiveKind.DOUBLE -> Types.DOUBLE
        PrimitiveKind.FLOAT -> Types.FLOAT
        PrimitiveKind.INT -> Types.INTEGER
        PrimitiveKind.LONG -> Types.BIGINT
        PrimitiveKind.SHORT -> Types.SMALLINT
        PrimitiveKind.STRING -> Types.VARCHAR
        SerialKind.ENUM -> Types.VARCHAR
        StructureKind.CLASS -> Types.STRUCT
        StructureKind.LIST -> Types.STRUCT
        StructureKind.MAP -> Types.STRUCT
        StructureKind.OBJECT -> Types.STRUCT
        // Remaining kinds are for polymorphism and contextual serialization.
        // These should have been resolved, so it's an error to hit them now.
        else -> throw IllegalArgumentException("Unknown serializer descriptor kind ${descriptor.kind}")
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun buildTypeString(descriptor: SerialDescriptor): String = when (descriptor.kind) {
        PrimitiveKind.BOOLEAN -> DuckDBColumnType.BOOLEAN.name
        PrimitiveKind.BYTE -> DuckDBColumnType.TINYINT.name
        PrimitiveKind.CHAR -> DuckDBColumnType.VARCHAR.name
        PrimitiveKind.DOUBLE -> DuckDBColumnType.DOUBLE.name
        PrimitiveKind.FLOAT -> DuckDBColumnType.FLOAT.name
        PrimitiveKind.INT -> DuckDBColumnType.INTEGER.name
        PrimitiveKind.LONG -> DuckDBColumnType.BIGINT.name
        PrimitiveKind.SHORT -> DuckDBColumnType.SMALLINT.name
        PrimitiveKind.STRING -> DuckDBColumnType.VARCHAR.name
        SerialKind.ENUM -> DuckDBColumnType.VARCHAR.name
        StructureKind.CLASS -> {
            val structArgs = (descriptor.elementNames zip descriptor.elementDescriptors)
                .joinToString(",") { (name, desc) -> "\"$name\" ${buildTypeString(desc)}" }
            "${DuckDBColumnType.STRUCT.name}($structArgs)"
        }
        StructureKind.LIST -> "${buildTypeString(descriptor.getElementDescriptor(0))}[]"
        StructureKind.MAP -> {
            val keyTypeString = buildTypeString(descriptor.getElementDescriptor(0))
            val valueTypeString = buildTypeString(descriptor.getElementDescriptor(1))
            "${DuckDBColumnType.MAP.name}(${keyTypeString}, ${valueTypeString})"
        }
        // The OBJECT structure kind is for singletons, encoded as an empty JSON object
        StructureKind.OBJECT -> "${DuckDBColumnType.STRUCT.name}()"
        // Remaining kinds are for polymorphism and contextual serialization.
        // These should have been resolved, so it's an error to hit them now.
        else -> throw IllegalArgumentException("Unknown serializer descriptor kind ${descriptor.kind}")
    }

    private fun buildReportsAppender(): PreparedStatement =
        connection.prepareStatement("""
            INSERT INTO reports VALUES (${",?".repeat(reportSqlTypes.size).substring(1)})
        """.trimIndent())

    override fun close() {
        channelAppender.close()
        if (pendingReports > 0) {
            reportsAppender.executeBatch()
            reportsAppender.clearBatch()
            pendingReports = 0
        }
        reportsAppender.close()
    }
}