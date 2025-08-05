package gov.nasa.jpl.pyre.spark.reporting

import gov.nasa.jpl.pyre.coals.Reflection.withArg
import gov.nasa.jpl.pyre.spark.resources.Dynamics
import gov.nasa.jpl.pyre.spark.resources.Resource
import gov.nasa.jpl.pyre.spark.tasks.now
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope
import gov.nasa.jpl.pyre.spark.tasks.sparkTaskScope
import gov.nasa.jpl.pyre.spark.tasks.task
import gov.nasa.jpl.pyre.spark.tasks.wheneverChanges
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.time.Instant

typealias Channel = String

@Serializable
data class ChannelizedReport<T>(
    val channel: String,
    @Contextual
    val time: Instant,
    val data: T,
)

/**
 * Wraps the simple simulation report function with [ChannelizedReport],
 * categorizing the report on a channel and adding the time of report.
 *
 * Note: reportType must be [ChannelizedReport] with an invariant argument for the type of data.
 * Giving this type directly, instead of constructing it within this function,
 * offers opportunities to improve performance by computing the reified type at init or even compile time.
 */
suspend fun <T> SparkTaskScope.report(channel: Channel, data: T, reportType: KType) {
    report(ChannelizedReport(
        channel,
        now(),
        data,
    ), reportType)
}

/**
 * Wraps the simple simulation report function with [ChannelizedReport],
 * categorizing the report on a channel and adding the time of report.
 */
suspend inline fun <reified T> SparkTaskScope.report(channel: Channel, data: T) = report(channel, data, typeOf<ChannelizedReport<T>>())

/**
 * Register a resource to be reported whenever it changes, using a [ChannelizedReport]
 */
fun <V, D : Dynamics<V, D>> SparkInitContext.register(
    name: String,
    resource: Resource<D>,
    dynamicsType: KType,
) {
    val reportType = ChannelizedReport::class.withArg(dynamicsType)
    val reportedResourceName = "$this/$name"
    onStartup("Report initial value for resource $reportedResourceName") {
        report(reportedResourceName, resource.getDynamics().data, reportType)
    }
    spawn("Report resource $reportedResourceName", wheneverChanges(resource) {
        report(reportedResourceName, resource.getDynamics().data, reportType)
    })
}

/**
 * Register a resource to be reported whenever it changes, using a [ChannelizedReport]
 */
inline fun <V, reified D : Dynamics<V, D>> SparkInitContext.register(name: String, resource: Resource<D>) =
    register(name, resource, typeOf<D>())

/**
 * Register a resource to be reported whenever it changes, using a [ChannelizedReport]
 * Use the resource's own toString method as its name, for use with [gov.nasa.jpl.pyre.spark.resources.named].
 */
inline fun <V, reified D : Dynamics<V, D>> SparkInitContext.register(resource: Resource<D>) =
    register(resource.toString(), resource, typeOf<D>())
