package gov.nasa.jpl.pyre.incremental.kernel

class GraphIncrementalSimulation : IncrementalKernelSimulation {
    override var plan: KernelPlan = KernelPlan(emptyList())
        private set

    override fun run(planEdits: KernelPlanEdits) {
        TODO("Not yet implemented")
    }

    override val reports: List<Any?>
        get() = TODO("Not yet implemented")
}