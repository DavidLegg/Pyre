package gov.nasa.jpl.pyre.kernel

import gov.nasa.jpl.pyre.foundation.plans.InstantSerializer
import gov.nasa.jpl.pyre.kernel.tasks.TaskHistory
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A snapshot of the state of [KernelSimulator] which supports serialization to disk.
 */
@Serializable
data class KernelSnapshot(
    @Serializable(with = InstantSerializer::class)
    val time: Instant,
    val cells: DependentMap,
    val tasks: List<KernelTaskSnapshot>,
)

@Serializable
data class KernelTaskSnapshot(
    /** The name of this task */
    val name: Name,
    /** The name of the root task from which this task is descended. */
    val root: Name = name,
    /** The time when this task is scheduled to resume, or null if this task is complete. */
    @Serializable(with = InstantSerializer::class)
    val time: Instant? = null,
    /** The steps taken by this task, or null if this task is complete. */
    val history: TaskHistory? = null,
)
