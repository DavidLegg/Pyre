package gov.nasa.jpl.pyre.general.scheduling

import gov.nasa.jpl.pyre.foundation.Simulator
import gov.nasa.jpl.pyre.foundation.incremental.IncrementalSimulatorOperations.applyTo
import gov.nasa.jpl.pyre.foundation.incremental.PlanEdits
import gov.nasa.jpl.pyre.foundation.plans.Checkpoint
import gov.nasa.jpl.pyre.foundation.plans.Plan
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.general.results.MutableSimulationResults
import gov.nasa.jpl.pyre.general.results.SimulationResults
import gov.nasa.jpl.pyre.general.results.SimulationResultsOperations.clear
import gov.nasa.jpl.pyre.general.results.SimulationResultsOperations.reportHandler

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

/**
 * Starting from [initialPlan], construct a new plan with the aid of simulation.
 *
 * Whenever [SchedulingScope.edit] is called, the entire plan will be resimulated and [SchedulingScope.results] updated.
 */
fun <M : Any> schedule(
    initialPlan: Plan<M>,
    constructModel: context (InitScope) () -> M,
    incon: Checkpoint<M>? = null,
    block: context (SchedulingScope<M>) () -> Unit
): Plan<M> {
    var workingPlan = initialPlan
    val results = MutableSimulationResults(initialPlan.startTime, initialPlan.endTime)
    lateinit var model: M

    fun resimulate() {
        results.clear()
        val simulator = Simulator(
            results.reportHandler(),
            workingPlan.startTime,
            incon = incon,
            constructModel = {
                // Capture the model as we build it
                constructModel().also { model = it }
            },
        )
        simulator.runPlan(workingPlan)
    }

    // Do one simulation before calling block, so we have initial results to work with.
    resimulate()

    block(object : SchedulingScope<M> {
        override val plan: Plan<M> get() = workingPlan
        override val model: M get() = model
        override val results: SimulationResults get() = results

        override fun edit(edits: PlanEdits<M>) {
            workingPlan = edits.applyTo(workingPlan)
            resimulate()
        }
    })

    return workingPlan
}
