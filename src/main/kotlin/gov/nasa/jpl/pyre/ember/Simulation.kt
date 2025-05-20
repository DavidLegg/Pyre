package gov.nasa.jpl.pyre.ember

import org.example.gov.nasa.jpl.pyre.core.*

class Simulation {
    data class SimulationSetup(
        val reportHandler: (JsonValue) -> Unit,
        val inconProvider: InconProvider,
        val finconCollector: FinconCollector,
        val finconTimes: Sequence<Duration>,
        val initializer: Task<*>,
        val endTime: Duration,
    )

    companion object {
        fun run(setup: SimulationSetup) {
            with (setup) {
                with(SimulationState(reportHandler)) {
                    addTask(initializer)
                    restore(inconProvider)
                    // Fincon tasks go in after restoring, because they vary by each simulation
                    finconTimes.forEach { finconTime ->
                        addTask(Task.of("collect fincon at $finconTime") {
                            save(finconCollector)
                            Task.PureStepResult.Complete(Unit)
                        }, time = finconTime)
                    }
                    while (time() < endTime) step()
                }
            }
        }
    }
}