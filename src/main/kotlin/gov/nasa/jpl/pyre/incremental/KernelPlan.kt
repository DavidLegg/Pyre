package gov.nasa.jpl.pyre.incremental

import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.PureTaskStep

/**
 * The plan for a simulation, at the "kernel" level, where there are no activities, only tasks.
 */
data class KernelPlan(
    val activities: List<KernelActivity>,
)

data class KernelActivity(
    val name: Name,
    val time: Duration,
    val task: PureTaskStep<*>,
)
