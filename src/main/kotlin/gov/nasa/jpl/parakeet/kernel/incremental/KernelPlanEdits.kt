package gov.nasa.jpl.parakeet.kernel.incremental

import gov.nasa.jpl.parakeet.kernel.tasks.KernelTask

data class KernelPlanEdits(
    val removals: List<KernelTask> = emptyList(),
    val additions: List<KernelTask> = emptyList(),
)
