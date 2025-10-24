package gov.nasa.jpl.pyre.examples.lander.models.heatprobe

import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.Duration.Companion.HOUR
import gov.nasa.jpl.pyre.kernel.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.kernel.Duration.Companion.SECOND
import gov.nasa.jpl.pyre.kernel.plus
import gov.nasa.jpl.pyre.kernel.times
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResource
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.asPolynomial
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.registeredIntegral
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDoubleResource
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.subContext
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope


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
            powerState = registeredDiscreteResource("powerState", PowerState.Off)
            internalDataRate = discreteResource("internalDataRate", 0.0)
            internalData = internalDataRate.asPolynomial().registeredIntegral("internalData", 0.0)
            sciDataSentInActivity = registeredDiscreteResource("sciDataSentInActivity", 0.0)
            ssaState = registeredDiscreteResource("ssaState", SSAState.Off)
            radState = registeredDiscreteResource("radState", RADState.Off)
            with (subContext("tableParams")) {
                parametersInTable = HeatProbeParameter.entries.associateWith {
                    registeredDiscreteResource(it.toString(), defaultParameters.getValue(it))
                }
            }
            with (subContext("currentParams")) {
                parametersCurrent = HeatProbeParameter.entries.associateWith {
                    registeredDiscreteResource(it.toString(), defaultParameters.getValue(it))
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
            HeatProbeParameter.PARAM_HeatProbe_MON_TEMP_DURATION to 5 * MINUTE,
            HeatProbeParameter.PARAM_HeatProbe_MON_WAIT_DURATION to 55 * MINUTE,
            HeatProbeParameter.PARAM_HeatProbe_SINGLEPEN_COOL_DURATION to 3 * HOUR,
            HeatProbeParameter.PARAM_HeatProbe_SINGLEPEN_TEMA_DURATION to 1 * HOUR,
            HeatProbeParameter.PARAM_HeatProbe_HAMMER_TIMEOUT to 4 * HOUR,
            HeatProbeParameter.PARAM_HeatProbe_CO_TEMP_DURATION to 10 * MINUTE,
            HeatProbeParameter.PARAM_HeatProbe_CO_TEMA_DURATION to 12 * MINUTE,
            HeatProbeParameter.PARAM_HeatProbe_CO_STATIL_TLM_DURATION to 14 * MINUTE,
            HeatProbeParameter.PARAM_RAD_HEATUP_DURATION to 15 * MINUTE,
            HeatProbeParameter.PARAM_RAD_MEAS_DURATION to 20 * MINUTE,
            HeatProbeParameter.PARAM_RAD_HOURLY_WAIT_DURATION to 56 * MINUTE + 4 * SECOND,
            HeatProbeParameter.PARAM_RAD_STD_WAIT_DURATION_SHORT to 2 * HOUR + 4 * MINUTE + 58 * SECOND,
            HeatProbeParameter.PARAM_RAD_STD_WAIT_DURATION_LONG to 8 * HOUR + 14 * MINUTE + 52 * SECOND,
            HeatProbeParameter.PARAM_RAD_SINGLEMEAS_DURATION to 15 * MINUTE,
            HeatProbeParameter.PARAM_RAD_CAL_MEAS_DURATION to 5 * MINUTE,
        )
    }
}