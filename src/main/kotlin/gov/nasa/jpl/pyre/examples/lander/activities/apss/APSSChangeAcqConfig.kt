package gov.nasa.jpl.pyre.examples.lander.activities.apss

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.ember.JsonValue
import gov.nasa.jpl.pyre.ember.JsonValue.*
import gov.nasa.jpl.pyre.ember.Serializer
import gov.nasa.jpl.pyre.ember.plus
import gov.nasa.jpl.pyre.ember.times
import gov.nasa.jpl.pyre.examples.lander.Mission
import gov.nasa.jpl.pyre.examples.lander.models.apss.APSSModel
import gov.nasa.jpl.pyre.examples.lander.models.power.PowerModel.PelItem
import gov.nasa.jpl.pyre.examples.lander.models.wake.WakeModel
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.flame.serialization.asBoolean
import gov.nasa.jpl.pyre.flame.serialization.asInt
import gov.nasa.jpl.pyre.flame.serialization.get
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope
import gov.nasa.jpl.pyre.spark.tasks.delayUntil
import java.util.List


/**
 * Effectively supports APSS_CHANGE_ACQ_CONFIG.
 * Turns on or off different components of APSS. Keeping these continuous states correct is important for correct power and data modeling results.
 * Use this to represent most APSS activities except boom swaps.
 */
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
) : Activity<Mission, Unit> {

    init {
        // Validation
        require(twinsPySetpoint >= -90 && twinsPySetpoint <= 50) {
            "TWINS PY heater setpoint must be within interval [-90, 50] (degrees C)"
        }
        require(twinsMySetpoint >= -75 && twinsMySetpoint <= 50) {
            "TWINS MY heater setpoint must be within interval [-75, 50] (degrees C)"
        }
    }

    context(SparkTaskScope<Unit>)
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

        val SERIALIZER: Serializer<APSSChangeAcqConfig> = Serializer.of(InvertibleFunction.of(
            {
                JsonMap(mapOf(
                    "duration" to Duration.serializer().serialize(it.duration),
                    "paeOn" to JsonBoolean(it.paeOn),
                    "twinsPyOn" to JsonBoolean(it.twinsPyOn),
                    "twinsMyOn" to JsonBoolean(it.twinsMyOn),
                    "psOn" to JsonBoolean(it.psOn),
                    "ifgOn" to JsonBoolean(it.ifgOn),
                    "_28vOn" to JsonBoolean(it._28vOn),
                    "twinsPySetpoint" to JsonInt(it.twinsPySetpoint.toLong()),
                    "twinsMySetpoint" to JsonInt(it.twinsMySetpoint.toLong()),
                ))
            },
            {
                APSSChangeAcqConfig(
                    Duration.serializer().deserialize(it["duration"]),
                    it["paeOn"].asBoolean(),
                    it["twinsPyOn"].asBoolean(),
                    it["twinsMyOn"].asBoolean(),
                    it["psOn"].asBoolean(),
                    it["ifgOn"].asBoolean(),
                    it["_28vOn"].asBoolean(),
                    it["twinsPySetpoint"].asInt(),
                    it["twinsMySetpoint"].asInt(),
                )
            }
        ))
    }
}