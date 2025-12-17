package gov.nasa.jpl.pyre.general.reporting

import gov.nasa.jpl.pyre.utilities.Reflection.withArg
import gov.nasa.jpl.pyre.utilities.andThen
import gov.nasa.jpl.pyre.kernel.ReportHandler
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport.ChannelData
import gov.nasa.jpl.pyre.kernel.Name
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.serializer
import java.io.OutputStream
import kotlin.let
import kotlin.reflect.KType
import kotlin.reflect.typeOf

typealias TypedChannelReport<T> = Pair<ChannelData<T>, KType>
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
    fun channels(vararg channelHandlers: Pair<Name, ReportHandler>, miscHandler: ReportHandler = discardReports): ReportHandler =
        channels(channelHandlers.toMap(), miscHandler)

    fun channels(channelHandlers: Map<Name, ReportHandler>, miscHandler: ReportHandler): ReportHandler = {
        value, type ->
        ((value as? ChannelData<*>)
            ?.channel
            ?.let { channelHandlers[it] }
            ?: miscHandler
                )(value, type)
    }

    fun <T> assumeType(type: KType): (Any?, KType) -> (TypedChannelReport<T>) {
        val reportType = ChannelData::class.withArg(type)
        return { value, type ->
            require(type == reportType) {
                "Expected report type $reportType, was $type"
            }
            @Suppress("UNCHECKED_CAST")
            TypedChannelReport(value as ChannelData<T>, type)
        }
    }

    inline fun <reified T> assumeType(): (Any?, KType) -> (TypedChannelReport<T>) = assumeType(typeOf<T>())

    fun <T, S> map(sType: KType, f: (T) -> S): TypedChannelReportProcessor<T, S> {
        val sReportType = ChannelData::class.withArg(sType)
        return { (report, _) ->
            TypedChannelReport(
                ChannelData(report.channel, report.time, f(report.data)),
                sReportType)
        }
    }

    inline fun <T, reified S> map(noinline f: (T) -> S) = map(typeOf<S>(), f)

    fun <T> rename(nameFn: (Name) -> Name): TypedChannelReportProcessor<T, T> = { (report, type) ->
        TypedChannelReport(report.copy(channel = nameFn(report.channel)), type)
    }

    fun <T> reportAllTo(handler: ReportHandler): (Collection<TypedChannelReport<T>>) -> Unit = {
        it.forEach({ (report, type) -> handler(report, type) })
    }

    fun <T, S> split(sType: KType, splitters: List<Pair<(T) -> S, (Name) -> Name>>): (TypedChannelReport<T>) -> List<TypedChannelReport<S>> {
        // Build the pipelines in advance. This saves time by not recomputing the reified result type.
        val pipelines = splitters.map { (mapFn, nameFn) -> map(sType, mapFn) andThen rename(nameFn) }
        return { report -> pipelines.map { it(report) } }
    }

    inline fun <T, reified S> split(splitters: List<Pair<(T) -> S, (Name) -> Name>>) = split(typeOf<S>(), splitters)

    inline fun <T, reified S> split(vararg splitters: Pair<(T) -> S, (Name) -> Name>) = split(splitters.asList())
}