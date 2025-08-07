package gov.nasa.jpl.pyre.flame.plans

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.toPyreDuration
import gov.nasa.jpl.pyre.spark.reporting.Reporting.report
import gov.nasa.jpl.pyre.spark.tasks.ResourceScope.Companion.now
import gov.nasa.jpl.pyre.spark.tasks.TaskScope
import gov.nasa.jpl.pyre.spark.tasks.TaskScope.Companion.delay
import gov.nasa.jpl.pyre.spark.tasks.TaskScope.Companion.spawn
import gov.nasa.jpl.pyre.spark.tasks.task
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

object ActivityActions {
    context (scope: TaskScope)
    suspend fun <M> defer(time: Duration, activity: FloatingActivity<M>, model: M) {
        spawn(activity.name, task {
            delay(time)
            // TODO: Replace the JSON report with a data class
            report("activities", JsonObject(mapOf(
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
        })
    }


    context(scope: TaskScope)
    suspend fun <M> deferUntil(time: Instant, activity: FloatingActivity<M>, model: M) =
        defer((time - now()).toPyreDuration(), activity, model)

    context(scope: TaskScope)
    suspend fun <M> spawn(activity: GroundedActivity<M>, model: M) =
        deferUntil(activity.time, activity.float(), model)

    context(scope: TaskScope)
    suspend fun <M> spawn(activity: FloatingActivity<M>, model: M) =
        defer(Duration.Companion.ZERO, activity, model)

    context(scope: TaskScope)
    suspend fun <M> spawn(activity: Activity<M>, model: M) =
        spawn(FloatingActivity(activity), model)
}
