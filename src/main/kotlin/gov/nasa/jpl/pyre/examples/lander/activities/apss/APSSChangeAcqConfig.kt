package gov.nasa.jpl.pyre.examples.lander.activities.apss

import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.kernel.plus
import gov.nasa.jpl.pyre.kernel.times
import gov.nasa.jpl.pyre.examples.lander.Mission
import gov.nasa.jpl.pyre.examples.lander.models.apss.APSSModel
import gov.nasa.jpl.pyre.examples.lander.models.power.PowerModel.PelItem
import gov.nasa.jpl.pyre.examples.lander.models.wake.WakeModel
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope.Companion.simulationClock
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope.Companion.delayUntil
import kotlinx.serialization.Serializable
import kotlin.collections.getValue


/**
 * Effectively supports APSS_CHANGE_ACQ_CONFIG.
 * Turns on or off different components of APSS. Keeping these continuous states correct is important for correct power and data modeling results.
 * Use this to represent most APSS activities except boom swaps.
 */
@Serializable
class APSSChangeAcqConfig(
    val duration: Duration = DEFAULT_DURATION,
    val paeOn: Boolean = DEFAULT_PAE_ON,
    val twinsPyOn: Boolean = DEFAULT_TWINS_PY_ON,
    val twinsMyOn: Boolean = DEFAULT_TWINS_MY_ON,
    val psOn: Boolean = DEFAULT_PS_ON,
    val ifgOn: Boolean = DEFAULT_IFG_ON,
    val _28vOn: Boolean = DEFAULT_28V_ON,
    val twinsPySetpoint: Int = DEFAULT_TWINS_PY_SETPOINT,
    val twinsMySetpoint: Int = DEFAULT_TWINS_MY_SETPOINT,
) : Activity<Mission> {

    init {
        // Validation
        require(twinsPySetpoint >= -90 && twinsPySetpoint <= 50) {
            "TWINS PY heater setpoint must be within interval [-90, 50] (degrees C)"
        }
        require(twinsMySetpoint >= -75 && twinsMySetpoint <= 50) {
            "TWINS MY heater setpoint must be within interval [-75, 50] (degrees C)"
        }
    }

    context (scope: TaskScope)
    override suspend fun effectModel(model: Mission) {
        val end = simulationClock.getValue() + duration

        // Toggle APSS HK data production
        val hkChannel = model.dataModel.hkModel.APSS
        if (paeOn) {
            model.dataModel.setInstrumentHKRate(
                WakeModel.WakeType.FULL, hkChannel, hkChannel.defaultFullWakeRate, hkChannel.defaultDiagnosticWakeRate)
        }
        else {
            model.dataModel.setInstrumentHKRate(
                WakeModel.WakeType.FULL, hkChannel, 0.0, 0.0)
        }

        // APSS sensor power modeling
        model.apssModel.paePoweredOn.set(paeOn)
        model.apssModel.setComponentState(APSSModel.Component.TWINS_PY, twinsPyOn)
        model.apssModel.setComponentState(APSSModel.Component.TWINS_MY, twinsMyOn)
        model.apssModel.setComponentState(APSSModel.Component.P, psOn)
        model.apssModel.setComponentState(APSSModel.Component.IFG, ifgOn)
        model.apssModel.setComponentState(APSSModel.Component.APSS_BUS_V, _28vOn)


        // Power modeling
        listOf(
            paeOn to listOf(
                PelItem.APSS_IDLE_PAE,
                PelItem.APSS_GEN_EXT
            ),
            twinsPyOn to listOf(
                PelItem.APSS_TWINSPY_EXT,
                PelItem.APSS_TWINSPY_PAE,
                PelItem.APSS_HEATPY_EXT,
                PelItem.APSS_HEATPY_PAE
            ),
            twinsMyOn to listOf(
                PelItem.APSS_TWINSMY_EXT,
                PelItem.APSS_TWINSMY_PAE,
                PelItem.APSS_HEATMY_EXT,
                PelItem.APSS_HEATMY_PAE
            ),
            psOn to listOf(
                PelItem.APSS_PS_EXT,
                PelItem.APSS_PS_PAE
            ),
            ifgOn to listOf(
                PelItem.APSS_IFG_EXT,
                PelItem.APSS_IFG_PAE
            ),
            _28vOn to listOf(
                PelItem.APSS_28V_EXT,
                PelItem.APSS_28V_PAE
            )
        ).forEach { (isOn, items) ->
            val state = if (isOn) "on" else "off"
            items.forEach { model.powerModel.pelStates.getValue(it).set(state) }
        }

        delayUntil(end)
    }

    companion object {
        private val DEFAULT_DURATION: Duration = 10 * MINUTE
        private const val DEFAULT_PAE_ON: Boolean = true
        private const val DEFAULT_TWINS_PY_ON: Boolean = false
        private const val DEFAULT_TWINS_MY_ON: Boolean = true
        private const val DEFAULT_PS_ON: Boolean = true
        private const val DEFAULT_IFG_ON: Boolean = true
        private const val DEFAULT_28V_ON: Boolean = true
        private const val DEFAULT_TWINS_PY_SETPOINT: Int = -75 // Degrees C
        private const val DEFAULT_TWINS_MY_SETPOINT: Int = -75 // Degrees C
    }
}