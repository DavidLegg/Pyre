package gov.nasa.jpl.parakeet.examples.lander.models.heatprobe

import gov.nasa.jpl.pyre.foundation.reporting.Reporting.registered
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResource
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.asPolynomial
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDoubleResource
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.subContext
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.integral
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


class HeatProbeModel(
    context: InitScope,
) {
    enum class PowerState {
        On,
        Off
    }

    enum class SSAState {
        Off,
        Idle,
        Checkout,
        Single,
        Monitoring
    }

    enum class RADState {
        Off,
        Idle,
        Single,
        Calibration,
        Standard,
        Hourly
    }

    enum class HeatProbeParameter {
        PARAM_HeatProbe_MON_TEMP_DURATION,
        PARAM_HeatProbe_MON_WAIT_DURATION,
        PARAM_HeatProbe_SINGLEPEN_COOL_DURATION,
        PARAM_HeatProbe_SINGLEPEN_TEMA_DURATION,
        PARAM_HeatProbe_HAMMER_TIMEOUT,
        PARAM_HeatProbe_CO_TEMP_DURATION,
        PARAM_HeatProbe_CO_TEMA_DURATION,
        PARAM_HeatProbe_CO_STATIL_TLM_DURATION,
        PARAM_RAD_HEATUP_DURATION,
        PARAM_RAD_MEAS_DURATION,
        PARAM_RAD_HOURLY_WAIT_DURATION,
        PARAM_RAD_STD_WAIT_DURATION_SHORT,
        PARAM_RAD_STD_WAIT_DURATION_LONG,
        PARAM_RAD_SINGLEMEAS_DURATION,
        PARAM_RAD_CAL_MEAS_DURATION
    }

    val powerState: MutableDiscreteResource<PowerState>
    val internalDataRate: MutableDoubleResource
    val internalData: PolynomialResource
    val sciDataSentInActivity: MutableDoubleResource
    val ssaState: MutableDiscreteResource<SSAState>
    val radState: MutableDiscreteResource<RADState>
    val parametersInTable: Map<HeatProbeParameter, MutableDiscreteResource<Duration>>
    val parametersCurrent: Map<HeatProbeParameter, MutableDiscreteResource<Duration>>

    init {
        with (context) {
            powerState = discreteResource("powerState", PowerState.Off).registered()
            internalDataRate = discreteResource("internalDataRate", 0.0)
            internalData = internalDataRate.asPolynomial().integral("internalData", 0.0)
                .registered()
            sciDataSentInActivity = discreteResource("sciDataSentInActivity", 0.0).registered()
            ssaState = discreteResource("ssaState", SSAState.Off).registered()
            radState = discreteResource("radState", RADState.Off).registered()
            subContext("tableParams") {
                parametersInTable = HeatProbeParameter.entries.associateWith {
                    discreteResource(it.toString(), defaultParameters.getValue(it)).registered()
                }
            }
            subContext("currentParams") {
                parametersCurrent = HeatProbeParameter.entries.associateWith {
                    discreteResource(it.toString(), defaultParameters.getValue(it)).registered()
                }
            }
        }
    }

    context (scope: TaskScope)
    suspend fun setParametersToTableValues() {
        for ((param, tableResource) in parametersInTable) {
            parametersCurrent.getValue(param).set(tableResource.getValue())
        }
    }

    companion object {
        private val defaultParameters: Map<HeatProbeParameter, Duration> = mapOf(
            HeatProbeParameter.PARAM_HeatProbe_MON_TEMP_DURATION to 5.minutes,
            HeatProbeParameter.PARAM_HeatProbe_MON_WAIT_DURATION to 55.minutes,
            HeatProbeParameter.PARAM_HeatProbe_SINGLEPEN_COOL_DURATION to 3.hours,
            HeatProbeParameter.PARAM_HeatProbe_SINGLEPEN_TEMA_DURATION to 1.hours,
            HeatProbeParameter.PARAM_HeatProbe_HAMMER_TIMEOUT to 4.hours,
            HeatProbeParameter.PARAM_HeatProbe_CO_TEMP_DURATION to 10.minutes,
            HeatProbeParameter.PARAM_HeatProbe_CO_TEMA_DURATION to 12.minutes,
            HeatProbeParameter.PARAM_HeatProbe_CO_STATIL_TLM_DURATION to 14.minutes,
            HeatProbeParameter.PARAM_RAD_HEATUP_DURATION to 15.minutes,
            HeatProbeParameter.PARAM_RAD_MEAS_DURATION to 20.minutes,
            HeatProbeParameter.PARAM_RAD_HOURLY_WAIT_DURATION to 56.minutes+ 4.seconds,
            HeatProbeParameter.PARAM_RAD_STD_WAIT_DURATION_SHORT to 2.hours + 4.minutes+ 58.seconds,
            HeatProbeParameter.PARAM_RAD_STD_WAIT_DURATION_LONG to 8.hours + 14.minutes + 52.seconds,
            HeatProbeParameter.PARAM_RAD_SINGLEMEAS_DURATION to 15.minutes,
            HeatProbeParameter.PARAM_RAD_CAL_MEAS_DURATION to 5.minutes
        )
    }
}