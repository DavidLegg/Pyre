package gov.nasa.jpl.pyre.flame.reporting

import gov.nasa.jpl.pyre.coals.Reflection.withArg
import gov.nasa.jpl.pyre.ember.ReportHandler
import gov.nasa.jpl.pyre.spark.reporting.ChannelizedReport
import kotlin.let
import kotlin.reflect.KType
import kotlin.reflect.typeOf

data class TypedChannelReport<T>(val report: ChannelizedReport<T>, val type: KType)

object ReportHandling {
    /**
     * Does nothing with reports sent to it.
     */
    val discardReports: ReportHandler = object: ReportHandler {
        override fun <T> handle(value: T, type: KType) {}
    }

    /**
     * Split reports by channel.
     * Reports on named channels go to their corresponding handlers.
     * All other reports go to miscHandler (discarded by default).
     */
    fun channels(vararg channelHandlers: Pair<String, ReportHandler>, miscHandler: ReportHandler = discardReports): ReportHandler =
        channels(channelHandlers.toMap(), miscHandler)

    fun channels(channelHandlers: Map<String, ReportHandler>, miscHandler: ReportHandler): ReportHandler =
        object : ReportHandler {
            override fun <T> handle(value: T, type: KType) =
                ((value as? ChannelizedReport<*>)
                    ?.channel
                    ?.let { channelHandlers[it] }
                    ?: miscHandler
                        ).handle(value, type)
        }

    // TODO: I want a way to easily define "channel splitting", e.g. splitting a Channel<Vector> into 3 Channel<Double>s...
    // This would better decouple modeling from reporting, as you wouldn't have to bend the modeling to fit the ideal reporting format.

    fun <T> channelHandler(type: KType, block: (TypedChannelReport<T>) -> Unit): ReportHandler {
        val reportType = ChannelizedReport::class.withArg(type)
        return object : ReportHandler {
            override fun <S> handle(value: S, type: KType) {
                require(type == reportType) {
                    "Expected report type $reportType, was $type"
                }
                block(TypedChannelReport(value as ChannelizedReport<T>, type))
            }
        }
    }

    inline fun <reified T> channelHandler(noinline block: TypedChannelReport<T>.() -> Unit) = channelHandler(typeOf<T>(), block)

    fun <T, S> TypedChannelReport<T>.map(sType: KType, f: (T) -> S) = TypedChannelReport(
        ChannelizedReport(report.channel, report.time, f(report.data)), ChannelizedReport::class.withArg(sType))

    inline fun <T, reified S> TypedChannelReport<T>.map(noinline f: (T) -> S) = map(typeOf<S>(), f)

    fun <T> TypedChannelReport<T>.rename(name: String) = TypedChannelReport(report.copy(channel = name), type)
    fun <T> TypedChannelReport<T>.rename(nameFn: (String) -> String) = TypedChannelReport(report.copy(channel = nameFn(report.channel)), type)

    fun <T> TypedChannelReport<T>.reportTo(handler: ReportHandler) = handler.handle(report, type)
    fun <T> Collection<TypedChannelReport<T>>.reportTo(handler: ReportHandler) = forEach { it.reportTo(handler) }

    fun <T, S> TypedChannelReport<T>.split(sType: KType, splitters: List<Pair<(T) -> S, (String) -> String>>): List<TypedChannelReport<S>> =
        splitters.map { (f, name) -> this.map(sType, f).rename(name) }

    inline fun <T, reified S> TypedChannelReport<T>.split(splitters: List<Pair<(T) -> S, (String) -> String>>) = split(typeOf<S>(), splitters)

    inline fun <T, reified S> TypedChannelReport<T>.split(vararg splitters: Pair<(T) -> S, (String) -> String>) = split(splitters.asList())
}