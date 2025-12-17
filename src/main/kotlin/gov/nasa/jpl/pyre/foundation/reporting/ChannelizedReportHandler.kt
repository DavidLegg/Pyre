package gov.nasa.jpl.pyre.foundation.reporting

import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport.ChannelData
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport.ChannelMetadata
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.ReportHandler

/**
 * Variation on [ReportHandler] specialized to handle channelized data.
 *
 * Implementations may assume that [initChannel] is called before [report] on any given channel,
 * though [initChannel] may be called after [report] for different channels.
 */
interface ChannelizedReportHandler : ReportHandler {
    fun <T> initChannel(metadata: ChannelMetadata<T>)
    fun <T> report(data: ChannelData<T>)

    override fun invoke(p1: Any?) {
        if (p1 is ChannelReport<*>) {
            when (p1) {
                is ChannelMetadata<*> -> initChannel(p1)
                is ChannelData<*> -> report(p1)
            }
        } else {
            throw IllegalArgumentException(ChannelizedReportHandler::class.simpleName +
                    " expects all reports to be ${ChannelReport::class.simpleName}," +
                    " but this report is ${p1?.let { it::class.simpleName } ?: "null"} instead.")
        }
    }
}

/**
 * Standard pattern for type-safe channel handling, which constructs and registers a handler for each initialized channel.
 *
 * For high-performance systems, there may be more efficient options that avoid unnecessary switching on channel name.
 */
abstract class BaseChannelizedReportHandler : ChannelizedReportHandler {
    abstract fun <T> constructChannel(metadata: ChannelMetadata<T>): (ChannelData<T>) -> Unit

    private val channelHandlers = mutableMapOf<Name, (ChannelData<*>) -> Unit>()

    override fun <T> initChannel(metadata: ChannelMetadata<T>) {
        val handler = constructChannel(metadata)
        @Suppress("UNCHECKED_CAST")
        check (channelHandlers.putIfAbsent(metadata.channel) { handler(it as ChannelData<T>) } == null) {
            "Channel ${metadata.channel} was initialized twice!"
        }
    }

    override fun <T> report(data: ChannelData<T>) {
        channelHandlers.getValue(data.channel)(data)
    }
}
