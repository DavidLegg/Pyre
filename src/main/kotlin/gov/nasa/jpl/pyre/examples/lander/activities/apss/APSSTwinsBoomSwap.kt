package gov.nasa.jpl.pyre.examples.lander.activities.apss

import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.kernel.plus
import gov.nasa.jpl.pyre.kernel.times
import gov.nasa.jpl.pyre.examples.lander.Mission
import gov.nasa.jpl.pyre.examples.lander.models.apss.APSSModel.Component.*
import gov.nasa.jpl.pyre.examples.lander.models.power.PowerModel.PelItem.*
import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope.Companion.simulationClock
import gov.nasa.jpl.pyre.foundation.tasks.TaskOperations.delayUntil
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import kotlinx.serialization.Serializable

@Serializable
class APSSTwinsBoomSwap(
    val duration: Duration = 20 * MINUTE,
): Activity<Mission> {
    context (scope: TaskScope)
    override suspend fun effectModel(model: Mission) {
        val end = simulationClock.getValue() + duration

        val twinsMyOn = model.apssModel.components.getValue(TWINS_MY).state
        val twinsPyOn = model.apssModel.components.getValue(TWINS_PY).state

        val twinsMyPelItems = listOf(
            APSS_TWINSPY_EXT,
            APSS_TWINSPY_PAE,
            APSS_HEATPY_EXT,
            APSS_HEATPY_PAE,
        )
        val twinsPyPelItems = listOf(
            APSS_TWINSMY_EXT,
            APSS_TWINSMY_PAE,
            APSS_HEATMY_EXT,
            APSS_HEATMY_PAE
        )

        // If not exactly one boom is on, we exit without doing anything and trigger a constraint
        if (twinsMyOn.getValue() && twinsPyOn.getValue()) {
            // If +Y is off, turn it on
            twinsPyOn.set(true)
            twinsPyPelItems.forEach { model.powerModel.pelStates.getValue(it).set("on") }
            // Turn -Y off
            twinsMyOn.set(false)
            twinsMyPelItems.forEach { model.powerModel.pelStates.getValue(it).set("off") }
        } else if (!twinsMyOn.getValue() && twinsPyOn.getValue()) {
            // If -Y is off, turn it on
            twinsMyOn.set(true)
            twinsMyPelItems.forEach { model.powerModel.pelStates.getValue(it).set("on") }
            // Turn +Y off
            twinsPyOn.set(false)
            twinsPyPelItems.forEach { model.powerModel.pelStates.getValue(it).set("off") }
        }

        delayUntil(end)
    }
}