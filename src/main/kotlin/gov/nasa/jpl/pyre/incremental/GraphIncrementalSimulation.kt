package gov.nasa.jpl.pyre.incremental

import gov.nasa.jpl.pyre.general.results.MutableSimulationResults
import gov.nasa.jpl.pyre.general.results.SimulationResults
import gov.nasa.jpl.pyre.general.results.SimulationResultsOperations.toSimulationResults

class GraphIncrementalSimulation : IncrementalKernelSimulation {
    override var plan: KernelPlan = KernelPlan(emptyList())
        private set

    override fun run(planEdits: KernelPlanEdits) {
        TODO("Not yet implemented")
    }

    override val reports: List<Any?>
        get() = TODO("Not yet implemented")
}