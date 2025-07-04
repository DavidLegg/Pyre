package gov.nasa.jpl.pyre.spark.reporting

import gov.nasa.jpl.pyre.ember.toKotlinDuration
import gov.nasa.jpl.pyre.spark.resources.Dynamics
import gov.nasa.jpl.pyre.spark.resources.Resource
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope
import gov.nasa.jpl.pyre.spark.tasks.sparkTaskScope
import gov.nasa.jpl.pyre.spark.tasks.task
import gov.nasa.jpl.pyre.spark.tasks.wheneverChanges
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import kotlin.time.Instant

typealias Channel = String

/**
 * Wraps the simple simulation report function with more structured report,
 * including a channel to report on and a time of the report.
 */
suspend fun SparkTaskScope<*>.report(channel: Channel, data: JsonElement) {
    report(JsonObject(mapOf(
        "channel" to JsonPrimitive(channel),
        "time" to JsonPrimitive((simulationEpoch + simulationClock.getValue().toKotlinDuration()).toString()),
        "data" to data,
    )))
}

/**
 * Register a resource to be reported whenever it changes, using the standard resource reporting format.
 */
inline fun <V, reified D : Dynamics<V, D>> SparkInitContext.register(
    name: String,
    resource: Resource<D>,
    serializer: KSerializer<D> = serializer(),
    ) {
    spawn("Report initial value for resource $name", task {
        with (sparkTaskScope()) {
            report(name, Json.encodeToJsonElement(resource.getDynamics().data))
        }
    })
    spawn("Report resource $name", wheneverChanges(resource) {
        report(name, Json.encodeToJsonElement(resource.getDynamics().data))
    })
}

typealias ReportHandler = (JsonElement) -> Unit
typealias ChannelHandler = (Instant, JsonElement) -> Unit

/**
 * Splits out reports by channel, for all reports in standard channel format.
 */
class ChannelizedReportHandler(
    private val createChannelHandler: (String) -> ChannelHandler,
    private val unchannelizedReportHandler: ReportHandler
): ReportHandler {
    private val channelHandlers: MutableMap<String, ChannelHandler> = mutableMapOf()
    override fun invoke(p1: JsonElement) {
        val channel = p1.jsonObject["channel"]?.jsonPrimitive?.content
        val time = p1.jsonObject["time"]?.jsonPrimitive?.content?.let(Instant::parse)
        if (channel != null && time != null) {
            channelHandlers.computeIfAbsent(channel, createChannelHandler)(time, p1.jsonObject["data"] ?: JsonNull)
        } else {
            unchannelizedReportHandler(p1)
        }
    }
}
