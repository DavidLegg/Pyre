package gov.nasa.jpl.parakeet.foundation.incremental

import gov.nasa.jpl.pyre.foundation.plans.GroundedActivity

// TODO - there's a latent bug somehow in the fuzz testing code
//   where it builds a PlanEdits with an addition/removal pair that haven't cancelled out.
//   Consider refactoring this class to prevent that using the private ctor/factory methods pattern
//   to do minimal necessary checks that no edits are redundant
data class PlanEdits<M>(
    val additions: List<GroundedActivity<M>> = emptyList(),
    val removals: List<GroundedActivity<M>> = emptyList(),
)
