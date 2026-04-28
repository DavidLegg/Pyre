package gov.nasa.jpl.pyre.foundation.incremental

import gov.nasa.jpl.pyre.foundation.plans.GroundedActivity

data class PlanEdits<M>(
    val additions: List<GroundedActivity<M>> = emptyList(),
    val removals: List<GroundedActivity<M>> = emptyList(),
)
