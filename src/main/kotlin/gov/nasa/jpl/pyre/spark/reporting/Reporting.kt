package gov.nasa.jpl.pyre.spark.reporting

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.InternalLogger
import gov.nasa.jpl.pyre.ember.JsonValue
import gov.nasa.jpl.pyre.ember.JsonValue.JsonMap
import gov.nasa.jpl.pyre.ember.JsonValue.JsonNull
import gov.nasa.jpl.pyre.ember.JsonValue.JsonString
import gov.nasa.jpl.pyre.ember.Serializer
import gov.nasa.jpl.pyre.spark.resources.Dynamics
import gov.nasa.jpl.pyre.spark.resources.Resource
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope
import gov.nasa.jpl.pyre.spark.tasks.sparkTaskScope
import gov.nasa.jpl.pyre.spark.tasks.task
import gov.nasa.jpl.pyre.spark.tasks.wheneverChanges

typealias Channel = String

/**
 * Wraps the simple simulation report function with more structured report,
 * including a channel to report on and a time of the report.
 */
suspend fun SparkTaskScope<*>.report(channel: Channel, data: JsonValue) {
    report(JsonMap(mapOf(
        "channel" to JsonString(channel),
        "time" to Duration.serializer().serialize(simulationClock.getValue()),
        "data" to data,
    )))
}

/**
 * Register a resource to be reported whenever it changes, using the standard resource reporting format.
 *
 * The standard resource reporting format consists of reports on a channel, named the resource's name.
 * The first report is a "channel metadata" report, describing the reports to follow.
 * The channel metadata report indicates that this is a resource channel, and optionally includes model-supplied additional metadata.
 * We suggest information like the resource kind (discrete? linear? clock? etc.) and units as useful metadata.
 *
 * All further reports are merely the serialized resource dynamics.
 */
fun <V, D : Dynamics<V, D>> SparkInitContext.register(
    name: String,
    resource: Resource<D>,
    serializer: Serializer<D>,
    ) {
    spawn("Report initial value for resource $name", task {
        with (sparkTaskScope()) {
            report(name, serializer.serialize(resource.getDynamics().data))
        }
    })
    spawn("Report resource $name", wheneverChanges(resource) {
        report(name, serializer.serialize(resource.getDynamics().data))
    })
}

typealias ReportHandler = (JsonValue) -> Unit
typealias ChannelHandler = (Duration, JsonValue) -> Unit

/**
 * Splits out reports by channel, for all reports in standard channel format.
 */
class ChannelizedReportHandler(
    private val createChannelHandler: (String) -> ChannelHandler,
    private val unchannelizedReportHandler: ReportHandler
): ReportHandler {
    private val channelHandlers: MutableMap<String, ChannelHandler> = mutableMapOf()
    override fun invoke(p1: JsonValue) {
        val channel = ((p1 as? JsonMap)?.values["channel"] as? JsonString)?.value
        val time = (p1 as? JsonMap)?.values["time"]?.let(Duration.serializer()::deserialize)
        if (channel != null && time != null) {
            channelHandlers.computeIfAbsent(channel, createChannelHandler)(time, p1.values["data"] ?: JsonNull)
        } else {
            unchannelizedReportHandler(p1)
        }
    }
}
