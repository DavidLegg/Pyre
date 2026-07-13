package gov.nasa.jpl.pyre.general.scheduling

import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.foundation.plans.GroundedActivity
import gov.nasa.jpl.pyre.foundation.plans.Plan
import gov.nasa.jpl.pyre.examples.scheduling.GroundedActivity
import gov.nasa.jpl.pyre.foundation.resources.Dynamics
import gov.nasa.jpl.pyre.foundation.resources.Resource
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.general.results.ProfileOperations.asResource
import gov.nasa.jpl.pyre.general.results.ProfileOperations.getProfile

object SchedulingOperations {
    fun <M : Any> SchedulingSystem<M>.addActivities(activities: Collection<GroundedActivity<M>>) =
        activities.forEach(::addActivity)

    fun <M : Any> SchedulingSystem<M>.addPlan(plan: Plan<M>) =
        addActivities(plan.activities)

    operator fun <M : Any> SchedulingSystem<M>.plusAssign(activity: Activity<M>) = addActivity(GroundedActivity(time(), activity))
    operator fun <M : Any> SchedulingSystem<M>.plusAssign(activity: GroundedActivity<M>) = addActivity(activity)
    operator fun <M : Any> SchedulingSystem<M>.plusAssign(activities: Collection<GroundedActivity<M>>) = addActivities(activities)
    operator fun <M : Any> SchedulingSystem<M>.plusAssign(plan: Plan<M>) = addPlan(plan)

    // TODO: Build up SchedulingContext, and port profile- and value-access operations to this, using a SchedulingContext param.
    // TODO: Port compute over here using a SchedulingContext param.

    /**
     * Look up the profile for [this] resource in [schedulingScope], then replay it as a resource.
     */
    context (schedulingScope: SchedulingScope<M>, _: InitScope)
    fun <M, D: Dynamics<*, D>> Resource<D>.replay(): Resource<D> =
        schedulingScope.results.getProfile<D>(this.name).asResource()
}