package gov.nasa.jpl.pyre.general.reporting

import gov.nasa.jpl.pyre.foundation.reporting.BaseChannelizedReportHandler
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport.ChannelData
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport.ChannelMetadata
import gov.nasa.jpl.pyre.foundation.reporting.ChannelizedReportHandler
import gov.nasa.jpl.pyre.foundation.reporting.Serialization.encodeToStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.io.OutputStream


object ReportHandling {
    /**
     * Does nothing with reports sent to it.
     */
    val discardReports: ChannelizedReportHandler = object : ChannelizedReportHandler {
        override fun <T> initChannel(metadata: ChannelMetadata<T>) {}
        override fun <T> report(data: ChannelData<T>) {}
    }

    /**
     * Writes reports as JSON lines directly to an output stream.
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun jsonlReportHandler(stream: OutputStream = System.out, jsonFormat: Json = Json): ChannelizedReportHandler =
        object : BaseChannelizedReportHandler() {
            override fun <T> constructChannel(metadata: ChannelMetadata<T>): (ChannelData<T>) -> Unit {
                // Write the metadata entry itself out
                jsonFormat.encodeToStream(metadata.metadataType, metadata, stream)
                stream.write('\n'.code)
                // Return a handler which bakes in the report type, and can write reports out.
                return {
                    jsonFormat.encodeToStream(metadata.reportType, it, stream)
                    stream.write('\n'.code)
                }
            }
        }
}