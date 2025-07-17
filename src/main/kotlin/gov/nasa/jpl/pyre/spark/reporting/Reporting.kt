package gov.nasa.jpl.pyre.spark.reporting

import gov.nasa.jpl.pyre.ember.toKotlinDuration
import gov.nasa.jpl.pyre.spark.resources.Dynamics
import gov.nasa.jpl.pyre.spark.resources.Resource
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope
import gov.nasa.jpl.pyre.spark.tasks.TaskScope.Companion.report
import gov.nasa.jpl.pyre.spark.tasks.sparkTaskScope
import gov.nasa.jpl.pyre.spark.tasks.task
import gov.nasa.jpl.pyre.spark.tasks.wheneverChanges
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlin.time.Instant

typealias Channel = String

data class ChannelizedReport<T>(
    val channel: String,
    val time: Instant,
    val data: T,
)

/**
 * Wraps the simple simulation report function with more structured report,
 * including a channel to report on and a time of the report.
 */
suspend fun <T> SparkTaskScope<*>.report(channel: Channel, data: T) {
    report(ChannelizedReport(
        channel,
        simulationEpoch + simulationClock.getValue().toKotlinDuration(),
        data,
    ))
}

/**
 * Register a resource to be reported whenever it changes, using the standard resource reporting format.
 */
inline fun <V, reified D : Dynamics<V, D>> SparkInitContext.register(
    name: String,
    resource: Resource<D>,
    ) {
    spawn("Report initial value for resource $name", task {
        with (sparkTaskScope()) {
            report(name, resource.getDynamics().data)
        }
    })
    spawn("Report resource $name", wheneverChanges(resource) {
        report(name, resource.getDynamics().data)
    })
}
