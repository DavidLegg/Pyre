package parakeet_tutorials

import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import parakeet_tutorials.Heater.HeaterState

data object TurnHeaterOn : Activity<Heater> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: Heater) {
        model.state.set(HeaterState.ON)
    }
}