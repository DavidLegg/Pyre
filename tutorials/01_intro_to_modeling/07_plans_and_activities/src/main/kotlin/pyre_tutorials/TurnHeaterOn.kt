package pyre_tutorials

import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import pyre_tutorials.Heater.HeaterState

data object TurnHeaterOn : Activity<Heater> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: Heater) {
        model.state.set(HeaterState.ON)
    }
}