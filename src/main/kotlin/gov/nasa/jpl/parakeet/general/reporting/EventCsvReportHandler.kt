package gov.nasa.jpl.parakeet.general.reporting

import gov.nasa.jpl.pyre.foundation.reporting.BaseChannelizedReportHandler
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport.ChannelData
import gov.nasa.jpl.pyre.utilities.Serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream
import java.io.OutputStreamWriter
import kotlin.time.Instant

/**
 * Use [EventCsvReportHandler] writing to this [File].
 */
fun File.usingEventCsvReportHandler(
    jsonFormat: Json = Json,
    timeFormat: (Instant) -> String = Instant::toString,
    block: (EventCsvReportHandler) -> Unit
) = outputStream().use { it.usingEventCsvReportHandler(jsonFormat, timeFormat, block) }

/**
 * Use [EventCsvReportHandler] writing to this [OutputStream].
 */
fun OutputStream.usingEventCsvReportHandler(
    jsonFormat: Json = Json,
    timeFormat: (Instant) -> String = Instant::toString,
    block: (EventCsvReportHandler) -> Unit
) = EventCsvReportHandler(this, jsonFormat, timeFormat).use(block)

/**
 * Writes reports in "Event CSV" format, a CSV file containing three columns:
 * - time - the date and time (UTC) of the report
 * - channel - the name of the channel
 * - data - the JSON-encoded payload of the report
 *
 * Instead of instantiating this class directly, consider using [File.usingEventCsvReportHandler] or [OutputStream.usingEventCsvReportHandler].
 * These functions automatically scope the handler's lifecycle, ensuring all data is flushed correctly before exiting.
 */
class EventCsvReportHandler(
    stream: OutputStream,
    private val jsonFormat: Json = Json,
    private val timeFormat: (Instant) -> String = Instant::toString,
): BaseChannelizedReportHandler(), AutoCloseable {
    private val streamWriter: OutputStreamWriter = stream.writer()

    init{
        writeRow("time", "channel", "data")
    }

    override fun <T> constructChannel(metadata: ChannelReport.ChannelMetadata<T>): (ChannelData<T>) -> Unit {
        // TODO: report the metadata somehow?
        return {
            val s = jsonFormat.encodeToString(metadata.dataType, it.data)
            val dataStr = if (s.startsWith('"') && s.endsWith('"') && ESCAPE_CHARS.none { it in s.substring(1..s.length-2) }) {
                // If the encoding is a quoted string that doesn't need escaping, strip the quotes back off
                jsonFormat.decodeFromString<String>(s)
            } else {
                // Otherwise, leave it escaped as-is
                s
            }
            writeRow(timeFormat(it.time), it.channel.toString(), dataStr)
        }
    }

    private fun writeRow(vararg row: String) {
        row.forEachIndexed { i, field ->
            if (i != 0) streamWriter.write(",")
            streamWriter.write(escapeField(field))
        }
        streamWriter.write("\n")
    }

    private fun escapeField(data: String): String {
        return if (',' in data || '"' in data) {
            '"' + data.replace("\"", "\"\"") + '"'
        } else {
            data
        }
    }

    override fun close() {
        streamWriter.close()
    }

    companion object {
        private val ESCAPE_CHARS: List<Char> = listOf('"', ',')
    }
}
