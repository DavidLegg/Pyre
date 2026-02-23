package gov.nasa.jpl.pyre.kernel

import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Instant

/**
 * The minimal type of simulation, in which the entire simulation is set up "at the start".
 * Save/Restore cycles on this simulation are "pure" - a simulation created by saving and restoring a fincon
 * should produce results identically to the original simulation.
 */
class SimpleSimulation(setup: SimulationSetup) {
    data class SimulationSetup(
        val reportHandler: ReportHandler,
        val inconProvider: Snapshot? = null,
        val startingTime: Duration = ZERO,
        val initialize: context (BasicInitScope) () -> Unit,
    )

    private val state: SimulationState

    init {
        with (setup) {
            state = SimulationState(reportHandler, inconProvider)
            initialize(state.initScope)
        }
    }

    fun runUntil(time: Instant) {
        require(time >= state.time()) {
            "Simulation time is currently ${state.time()}, cannot step backwards to $time"
        }
        while (state.time() < time) state.stepTo(time)
    }

    fun save(finconCollector: MutableSnapshot) {
        state.save(finconCollector)
    }

    companion object {
        fun SimpleSimulation.save(): Snapshot = MutableSnapshot().also(this::save)
    }
}