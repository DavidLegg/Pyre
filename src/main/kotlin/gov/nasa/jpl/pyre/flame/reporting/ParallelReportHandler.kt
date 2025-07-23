package gov.nasa.jpl.pyre.flame.reporting

import gov.nasa.jpl.pyre.ember.ReportHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.lang.AutoCloseable
import kotlin.reflect.KType

/**
 * Runs the report handler on the IO dispatcher, in parallel with the main thread.
 */
class ParallelReportHandler private constructor(
    scope: CoroutineScope,
    private val handler: ReportHandler,
) : ReportHandler, AutoCloseable {
    private val channel = Channel<Pair<Any?, KType>>(Channel.BUFFERED)
    private val job = scope.launch(Dispatchers.IO) {
        for ((value, type) in channel) {
            handler(value, type)
        }
    }

    override fun invoke(value: Any?, type: KType) {
        runBlocking {
            channel.send(Pair(value, type))
        }
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
        fun <R> ReportHandler.inParallel(block: (ParallelReportHandler) -> R) = ParallelReportHandler(scope, this).use(block)
    }
}
