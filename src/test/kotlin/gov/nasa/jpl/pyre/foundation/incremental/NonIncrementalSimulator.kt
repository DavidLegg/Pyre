package gov.nasa.jpl.pyre.foundation.incremental

import gov.nasa.jpl.pyre.foundation.plans.Checkpoint
import gov.nasa.jpl.pyre.foundation.plans.Plan
import gov.nasa.jpl.pyre.foundation.Simulator
import gov.nasa.jpl.pyre.foundation.SimulatorOperations.plusAssign
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport
import gov.nasa.jpl.pyre.foundation.reporting.ChannelizedReportHandler
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.general.results.MutableSimulationResults
import gov.nasa.jpl.pyre.general.results.SimulationResults
import gov.nasa.jpl.pyre.general.results.SimulationResultsOperations.reportHandler
import gov.nasa.jpl.pyre.general.results.SimulationResultsOperations.toSimulationResults
import gov.nasa.jpl.pyre.foundation.incremental.IncrementalSimulatorOperations.plus
import kotlin.time.Instant

/**
 * Not-actually-incremental implementation of [IncrementalSimulator].
 * This is the baseline correct behavior for an incremental simulator, without the complexity of actually being incremental.
 */
class NonIncrementalSimulator<M : Any>(
    private val constructModel: context (InitScope) () -> M,
    plan: Plan<M>,
    private val incon: Checkpoint<M>? = null,
) : IncrementalSimulator<M> {
    override var plan: Plan<M> = plan
        private set
    override var results: SimulationResults = computeResults(plan)

    override fun run(edits: PlanEdits<M>) {
        plan += edits
        results = computeResults(plan)
    }

    override fun save(time: Instant): Checkpoint<M> {
        // Brute-force the problem of getting a checkpoint at an arbitrary time by running a fresh sim.
        val simulator = Simulator(
            // Since we aren't interested in results, just ignore all reports coming out of the simulator.
            object : ChannelizedReportHandler {
                override fun <T> initChannel(metadata: ChannelReport.ChannelMetadata<T>) { /* ignore */ }
                override fun <T> report(data: ChannelReport.ChannelData<T>) { /* ignore */ }
            },
            plan.startTime,
            incon,
            constructModel,
        )
        simulator += plan.activities
        simulator.runUntil(time)
        return simulator.save()
    }

    private fun computeResults(plan: Plan<M>): SimulationResults {
        // Build a fresh simulator and a fresh set of simulation results
        val newResults = MutableSimulationResults(plan.startTime, plan.endTime)
        val simulator = Simulator(
            newResults.reportHandler(),
            plan.startTime,
            incon,
            constructModel,
        )
        simulator.runPlan(plan)
        return newResults.toSimulationResults()
    }
}
