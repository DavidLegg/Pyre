package gov.nasa.jpl.parakeet.general.scheduling

import gov.nasa.jpl.pyre.foundation.incremental.PlanEdits
import gov.nasa.jpl.pyre.foundation.plans.Plan
import gov.nasa.jpl.pyre.general.results.SimulationResults

/**
 * The scope used in [schedule] below to construct a plan with the help of simulation.
 */
interface SchedulingScope<M> {
    val plan: Plan<M>
    val model: M
    val results: SimulationResults

    /**
     * Change [plan] and [results] according to these edits.
     */
    fun edit(edits: PlanEdits<M>)
}
