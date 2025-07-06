package gov.nasa.jpl.pyre.flame.plans

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.minus
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope
import gov.nasa.jpl.pyre.spark.reporting.report
import gov.nasa.jpl.pyre.spark.tasks.sparkTaskScope
import gov.nasa.jpl.pyre.spark.tasks.task
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Base unit of planned simulation behavior.
 */
interface Activity<M> {
    context (scope: SparkTaskScope<Unit>)
    suspend fun effectModel(model: M)
}

/**
 * An activity, with all supplemental information attached, except for the start time.
 * This activity "floats" in time until it is grounded by choosing a start time.
 */
@Serializable
data class FloatingActivity<M>(
    val activity: Activity<M>,
    val typeName: String = activity::class.simpleName ?: throw IllegalArgumentException("Activity must have a typeName"),
    val name: String = typeName,
)

/**
 * An activity with all supplemental information attached, including the start time.
 */
@Serializable
data class GroundedActivity<M>(
    val time: Duration,
    val activity: Activity<M>,
    val typeName: String = activity::class.simpleName ?: throw IllegalArgumentException("Activity must have a typeName"),
    val name: String = typeName,
)

fun <M> GroundedActivity<M>.float() = FloatingActivity(activity, typeName, name)
fun <M> FloatingActivity<M>.ground(time: Duration) = GroundedActivity(time, activity, typeName, name)

suspend fun <M> SparkTaskScope<*>.defer(time: Duration, activity: FloatingActivity<M>, model: M) {
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

suspend fun <M> SparkTaskScope<*>.deferUntil(time: Duration, activity: FloatingActivity<M>, model: M) =
    defer(time - simulationClock.getValue(), activity, model)

suspend fun <M> SparkTaskScope<*>.spawn(activity: GroundedActivity<M>, model: M) =
    deferUntil(activity.time, activity.float(), model)

suspend fun <M> SparkTaskScope<*>.spawn(activity: FloatingActivity<M>, model: M) =
    defer(Duration.Companion.ZERO, activity, model)

suspend fun <M> SparkTaskScope<*>.spawn(activity: Activity<M>, model: M) =
    spawn(FloatingActivity(activity), model)