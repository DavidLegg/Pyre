package gov.nasa.jpl.pyre.incremental

import gov.nasa.jpl.pyre.kernel.tasks.KernelTask

data class KernelPlanEdits(
    val removals: List<KernelTask> = emptyList(),
    val additions: List<KernelTask> = emptyList(),
)
