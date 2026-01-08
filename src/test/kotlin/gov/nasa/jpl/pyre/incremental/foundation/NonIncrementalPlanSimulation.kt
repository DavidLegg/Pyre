package gov.nasa.jpl.pyre.incremental.foundation

import gov.nasa.jpl.pyre.foundation.plans.Plan
import gov.nasa.jpl.pyre.foundation.plans.PlanSimulation
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.general.results.MutableSimulationResults
import gov.nasa.jpl.pyre.general.results.SimulationResults
import gov.nasa.jpl.pyre.general.results.SimulationResultsOperations.reportHandler
import gov.nasa.jpl.pyre.general.results.SimulationResultsOperations.toSimulationResults
import gov.nasa.jpl.pyre.incremental.IncrementalPlanSimulation
import gov.nasa.jpl.pyre.incremental.PlanEdits
import kotlin.reflect.KType

/**
 * Not-actually-incremental implementation of [gov.nasa.jpl.pyre.incremental.IncrementalPlanSimulation].
 * This is the baseline correct behavior for an incremental simulator, without the complexity of actually being incremental.
 */
class NonIncrementalPlanSimulation<M>(
    private val constructModel: context (InitScope) () -> M,
    plan: Plan<M>,
    private val modelClass: KType,
) : IncrementalPlanSimulation<M> {
    override var plan: Plan<M> = plan
        private set
    override var results: SimulationResults

    init {
        results = computeResults(plan)
    }

    override fun run(edits: PlanEdits<M>) {
        plan = edits.applyTo(plan)
        results = computeResults(plan)
    }

    private fun computeResults(plan: Plan<M>): SimulationResults {
        val newResults = MutableSimulationResults(plan.startTime, plan.endTime)
        val simulation = PlanSimulation(
            newResults.reportHandler(),
            plan.startTime,
            constructModel = constructModel,
            modelClass = modelClass
        )
        simulation.runPlan(plan)
        return newResults.toSimulationResults()
    }
}
