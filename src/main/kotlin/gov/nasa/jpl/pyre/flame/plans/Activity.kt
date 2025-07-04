package gov.nasa.jpl.pyre.flame.plans

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.minus
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope
import gov.nasa.jpl.pyre.spark.reporting.report
import gov.nasa.jpl.pyre.spark.tasks.sparkTaskScope
import gov.nasa.jpl.pyre.spark.tasks.task
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Base unit of planned simulation behavior.
 */
interface Activity<M, R> {
    context (scope: SparkTaskScope<R>)
    suspend fun effectModel(model: M): R
}

/**
 * An activity, with all supplemental information attached, except for the start time.
 * This activity "floats" in time until it is grounded by choosing a start time.
 */
data class FloatingActivity<M, R>(
    val activity: Activity<M, R>,
    val typeName: String = activity::class.simpleName ?: throw IllegalArgumentException("Activity must have a typeName"),
    val name: String = typeName,
)

/**
 * An activity with all supplemental information attached, including the start time.
 */
data class GroundedActivity<M, R>(
    val time: Duration,
    val activity: Activity<M, R>,
    val typeName: String = activity::class.simpleName ?: throw IllegalArgumentException("Activity must have a typeName"),
    val name: String = typeName,
)

fun <M, R> GroundedActivity<M, R>.float() = FloatingActivity(activity, typeName, name)
fun <M, R> FloatingActivity<M, R>.ground(time: Duration) = GroundedActivity(time, activity, typeName, name)

suspend fun <M, R> SparkTaskScope<*>.defer(time: Duration, activity: FloatingActivity<M, R>, model: M) {
    spawn(activity.name, task {
        with(sparkTaskScope()) {
            delay(time)
            report(
                "activities", JsonObject(mapOf(
                    "name" to JsonPrimitive(activity.name),
                    "type" to JsonPrimitive(activity.typeName),
                    "event" to JsonPrimitive("start")
                ))
            )
            val result = activity.activity.effectModel(model)
            report(
                "activities", JsonObject(mapOf(
                    "name" to JsonPrimitive(activity.name),
                    "type" to JsonPrimitive(activity.typeName),
                    "event" to JsonPrimitive("end")
                ))
            )
            result
        }
    })
}

suspend fun <M, R> SparkTaskScope<*>.deferUntil(time: Duration, activity: FloatingActivity<M, R>, model: M) =
    defer(time - simulationClock.getValue(), activity, model)

suspend fun <M, R> SparkTaskScope<*>.spawn(activity: GroundedActivity<M, R>, model: M) =
    deferUntil(activity.time, activity.float(), model)

suspend fun <M, R> SparkTaskScope<*>.spawn(activity: FloatingActivity<M, R>, model: M) =
    defer(Duration.Companion.ZERO, activity, model)

suspend fun <M, R> SparkTaskScope<*>.spawn(activity: Activity<M, R>, model: M) =
    spawn(FloatingActivity(activity), model)