package gov.nasa.jpl.pyre.ember

import gov.nasa.jpl.pyre.ember.Duration.Companion.ZERO
import gov.nasa.jpl.pyre.ember.SimulationState.SimulationInitContext

interface Simulation {
    fun runUntil(time: Duration)
    fun save(finconCollector: FinconCollector)
}

/**
 * The minimal type of simulation, in which the entire simulation is set up "at the start".
 * Save/Restore cycles on this simulation are "pure" -
 */
class SimpleSimulation(setup: SimulationSetup) : Simulation {
    data class SimulationSetup(
        val reportHandler: ReportHandler,
        val inconProvider: InconProvider?,
        val startingTime: Duration = ZERO,
        val initialize: SimulationInitContext.() -> Unit,
    )

    private val state: SimulationState

    init {
        with (setup) {
            state = SimulationState(reportHandler)
            // Initialize the model, which gives us cells and tasks
            state.initContext().initialize()
            // Restore the model if we have an incon
            inconProvider?.let(state::restore)
        }
    }

    override fun runUntil(time: Duration) {
        require(time >= state.time()) {
            "Simulation time is currently ${state.time()}, cannot step backwards to $time"
        }
        while (state.time() < time) state.stepTo(time)
    }

    override fun save(finconCollector: FinconCollector) {
        state.save(finconCollector)
    }
}