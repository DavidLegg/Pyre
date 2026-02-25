package gov.nasa.jpl.pyre.foundation.plans

import gov.nasa.jpl.pyre.kernel.DependentMap
import gov.nasa.jpl.pyre.kernel.GroundedKernelTaskSnapshot
import kotlin.time.Instant

/**
 * A snapshot of [PlanSimulation] which supports serialization to disk.
 */
data class Snapshot<M>(
    val time: Instant,
    val cells: DependentMap,
    val tasks: List<GroundedKernelTaskSnapshot>,
    val activities: List<GroundedActivity<M>>
)
