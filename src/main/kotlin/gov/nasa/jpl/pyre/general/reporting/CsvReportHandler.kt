package gov.nasa.jpl.pyre.general.reporting

import gov.nasa.jpl.pyre.kernel.ReportHandler
import gov.nasa.jpl.pyre.foundation.reporting.ChannelData
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.OutputStream
import java.io.OutputStreamWriter
import kotlin.reflect.KType
import kotlin.time.Instant

class CsvReportHandler(
    stream: OutputStream,
    private val jsonFormat: Json = Json,
    private val timeFormat: (Instant) -> String = Instant::toString,
): ReportHandler, AutoCloseable {
    private val streamWriter: OutputStreamWriter = stream.writer()

    init{
        writeRow("time", "channel", "data")
    }

    override fun invoke(value: Any?, type: KType) {
        when (value) {
            is ChannelData<*> -> {
                val dataType = requireNotNull(type.arguments[0].type)
                val s = jsonFormat.encodeToString(jsonFormat.serializersModule.serializer(dataType), value.data)
                val dataStr = if (s.startsWith('"') && s.endsWith('"') && ESCAPE_CHARS.none { it in s.substring(1..s.length-2) }) {
                    // If the encoding is a quoted string that doesn't need escaping, strip the quotes back off
                    jsonFormat.decodeFromString<String>(s)
                } else {
                    // Otherwise, leave it escaped as-is
                    s
                }
                writeRow(timeFormat(value.time), value.channel.toString(), dataStr)
            }
            // TODO: Consider how to report channel metadata
            // is ChannelMetadata -> {
            //     writeRow("", value.name.toString(), jsonFormat.encodeToString(value.metadata))
            // }
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
