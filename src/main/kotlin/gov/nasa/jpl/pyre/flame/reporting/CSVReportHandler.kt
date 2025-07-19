package gov.nasa.jpl.pyre.flame.reporting

import gov.nasa.jpl.pyre.ember.ReportHandler
import gov.nasa.jpl.pyre.spark.reporting.ChannelizedReport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.serializer
import java.io.OutputStream
import java.io.OutputStreamWriter
import kotlin.reflect.KType
import kotlin.time.Instant

/**
 * Collates reports by channels into a CSV format.
 *
 * Each channel defines a column, plus an initial time column.
 * Each row contains reports for a single time.
 * If multiple reports are issued for the same channel at one time, only the last report is included.
 *
 * Note this is [AutoCloseable] and must be closed to ensure the last row is written.
 */
class CSVReportHandler(
    channels: List<String>,
    stream: OutputStream,
    private val jsonFormat: Json,
    timeColumnName: String = "time",
    private val timeFormat: (Instant) -> String = Instant::toString,
) : ReportHandler, AutoCloseable {
    private val channelSet: Set<String> = channels.toSet()
    private val columnLookup: Map<String, Int>
    private var currentTime: Instant? = null
    private val currentRow: Array<String>
    private val streamWriter: OutputStreamWriter = stream.writer()

    init {
        currentRow = (listOf(timeColumnName) + channels).toTypedArray()
        columnLookup = currentRow.mapIndexed<String, Pair<String, Int>> { i, c -> c to i }.toMap()
        flushCurrentRow()

        require(columnLookup.size == currentRow.size) {
            "All column names given to CSV report handler must be unique!"
        }
    }

    override fun <T> handle(value: T, type: KType) {
        if (value is ChannelizedReport<*>) {
            // If we have a row from an earlier time, because reports are time-ordered, we're done with that row.
            // Flush that row and adjust the time.
            if (currentTime != value.time) {
                if (currentTime != null) flushCurrentRow()
                currentRow[0] = timeFormat(value.time)
            }
            currentTime = value.time

            if (value.channel in channelSet) {
                val stringValue = if (value.data == null || value.data is String) {
                    // Write strings as-is, not wrapped in another layer of escaping
                    value.data ?: ""
                } else {
                    // Write everything else in JSON format, as a general-purpose fallback.
                    val dataType = requireNotNull(type.arguments[0].type)
                    jsonFormat.encodeToString(jsonFormat.serializersModule.serializer(dataType), value.data)
                }
                val column = columnLookup.getValue(value.channel)
                currentRow[column] = stringValue
            }
        }
    }

    private fun flushCurrentRow() {
        streamWriter.write(currentRow.joinToString(",", transform = ::escapeField))
        streamWriter.write("\n")
        currentRow.fill("")
    }

    private fun escapeField(data: String): String {
        return if (',' in data || '"' in data) {
            '"' + data.replace("\"", "\\\"") + '"'
        } else {
            data
        }
    }

    override fun close() {
        // If we have some lingering data in the row (besides the time itself), flush it.
        if (currentRow.slice(1..<currentRow.size).any { it.isNotEmpty() }) {
            flushCurrentRow()
        }
        streamWriter.close()
    }
}