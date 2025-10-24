package gov.nasa.jpl.pyre.general.plans

import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.toPyreDuration
import gov.nasa.jpl.pyre.foundation.reporting.Reporting.report
import gov.nasa.jpl.pyre.foundation.tasks.ResourceScope.Companion.now
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope.Companion.delay
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope.Companion.spawn
import gov.nasa.jpl.pyre.foundation.tasks.task
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.time.Instant

object ActivityActions {
    @Serializable
    data class ActivityEvent(
        val name: String,
        val type: String,
        @Contextual
        val start: Instant,
        @Contextual
        val end: Instant? = null,
        // Report the activity instance itself, but only for in-memory usage.
        // The default serialization drops this extra detail.
        @Transient
        val activity: Activity<*>? = null,
    )

    context (scope: TaskScope)
    suspend fun <M> call(activity: FloatingActivity<M>, model: M) {
        val startTime = now()
        report("activities", ActivityEvent(
            activity.name,
            activity.typeName,
            startTime,
            activity = activity.activity,
        ))
        activity.activity.effectModel(model)
        // Report both start and end time with the activity end event.
        // This avoids needing to generate or persist unique IDs for activities.
        // Instead, any start event which matches all three serialized fields can be paired with this end event;
        // all such start events are exactly equivalent.
        report("activities", ActivityEvent(
            activity.name,
            activity.typeName,
            startTime,
            now(),
            activity = activity.activity,
        ))
    }

    context (scope: TaskScope)
    suspend fun <M> call(activity: Activity<M>, model: M) =
        call(FloatingActivity(activity), model)

    context (scope: TaskScope)
    suspend fun <M> defer(time: Duration, activity: FloatingActivity<M>, model: M) {
        spawn(activity.name, task {
            delay(time)
            call(activity, model)
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
