package pyre_tutorials

import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.tasks.TaskOperations.delay
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import pyre_tutorials.Heater.HeaterState
import kotlin.time.Duration

data class TurnHeaterOff(val time: Duration) : Activity<Heater> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: Heater) {
        delay(time)
        model.state.set(HeaterState.OFF)
    }
}
