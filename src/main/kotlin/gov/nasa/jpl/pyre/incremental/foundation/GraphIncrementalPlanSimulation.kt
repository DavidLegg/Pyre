package gov.nasa.jpl.pyre.incremental.foundation

import gov.nasa.jpl.pyre.foundation.plans.Plan
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.general.results.SimulationResults
import kotlin.reflect.KType

/**
 * Implements [IncrementalPlanSimulation] using an in-memory directed acyclic graph of the events that took place.
 */
class GraphIncrementalPlanSimulation<M>(
    constructModel: context (InitScope) () -> M,
    plan: Plan<M>,
    modelClass: KType,
) : IncrementalPlanSimulation<M> {
    override var plan: Plan<M> = plan
        private set
    override val results: SimulationResults get() = TODO()

    override fun run(edits: PlanEdits<M>) {
        TODO("Not yet implemented")
    }
}