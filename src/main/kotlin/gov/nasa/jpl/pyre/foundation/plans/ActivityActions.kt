package gov.nasa.jpl.pyre.foundation.plans

import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.subContext
import gov.nasa.jpl.pyre.foundation.tasks.ReportScope.Companion.report
import gov.nasa.jpl.pyre.foundation.tasks.ResourceScope.Companion.now
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope.Companion.subSimulationScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskOperations.delay
import gov.nasa.jpl.pyre.foundation.tasks.TaskOperations.delayUntil
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope.Companion.spawn
import gov.nasa.jpl.pyre.foundation.tasks.task
import gov.nasa.jpl.pyre.kernel.Name
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.time.Duration
import kotlin.time.Instant

object ActivityActions {
    @Serializable
    data class ActivityEvent(
        val name: Name,
        val type: String,
        @Serializable(with = InstantSerializer::class)
        val start: Instant,
        @Serializable(with = InstantSerializer::class)
        val end: Instant? = null,
        // Report the activity instance itself, but only for in-memory usage.
        // The default serialization drops this extra detail.
        @Transient
        val activity: Activity<*>? = null,
    ) {
        override fun toString(): String =
            "ActivityEvent(name='$name', type='$type', start=$start, end=$end, activity=${if (activity == null) "null" else "not null"})"
    }

    context (scope: TaskScope)
    suspend fun <M> call(activity: FloatingActivity<M>, model: M) {
        val startTime = now()
        scope.activities.report(ActivityEvent(
            activity.name,
            activity.activity::class.simpleName!!,
            startTime,
            activity = activity.activity,
        ))
        // TODO: Wrap this in a block to add activity to the context name, somehow.
        activity.activity.effectModel(model)
        // Report both start and end time with the activity end event.
        // This avoids needing to generate or persist unique IDs for activities.
        // Instead, any start event which matches all three serialized fields can be paired with this end event;
        // all such start events are exactly equivalent.
        scope.activities.report(ActivityEvent(
            activity.name,
            activity.activity::class.simpleName!!,
            startTime,
            now(),
            activity = activity.activity,
        ))
    }

    context (scope: TaskScope)
    suspend fun <M> call(activity: Activity<M>, model: M) =
        call(FloatingActivity(Name(requireNotNull(activity::class.simpleName)), activity), model)

    context (scope: TaskScope)
    suspend fun <M> defer(time: Duration, activity: FloatingActivity<M>, model: M) =
        deferUntil(now() + time, activity, model)

    context (scope: TaskScope)
    suspend fun <M> deferUntil(time: Instant, activity: FloatingActivity<M>, model: M) {
        spawn(activity.name, task {
            delayUntil(time)
            call(activity, model)
        })
    }

    context(scope: TaskScope)
    suspend fun <M> spawn(activity: GroundedActivity<M>, model: M) =
        deferUntil(activity.time, activity.float(), model)

    context(scope: TaskScope)
    suspend fun <M> spawn(activity: FloatingActivity<M>, model: M) =
        defer(Duration.ZERO, activity, model)

    context(scope: TaskScope)
    suspend fun <M> spawn(activity: Activity<M>, model: M) =
        spawn(FloatingActivity(Name(requireNotNull(activity::class.simpleName)), activity), model)
}
