package gov.nasa.jpl.pyre.general.reporting

import gov.nasa.jpl.pyre.foundation.reporting.BaseChannelizedReportHandler
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport.ChannelData
import gov.nasa.jpl.pyre.foundation.reporting.Serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.io.OutputStreamWriter
import kotlin.time.Instant

class CsvReportHandler(
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
