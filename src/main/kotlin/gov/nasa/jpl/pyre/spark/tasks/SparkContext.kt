package gov.nasa.jpl.pyre.spark.tasks

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.JsonValue
import gov.nasa.jpl.pyre.ember.JsonValue.*
import gov.nasa.jpl.pyre.ember.Serializer
import gov.nasa.jpl.pyre.ember.SimulationState.SimulationInitContext
import gov.nasa.jpl.pyre.spark.resources.Dynamics
import gov.nasa.jpl.pyre.spark.resources.Resource
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.resources.timer.Timer

/**
 * A context for all the "global" conveniences offered by Spark during simulation.
 */
interface SparkContext {
    val SIMULATION_CLOCK: Resource<Timer>
}

interface SparkInitContext : SparkContext, SimulationInitContext
interface SparkTaskScope<T> : SparkContext, TaskScope<T>

context (SparkContext, TaskScope<T>)
fun <T> sparkContext(): SparkTaskScope<T> =
    object : SparkTaskScope<T>, SparkContext by this@SparkContext, TaskScope<T> by this@TaskScope {}

/**
 * Wraps the simple simulation report function with more structured report,
 * including a channel to report on and a time of the report.
 */
suspend fun SparkTaskScope<*>.report(channel: String, data: JsonValue) {
    report(JsonMap(mapOf(
        "channel" to JsonString(channel),
        "time" to Duration.serializer().serialize(SIMULATION_CLOCK.getValue()),
        "data" to data,
    )))
}

fun <V, D : Dynamics<V, D>> SparkInitContext.register(
    name: String,
    resource: Resource<D>,
    serializer: Serializer<D>) {
    spawn("Report initial value for resource $name", task {
        sparkContext().report(name, serializer.serialize(resource.getDynamics().data))
    })
    spawn("Report resource $name", wheneverChanges(resource) {
        sparkContext().report(name, serializer.serialize(resource.getDynamics().data))
    })
}
