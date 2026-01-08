package gov.nasa.jpl.pyre.incremental

import gov.nasa.jpl.pyre.foundation.plans.GroundedActivity
import gov.nasa.jpl.pyre.foundation.plans.Plan
import kotlin.collections.plus

data class PlanEdits<M>(
    val additions: List<GroundedActivity<M>> = emptyList(),
    val removals: List<GroundedActivity<M>> = emptyList(),
) {
    fun applyTo(plan: Plan<M>): Plan<M> =
        plan.copy(activities = plan.activities.filter { it !in removals } + additions)
}
