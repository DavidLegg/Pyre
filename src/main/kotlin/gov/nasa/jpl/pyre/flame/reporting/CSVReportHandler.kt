package gov.nasa.jpl.pyre.flame.reporting

import gov.nasa.jpl.pyre.ember.ReportHandler
import gov.nasa.jpl.pyre.spark.reporting.ChannelizedReport
import kotlinx.serialization.json.Json
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
    stream: OutputStream,
    private val jsonFormat: Json,
    timeColumnName: String = "time",
    private val timeFormat: (Instant) -> String = Instant::toString,
) : ReportHandler, AutoCloseable {
    private val columnLookup: MutableMap<String, Int> = mutableMapOf(timeColumnName to 0)
    private var currentTime: Instant? = null
    private var headerRow: MutableList<String> = mutableListOf(timeColumnName)
    private var currentRow: Array<String> = Array(1) { "" }
    private val streamWriter: OutputStreamWriter = stream.writer()
    private var initialized: Boolean = false

    override fun invoke(value: Any?, type: KType) {
        if (value is ChannelizedReport<*>) {
            // If we have a row from an earlier time, because reports are time-ordered, we're done with that row.
            // Flush that row and adjust the time.
            if (currentTime != value.time) {
                if (currentTime != null) {
                    flushCurrentRow()
                }
                currentTime = value.time
                currentRow[0] = timeFormat(value.time)
            }

            if (value.channel !in columnLookup && !initialized) {
                columnLookup[value.channel] = columnLookup.size
                headerRow += value.channel
                currentRow = currentRow + ""
            }

            columnLookup[value.channel]?.let { column ->
                // Start off by JSON-encoding everything as a general-purpose fallback
                val dataType = requireNotNull(type.arguments[0].type)
                val s = jsonFormat.encodeToString(jsonFormat.serializersModule.serializer(dataType), value.data)
                currentRow[column] = if (s.startsWith('"') && s.endsWith('"')
                    && ESCAPE_CHARS.none { it in s.substring(1..s.length-2) }) {
                    // If the encoding is a quoted string that doesn't need escaping, strip the quotes back off
                    jsonFormat.decodeFromString<String>(s)
                } else {
                    // Otherwise, leave it escaped as-is
                    s
                }
            }
        }
    }

    companion object {
        private val ESCAPE_CHARS: List<Char> = listOf('"', ',')
    }

    private fun flushCurrentRow() {
        if (!initialized) {
            writeRow(headerRow.toTypedArray())
            initialized = true
            // Clear the header row list to save a little memory
            headerRow.clear()
        }
        writeRow(currentRow)
        currentRow.fill("")
    }

    private fun writeRow(row: Array<String>) {
        row.forEachIndexed { i, field ->
            if (i != 0) streamWriter.write(",")
            streamWriter.write(escapeField(field))
        }
        streamWriter.write("\n")
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