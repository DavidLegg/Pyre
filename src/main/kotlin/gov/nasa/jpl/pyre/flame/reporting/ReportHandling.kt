package gov.nasa.jpl.pyre.flame.reporting

import gov.nasa.jpl.pyre.coals.Reflection.withArg
import gov.nasa.jpl.pyre.coals.andThen
import gov.nasa.jpl.pyre.ember.ReportHandler
import gov.nasa.jpl.pyre.spark.reporting.ChannelizedReport
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.serializer
import java.io.OutputStream
import kotlin.let
import kotlin.reflect.KType
import kotlin.reflect.typeOf

data class TypedChannelReport<T>(val report: ChannelizedReport<T>, val type: KType)
typealias TypedChannelReportProcessor<T, S> = (TypedChannelReport<T>) -> TypedChannelReport<S>

object ReportHandling {
    /**
     * Does nothing with reports sent to it.
     */
    val discardReports: ReportHandler = { value, type -> }

    /**
     * Writes reports as JSON directly to an output stream.
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun streamReportHandler(stream: OutputStream = System.out, jsonFormat: Json = Json): ReportHandler = { value, type ->
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

    // TODO: I want a way to easily define "channel splitting", e.g. splitting a Channel<Vector> into 3 Channel<Double>s...
    // This would better decouple modeling from reporting, as you wouldn't have to bend the modeling to fit the ideal reporting format.

    fun <T> channelHandler(type: KType, block: (TypedChannelReport<T>) -> Unit): ReportHandler {
        val reportType = ChannelizedReport::class.withArg(type)
        return { value, type ->
            require(type == reportType) {
                "Expected report type $reportType, was $type"
            }
            block(TypedChannelReport(value as ChannelizedReport<T>, type))
        }
    }

    inline fun <reified T> channelHandler(noinline block: (TypedChannelReport<T>) -> Unit) = channelHandler(typeOf<T>(), block)

    fun <T, S> map(sType: KType, f: (T) -> S): TypedChannelReportProcessor<T, S> {
        val sReportType = ChannelizedReport::class.withArg(sType)
        return {
            TypedChannelReport(
                ChannelizedReport(it.report.channel, it.report.time, f(it.report.data)),
                sReportType)
        }
    }

    inline fun <T, reified S> map(noinline f: (T) -> S) = map(typeOf<S>(), f)

    fun <T> rename(name: String): TypedChannelReportProcessor<T, T> = {
        TypedChannelReport(it.report.copy(channel = name), it.type)
    }
    fun <T> rename(nameFn: (String) -> String): TypedChannelReportProcessor<T, T> = {
        TypedChannelReport(it.report.copy(channel = nameFn(it.report.channel)), it.type)
    }

    fun <T> reportTo(handler: ReportHandler): (TypedChannelReport<T>) -> Unit = {
        handler(it.report, it.type)
    }
    fun <T> reportAllTo(handler: ReportHandler): (Collection<TypedChannelReport<T>>) -> Unit = {
        it.forEach(reportTo(handler))
    }

    fun <T, S> split(sType: KType, splitters: List<Pair<(T) -> S, (String) -> String>>): (TypedChannelReport<T>) -> List<TypedChannelReport<S>> {
        // Build the pipelines in advance. This saves time by not recomputing the reified result type.
        val pipelines = splitters.map { (mapFn, nameFn) -> map(sType, mapFn) andThen rename(nameFn) }
        return { report -> pipelines.map { it(report) } }
    }

    inline fun <T, reified S> split(splitters: List<Pair<(T) -> S, (String) -> String>>) = split(typeOf<S>(), splitters)

    inline fun <T, reified S> split(vararg splitters: Pair<(T) -> S, (String) -> String>) = split(splitters.asList())
}