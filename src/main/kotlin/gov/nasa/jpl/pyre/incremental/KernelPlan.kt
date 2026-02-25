package gov.nasa.jpl.pyre.incremental

import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.tasks.PureTaskStep
import kotlin.time.Instant

// TODO: Ditch this class, and use ActivityTask instead?
//   Somehow I just want the incremental simulator to conform to a similar interface as the single-shot simulator
data class KernelActivity(
    val name: Name,
    val time: Instant,
    val task: PureTaskStep,
)
