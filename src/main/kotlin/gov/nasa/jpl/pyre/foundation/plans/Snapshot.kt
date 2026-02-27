package gov.nasa.jpl.pyre.foundation.plans

import gov.nasa.jpl.pyre.kernel.DependentMap
import gov.nasa.jpl.pyre.kernel.KernelTaskSnapshot
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A snapshot of [PlanSimulation] which supports serialization to disk.
 */
@Serializable
data class Snapshot<M>(
    @Serializable(with = InstantSerializer::class)
    val time: Instant,
    val cells: DependentMap,
    val tasks: List<KernelTaskSnapshot>,
    val activities: List<GroundedActivity<M>>
)
