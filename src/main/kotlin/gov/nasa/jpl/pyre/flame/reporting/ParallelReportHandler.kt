package gov.nasa.jpl.pyre.flame.reporting

import gov.nasa.jpl.pyre.ember.ReportHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
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
        while (true) {
            val (value, type) = channel.receive()
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
            job.cancelAndJoin()
        }
    }

    companion object {
        fun CoroutineScope.inParallel(handler: ReportHandler) = ParallelReportHandler(this, handler)
    }
}
