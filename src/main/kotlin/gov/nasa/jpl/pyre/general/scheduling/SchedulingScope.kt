package gov.nasa.jpl.pyre.general.scheduling

import gov.nasa.jpl.pyre.foundation.incremental.PlanEdits
import gov.nasa.jpl.pyre.foundation.plans.GroundedActivity
import gov.nasa.jpl.pyre.foundation.plans.Plan
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.general.results.SimulationResults
import kotlin.time.Instant

/**
 * The scope used in [schedule] below to construct a plan with the help of simulation.
 */
interface SchedulingScope<M> {
    val planStart: Instant
    val planEnd: Instant
    val model: M
    val results: SimulationResults
    // TODO: Should we have an "isStale" type flag to indicate if the results are out-of-sync with the activities?
    //   Or, should calling "edit" just immediately re-simulate?
    //   Doing the latter would simplify the interface, and we could have a second interface
    //   that specializes this one, with the added ability to "cache" edits together.
    //   This would also play nicely with incremental simulation.
    val activities: List<GroundedActivity<M>>

    /** Change the plan according to these edits. */
    fun edit(edits: PlanEdits<M>)
}

// Let's think through use cases.
// I basically want something that works a bit like APGen/Blackbird style "global only" scheduling systems.
// I want a plan I can modify at will.
// I want the ability to simulate that plan in full, with a simple no-args "simulate()" function.
// I want the ability to access sim results from anywhere, and to do so through the model resources in a type-safe way.

// The requirement to "just simulate" means we need to know the end time...
// This is a little different from the scheduling system use case, where we're more "open-ended" about our time window...
// In fact, this seems to suggest more of a function to run than an object to build.

fun <M> schedule(
    timeRange: ClosedRange<Instant>,
    constructModel: context (InitScope) () -> M,
    block: context (SchedulingScope<M>) () -> Unit
): Plan<M> {
    val activities = mutableListOf<GroundedActivity<M>>()
    block(object : SchedulingScope<M> {
    })
    return Plan(timeRange.start, timeRange.endInclusive, activities)
}
