package gov.nasa.jpl.pyre.flame.plans

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope

/**
 * Base unit of planned simulation behavior.
 */
interface Activity<M, R> {
    context (SparkTaskScope<R>)
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
