package gov.nasa.jpl.pyre.incremental

import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.PureTaskStep
import kotlin.time.Instant

data class KernelActivity(
    val name: Name,
    val time: Instant,
    val task: PureTaskStep<*>,
)

// TODO: Consider using Instant as the kernel-level time type everywhere and ditching Duration
//   See what, if any, effect this has on performance, esp. compared to using an inline value Duration.
//   I suspect the effect would be negligible, because we so frequently wind up converting to Instant anyways.
