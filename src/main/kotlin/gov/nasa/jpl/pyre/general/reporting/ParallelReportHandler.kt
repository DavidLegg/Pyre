package gov.nasa.jpl.pyre.general.reporting

import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport
import gov.nasa.jpl.pyre.foundation.reporting.ChannelizedReportHandler
import gov.nasa.jpl.pyre.kernel.ReportHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.lang.AutoCloseable

/**
 * Runs the report handler on the IO dispatcher, in parallel with the main thread.
 */
class ParallelReportHandler private constructor(
    scope: CoroutineScope,
    private val handler: ChannelizedReportHandler,
) : ChannelizedReportHandler, AutoCloseable {
    private val channel = Channel<Any?>(Channel.BUFFERED)
    private val job = scope.launch(Dispatchers.IO) {
        for (report in channel) {
            handler(report)
        }
    }

    // Unlike most ChannelizedReportHandlers, this defers the two specialized methods to the general report,
    // rather than splitting report into two specialized handlers.
    override fun <T> initChannel(metadata: ChannelReport.ChannelMetadata<T>) {
        this(metadata)
    }

    override fun <T> report(data: ChannelReport.ChannelData<T>) {
        this(data)
    }

    override fun invoke(p1: Any?) = runBlocking {
        channel.send(p1)
    }

    override fun close() {
        runBlocking {
            // Close the channel to signal end-of-data to the reporter
            channel.close()
            // Join the reporter to await it reporting all remaining data in the channel
            job.join()
        }
    }

    companion object {
        context (scope: CoroutineScope)
        fun <R> ChannelizedReportHandler.inParallel(block: (ParallelReportHandler) -> R) = ParallelReportHandler(scope, this).use(block)
    }
}
