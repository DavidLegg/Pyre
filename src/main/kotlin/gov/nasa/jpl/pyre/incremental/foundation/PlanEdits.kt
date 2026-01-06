package gov.nasa.jpl.pyre.incremental.foundation

import gov.nasa.jpl.pyre.foundation.plans.GroundedActivity
import gov.nasa.jpl.pyre.foundation.plans.Plan
import kotlin.collections.plus

data class PlanEdits<M>(
    val additions: List<GroundedActivity<M>>,
    val removals: List<GroundedActivity<M>>,
) {
    fun applyTo(plan: Plan<M>): Plan<M> =
        plan.copy(activities = plan.activities.filter { it !in removals } + additions)
}
