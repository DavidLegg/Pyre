package gov.nasa.jpl.pyre.spark.tasks

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.JsonValue
import gov.nasa.jpl.pyre.ember.JsonValue.*
import gov.nasa.jpl.pyre.ember.Serializer
import gov.nasa.jpl.pyre.ember.SimulationState.SimulationInitContext
import gov.nasa.jpl.pyre.ember.minus
import gov.nasa.jpl.pyre.spark.resources.Dynamics
import gov.nasa.jpl.pyre.spark.resources.Resource
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.resources.timer.Timer

/**
 * A context for all the "global" conveniences offered by Spark during simulation.
 */
interface SparkContext {
    val simulationClock: Resource<Timer>
}

interface SparkInitContext : SparkContext, SimulationInitContext
interface SparkTaskScope<T> : SparkContext, TaskScope<T>

context (SparkContext, TaskScope<T>)
fun <T> sparkTaskScope(): SparkTaskScope<T> =
    object : SparkTaskScope<T>, SparkContext by this@SparkContext, TaskScope<T> by this@TaskScope {}

/**
 * Delay until the given absolute simulation time, measured against [SparkTaskScope.simulationClock]
 */
suspend fun SparkTaskScope<*>.delayUntil(time: Duration) = delay(time - simulationClock.getValue())
