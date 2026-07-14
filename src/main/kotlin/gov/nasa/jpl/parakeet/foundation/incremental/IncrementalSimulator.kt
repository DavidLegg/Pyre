package gov.nasa.jpl.parakeet.foundation.incremental

import gov.nasa.jpl.pyre.foundation.plans.Checkpoint
import gov.nasa.jpl.pyre.foundation.plans.Plan
import gov.nasa.jpl.pyre.general.results.SimulationResults
import kotlin.time.Instant

interface IncrementalSimulator<M> {
    /**
     * The currently-active plan, the result of the initial plan and all subsequent edits applied via [run]
     */
    val plan: Plan<M>

    /**
     * The results from running [plan]
     */
    val results: SimulationResults

    /**
     * Apply [edits] to [plan] and update [results] to be equivalent to running that plan.
     */
    fun run(edits: PlanEdits<M>)

    /**
     * Save a checkpoint at the given [time]. [time] must be within the time bounds of [plan].
     */
    fun save(time: Instant): Checkpoint<M>
}