package gov.nasa.jpl.pyre.incremental

data class KernelPlanEdits(
    val removals: List<KernelActivity> = emptyList(),
    val additions: List<KernelActivity> = emptyList(),
)
