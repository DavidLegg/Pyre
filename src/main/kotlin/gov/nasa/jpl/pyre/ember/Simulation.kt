package gov.nasa.jpl.pyre.ember

import gov.nasa.jpl.pyre.ember.SimulationState.SimulationInitializer

class Simulation {
    data class SimulationSetup(
        val reportHandler: (JsonValue) -> Unit,
        val inconProvider: InconProvider?,
        val finconCollector: FinconCollector,
        val finconTime: Duration?,
        val initialize: SimulationInitializer.() -> Unit,
        val endTime: Duration,
    )

    companion object {
        fun run(setup: SimulationSetup) {
            with (setup) {
                with(SimulationState(reportHandler)) {
                    // Initialize the model, which gives us cells and tasks
                    initializer().initialize()
                    // Restore the model if we have an incon
                    inconProvider?.let { restore(it) }
                    // Add a task to cut the fincon at the right time, if fincon was requested
                    finconTime?.let {
                        addTask(Task.of("collect fincon at $finconTime") {
                            save(finconCollector)
                            Task.PureStepResult.Complete(Unit)
                        }, time = it)
                    }
                    // Step the simulation until we hit the end time
                    // This includes cutting fincons at each fincon time.
                    while (time() < endTime) stepTo(endTime)
                }
            }
        }
    }
}