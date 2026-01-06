package gov.nasa.jpl.pyre.incremental.kernel

import gov.nasa.jpl.pyre.foundation.plans.Plan
import gov.nasa.jpl.pyre.kernel.BasicInitScope
import kotlin.reflect.KType

class GraphIncrementalSimulation(
    constructModel: context (BasicInitScope) () -> Unit,
    kernelPlan: KernelPlan,
    modelClass: KType,
) : IncrementalKernelSimulation {
    override var plan: KernelPlan = KernelPlan(emptyList())
        private set

    override fun run(planEdits: KernelPlanEdits) {
        TODO("Not yet implemented")
    }
}