package gov.nasa.jpl.pyre.incremental.kernel

import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.PureTaskStep
import kotlin.time.Instant

/**
 * The plan for a simulation, at the "kernel" level, where there are no activities, only tasks.
 */
data class KernelPlan(
    val planStart: Instant,
    val planEnd: Instant,
    val activities: List<KernelActivity>,
)

data class KernelActivity(
    val name: Name,
    val time: Instant,
    val task: PureTaskStep<*>,
)
