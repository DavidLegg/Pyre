package gov.nasa.jpl.pyre.flame.reporting

import gov.nasa.jpl.pyre.utilities.Reflection.withArg
import gov.nasa.jpl.pyre.utilities.andThen
import gov.nasa.jpl.pyre.kernel.ReportHandler
import gov.nasa.jpl.pyre.foundation.reporting.ChannelizedReport
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.serializer
import java.io.OutputStream
import kotlin.let
import kotlin.reflect.KType
import kotlin.reflect.typeOf

typealias TypedChannelReport<T> = Pair<ChannelizedReport<T>, KType>
typealias TypedChannelReportProcessor<T, S> = (TypedChannelReport<T>) -> TypedChannelReport<S>

object ReportHandling {
    /**
     * Does nothing with reports sent to it.
     */
    val discardReports: ReportHandler = { value, type -> }

    /**
     * Writes reports as JSON lines directly to an output stream.
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun jsonlReportHandler(stream: OutputStream = System.out, jsonFormat: Json = Json): ReportHandler = { value, type ->
        jsonFormat.encodeToStream(jsonFormat.serializersModule.serializer(type), value, stream)
        stream.write('\n'.code)
    }

    /**
     * Split reports by channel.
     * Reports on named channels go to their corresponding handlers.
     * All other reports go to miscHandler (discarded by default).
     */
    fun channels(vararg channelHandlers: Pair<String, ReportHandler>, miscHandler: ReportHandler = discardReports): ReportHandler =
        channels(channelHandlers.toMap(), miscHandler)

    fun channels(channelHandlers: Map<String, ReportHandler>, miscHandler: ReportHandler): ReportHandler = {
        value, type ->
        ((value as? ChannelizedReport<*>)
            ?.channel
            ?.let { channelHandlers[it] }
            ?: miscHandler
                )(value, type)
    }

    sealed interface HierarchicalReportingStructure {
        fun resolve(channelParts: List<String>): ReportHandler?

        private class SimpleReportHandler(val base: ReportHandler) : HierarchicalReportingStructure {
            override fun resolve(channelParts: List<String>): ReportHandler? = base
        }

        private class SplittingReportHandler(val branches: Map<String, HierarchicalReportingStructure>) : HierarchicalReportingStructure {
            override fun resolve(channelParts: List<String>): ReportHandler? =
                channelParts.firstOrNull()
                    ?.let(branches::get)
                    ?.resolve(channelParts.subList(1, channelParts.size))
        }

        companion object {
            fun reportTo(base: ReportHandler): HierarchicalReportingStructure = SimpleReportHandler(base)
            fun split(branches: Map<String, HierarchicalReportingStructure>): HierarchicalReportingStructure =
                SplittingReportHandler(branches)
            fun split(vararg branches: Pair<String, HierarchicalReportingStructure>): HierarchicalReportingStructure =
                split(branches.toMap())
        }
    }

    /**
     * Split channels out according to a hierarchical structure.
     *
     * By default, channel names are split by backslash (/), the default delimiter used by [gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.subContext].
     *
     * Use [HierarchicalReportingStructure.reportTo] and [HierarchicalReportingStructure.split] to construct the handler.
     */
    fun hierarchicalChannels(handler: HierarchicalReportingStructure, miscHandler: ReportHandler = discardReports, delimiter: String = "/"): ReportHandler =
        { value, type ->
            ((value as? ChannelizedReport<*>)
                ?.channel
                ?.split(delimiter)
                ?.let(handler::resolve)
                ?: miscHandler)(value, type)
        }

    fun <T> assumeType(type: KType): (Any?, KType) -> (TypedChannelReport<T>) {
        val reportType = ChannelizedReport::class.withArg(type)
        return { value, type ->
            require(type == reportType) {
                "Expected report type $reportType, was $type"
            }
            @Suppress("UNCHECKED_CAST")
            TypedChannelReport(value as ChannelizedReport<T>, type)
        }
    }

    inline fun <reified T> assumeType(): (Any?, KType) -> (TypedChannelReport<T>) = assumeType(typeOf<T>())

    fun <T> channelHandler(type: KType, block: (TypedChannelReport<T>) -> Unit): ReportHandler {
        val reportType = ChannelizedReport::class.withArg(type)
        return { value, type ->
            require(type == reportType) {
                "Expected report type $reportType, was $type"
            }
            @Suppress("UNCHECKED_CAST")
            block(TypedChannelReport(value as ChannelizedReport<T>, type))
        }
    }

    inline fun <reified T> channelHandler(noinline block: (TypedChannelReport<T>) -> Unit) = channelHandler(typeOf<T>(), block)

    fun <T, S> map(sType: KType, f: (T) -> S): TypedChannelReportProcessor<T, S> {
        val sReportType = ChannelizedReport::class.withArg(sType)
        return { (report, _) ->
            TypedChannelReport(
                ChannelizedReport(report.channel, report.time, f(report.data)),
                sReportType)
        }
    }

    inline fun <T, reified S> map(noinline f: (T) -> S) = map(typeOf<S>(), f)

    fun <T> rename(nameFn: (String) -> String): TypedChannelReportProcessor<T, T> = { (report, type) ->
        TypedChannelReport(report.copy(channel = nameFn(report.channel)), type)
    }

    fun <T> reportAllTo(handler: ReportHandler): (Collection<TypedChannelReport<T>>) -> Unit = {
        it.forEach({ (report, type) -> handler(report, type) })
    }

    fun <T, S> split(sType: KType, splitters: List<Pair<(T) -> S, (String) -> String>>): (TypedChannelReport<T>) -> List<TypedChannelReport<S>> {
        // Build the pipelines in advance. This saves time by not recomputing the reified result type.
        val pipelines = splitters.map { (mapFn, nameFn) -> map(sType, mapFn) andThen rename(nameFn) }
        return { report -> pipelines.map { it(report) } }
    }

    inline fun <T, reified S> split(splitters: List<Pair<(T) -> S, (String) -> String>>) = split(typeOf<S>(), splitters)

    inline fun <T, reified S> split(vararg splitters: Pair<(T) -> S, (String) -> String>) = split(splitters.asList())
}