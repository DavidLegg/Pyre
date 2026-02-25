package gov.nasa.jpl.pyre.kernel

import gov.nasa.jpl.pyre.kernel.tasks.TaskHistory
import kotlin.time.Instant

/**
 * A snapshot of the state of [KernelSimulator] which supports serialization to disk.
 */
data class KernelSnapshot(
    val time: Instant,
    val cells: DependentMap<Name>,
    val tasks: List<KernelTaskSnapshot>,
)

data class KernelTaskSnapshot(
    val time: Instant,
    val name: Name,
    val root: Name,
    val history: TaskHistory,
)

data class TaskSnapshot(
    val name: Name,
    val root: Name,
    val history: TaskHistory,
)
