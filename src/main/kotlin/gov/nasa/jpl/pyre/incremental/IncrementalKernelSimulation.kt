package gov.nasa.jpl.pyre.incremental

interface IncrementalKernelSimulation {
    val plan: KernelPlan

    /**
     * Updates this to [planEdits].applyTo([plan])
     */
    fun run(planEdits: KernelPlanEdits)

    val reports: List<Any?>
}

