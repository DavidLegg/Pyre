package gov.nasa.jpl.pyre.incremental.foundation

import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport.*
import gov.nasa.jpl.pyre.kernel.Name

/**
 * The combination of [IncrementalReportHandler] and [gov.nasa.jpl.pyre.foundation.reporting.ChannelizedReportHandler],
 * a report handler which constructs channels during initialization,
 * and permits both issuing and revoking reports on those channels.
 */
interface IncrementalChannelizedReportHandler : IncrementalReportHandler {
    fun <T> initChannel(metadata: ChannelMetadata<T>)
}

interface IncrementalChannelHandler<T> {
    fun report(report: IncrementalReport<ChannelData<T>>)
    fun revoke(report: IncrementalReport<ChannelData<T>>)
}

abstract class BaseIncrementalChannelizedReportHandler : IncrementalChannelizedReportHandler {
    abstract fun <T> constructChannel(metadata: ChannelMetadata<T>): IncrementalChannelHandler<T>

    private val channelHandlers = mutableMapOf<Name, IncrementalChannelHandler<*>>()

    override fun <T> initChannel(metadata: ChannelMetadata<T>) {
        val handler = constructChannel(metadata)
        check(channelHandlers.putIfAbsent(metadata.channel, handler) == null) {
            "Channel ${metadata.channel} was initialized twice!"
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun report(report: IncrementalReport<*>) =
        coerceAndReport((report as IncrementalReport<ChannelData<*>>).content, report)
    @Suppress("UNCHECKED_CAST")
    override fun revoke(report: IncrementalReport<*>) =
        coerceAndRevoke((report as IncrementalReport<ChannelData<*>>).content, report)

    // These awkwardly-typed helper functions are a kludge around the generics system imposed by type erasure.
    // We need the type variable T to appear as a top-level type var in order to omit it above,
    // and we must omit it above since we only have star projections above.

    @Suppress("UNCHECKED_CAST")
    private fun <T> coerceAndReport(reportContent: ChannelData<T>, report: IncrementalReport<ChannelData<*>>) =
        (channelHandlers.getValue(reportContent.channel) as IncrementalChannelHandler<T>)
            .report(report as IncrementalReport<ChannelData<T>>)

    @Suppress("UNCHECKED_CAST")
    private fun <T> coerceAndRevoke(reportContent: ChannelData<T>, report: IncrementalReport<ChannelData<*>>) =
        (channelHandlers.getValue(reportContent.channel) as IncrementalChannelHandler<T>)
            .revoke(report as IncrementalReport<ChannelData<T>>)
}
