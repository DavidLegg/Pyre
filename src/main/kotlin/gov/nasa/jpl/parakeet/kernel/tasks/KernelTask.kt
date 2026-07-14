package gov.nasa.jpl.parakeet.kernel.tasks

import gov.nasa.jpl.parakeet.kernel.Name
import kotlin.time.Instant

data class KernelTask(
    val name: Name,
    val time: Instant,
    val step: PureTaskStep,
)
