package gov.nasa.jpl.pyre.foundation.plans

import gov.nasa.jpl.pyre.kernel.DependentMap
import gov.nasa.jpl.pyre.kernel.KernelTaskSnapshot
import gov.nasa.jpl.pyre.kernel.Name
import kotlin.time.Instant

/**
 * A snapshot of [PlanSimulation] which supports serialization to disk.
 */
data class Snapshot<M>(
    val time: Instant,
    val cells: DependentMap<Name>,
    val tasks: List<KernelTaskSnapshot>,
    val activities: List<GroundedActivity<M>>
)
