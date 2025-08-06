package gov.nasa.jpl.pyre.ember

import gov.nasa.jpl.pyre.ember.Duration.Companion.ZERO

/**
 * The minimal type of simulation, in which the entire simulation is set up "at the start".
 * Save/Restore cycles on this simulation are "pure" -
 */
class SimpleSimulation(setup: SimulationSetup) {
    data class SimulationSetup(
        val reportHandler: ReportHandler,
        val inconProvider: InconProvider?,
        val startingTime: Duration = ZERO,
        val initialize: InitScope.() -> Unit,
    )

    private val state: SimulationState

    init {
        with (setup) {
            state = SimulationState(reportHandler)
            // Initialize the model, which gives us cells and tasks
            state.initScope().initialize()
            // Restore the model if we have an incon
            inconProvider?.let(state::restore)
        }
    }

    fun runUntil(time: Duration) {
        require(time >= state.time()) {
            "Simulation time is currently ${state.time()}, cannot step backwards to $time"
        }
        while (state.time() < time) state.stepTo(time)
    }

    fun save(finconCollector: FinconCollector) {
        state.save(finconCollector)
    }
}