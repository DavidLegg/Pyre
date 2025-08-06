package gov.nasa.jpl.pyre.flame.plans

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.toPyreDuration
import gov.nasa.jpl.pyre.flame.plans.ActivityActions.deferUntil
import gov.nasa.jpl.pyre.flame.plans.ActivityActions.spawn
import gov.nasa.jpl.pyre.spark.tasks.TaskScope
import gov.nasa.jpl.pyre.spark.tasks.now
import kotlin.time.Instant

object ActivityActions {
    suspend fun <M> TaskScope.deferUntil(time: Instant, activity: FloatingActivity<M>, model: M) =
        defer((time - now()).toPyreDuration(), activity, model)

    suspend fun <M> TaskScope.spawn(activity: GroundedActivity<M>, model: M) =
        deferUntil(activity.time, activity.float(), model)

    suspend fun <M> TaskScope.spawn(activity: FloatingActivity<M>, model: M) =
        defer(Duration.Companion.ZERO, activity, model)

    suspend fun <M> TaskScope.spawn(activity: Activity<M>, model: M) =
        spawn(FloatingActivity(activity), model)
}

object ActivityActionsByContext {
    context(scope: TaskScope)
    suspend fun <M> deferUntil(time: Instant, activity: FloatingActivity<M>, model: M) = scope.deferUntil(time, activity, model)

    context(scope: TaskScope)
    suspend fun <M> spawn(activity: GroundedActivity<M>, model: M) = scope.spawn(activity, model)

    context(scope: TaskScope)
    suspend fun <M> spawn(activity: FloatingActivity<M>, model: M) = scope.spawn(activity, model)

    context(scope: TaskScope)
    suspend fun <M> spawn(activity: Activity<M>, model: M) = scope.spawn(activity, model)
}
