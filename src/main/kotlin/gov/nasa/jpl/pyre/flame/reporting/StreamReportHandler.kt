package gov.nasa.jpl.pyre.flame.reporting

import gov.nasa.jpl.pyre.ember.ReportHandler
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.serializer
import java.io.OutputStream
import kotlin.reflect.KType

class StreamReportHandler(
    private val stream: OutputStream = System.out,
    private val jsonFormat: Json = Json.Default,
) : ReportHandler {
    @OptIn(ExperimentalSerializationApi::class)
    override fun <T> handle(value: T, type: KType) {
        jsonFormat.encodeToStream(jsonFormat.serializersModule.serializer(type), value, stream)
        stream.write('\n'.code)
    }
}