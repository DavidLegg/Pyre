package gov.nasa.jpl.pyre.kernel

import gov.nasa.jpl.pyre.kernel.Duration.Companion.ZERO
import kotlinx.coroutines.runBlocking

/**
 * The minimal type of simulation, in which the entire simulation is set up "at the start".
 * Save/Restore cycles on this simulation are "pure" - a simulation created by saving and restoring a fincon
 * should produce results identically to the original simulation.
 */
class SimpleSimulation(setup: SimulationSetup) {
    data class SimulationSetup(
        val reportHandler: ReportHandler,
        val inconProvider: InconProvider?,
        val startingTime: Duration = ZERO,
        val initialize: suspend context (BasicInitScope) () -> Unit,
    )

    private val state: SimulationState

    init {
        with (setup) {
            state = SimulationState(reportHandler)
            // Initialize the model, which gives us cells and tasks
            // We'll just "runBlocking" this, because we know that we won't actually block during initialization.
            runBlocking { initialize(state.initScope()) }
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