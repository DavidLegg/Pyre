package gov.nasa.jpl.pyre.incremental

import gov.nasa.jpl.pyre.foundation.plans.Plan
import gov.nasa.jpl.pyre.general.results.SimulationResults

interface IncrementalPlanSimulation<M> {
    /**
     * The currently-active plan, the result of the initial plan and all subsequent edits applied via [run]
     */
    val plan: Plan<M>

    /**
     * The results from running [plan]
     */
    val results: SimulationResults

    /**
     * Apply [edits] to [plan], and update [results] to be equivalent to running that plan.
     */
    fun run(edits: PlanEdits<M>)
}