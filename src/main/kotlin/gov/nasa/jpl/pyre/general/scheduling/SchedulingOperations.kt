package gov.nasa.jpl.pyre.general.scheduling

import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.foundation.plans.GroundedActivity
import gov.nasa.jpl.pyre.foundation.plans.Plan
import gov.nasa.jpl.pyre.examples.scheduling.GroundedActivity

object SchedulingOperations {
    fun <M : Any, C> SchedulingSystem<M, C>.addActivities(activities: Collection<GroundedActivity<M>>) =
        activities.forEach(::addActivity)

    fun <M : Any, C> SchedulingSystem<M, C>.addPlan(plan: Plan<M>) =
        addActivities(plan.activities)

    operator fun <M : Any, C> SchedulingSystem<M, C>.plusAssign(activity: Activity<M>) = addActivity(GroundedActivity(time(), activity))
    operator fun <M : Any, C> SchedulingSystem<M, C>.plusAssign(activity: GroundedActivity<M>) = addActivity(activity)
    operator fun <M : Any, C> SchedulingSystem<M, C>.plusAssign(activities: Collection<GroundedActivity<M>>) = addActivities(activities)
    operator fun <M : Any, C> SchedulingSystem<M, C>.plusAssign(plan: Plan<M>) = addPlan(plan)

    // TODO: Build up SchedulingContext, and port profile- and value-access operations to this, using a SchedulingContext param.
    // TODO: Port compute over here using a SchedulingContext param.
}