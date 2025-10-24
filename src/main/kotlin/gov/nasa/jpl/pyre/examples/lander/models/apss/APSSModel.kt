package gov.nasa.jpl.pyre.examples.lander.models.apss

import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.Duration.Companion.SECOND
import gov.nasa.jpl.pyre.kernel.ratioOver
import gov.nasa.jpl.pyre.flame.resources.polynomial.PolynomialResource
import gov.nasa.jpl.pyre.flame.resources.polynomial.PolynomialResourceOperations.asPolynomial
import gov.nasa.jpl.pyre.flame.resources.polynomial.PolynomialResourceOperations.registeredIntegral
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.resources.discrete.DoubleResourceOperations.decrease
import gov.nasa.jpl.pyre.foundation.resources.discrete.DoubleResourceOperations.increase
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableBooleanResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDoubleResource
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.subContext
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope.Companion.delay
import kotlinx.serialization.Serializable

class APSSModel(
    context: InitScope,
) {
    enum class Component {
        TWINS_PY,
        TWINS_MY,
        P,
        IFG,
        APSS_BUS_V
    }

    @Serializable
    data class ComponentRate(
        val defaultRate: Double,
        val bothBoomsOnRate: Double,
    ) {
        fun activeRate(bothBoomsOn: Boolean) = if (bothBoomsOn) bothBoomsOnRate else defaultRate
    }

    class ComponentModel(
        context: InitScope,
        initialState: Boolean,
        initialInRate: ComponentRate,
        initialOutRate: ComponentRate,
    ) {
        val state: MutableBooleanResource
        val inRate: MutableDiscreteResource<ComponentRate>
        val outRate: MutableDiscreteResource<ComponentRate>

        init {
            with (context) {
                state = registeredDiscreteResource("state", initialState)
                inRate = discreteResource("inRate", initialInRate)
                outRate = discreteResource("outRate", initialOutRate)
            }
        }
    }

    val paePoweredOn: MutableBooleanResource
    val components: Map<Component, ComponentModel>
    val internalRate: MutableDoubleResource
    val internalVolume: PolynomialResource
    val rateToSendToVC: MutableDoubleResource
    val volumeToSendToVC: PolynomialResource
    val continuousDataSentIn: MutableDoubleResource

    // Data rate for how fast APSS can process data and pass to lander in current configuration
    val transferRate: MutableDoubleResource // MBits/sec

    init {
        with (context) {
            with (subContext("power_on")) {
                paePoweredOn = registeredDiscreteResource("PAE", false)
                components = Component.entries.associateWith {
                    ComponentModel(this, false, ComponentRate(0.0, 0.0), ComponentRate(0.0, 0.0))
                }
            }
            with (subContext("internal_volume")) {
                internalRate = registeredDiscreteResource("rate", 0.0)
                internalVolume = internalRate.asPolynomial().registeredIntegral("volume", 0.0)
            }
            with (subContext("volume_to_send_to_vc")) {
                rateToSendToVC = registeredDiscreteResource("rate", 0.0)
                volumeToSendToVC = rateToSendToVC.asPolynomial().registeredIntegral("volume", 0.0)
            }
            continuousDataSentIn = registeredDiscreteResource("continuous_data_sent_in", 0.0)
            transferRate = registeredDiscreteResource("transfer_rate", 751.68/3600.0)
        }
    }

    context (scope: TaskScope)
    suspend fun setComponentState(component: Component, state: Boolean) {
        components.getValue(component).state.set(state)
        updateComponentRates()
    }

    context (scope: TaskScope)
    suspend fun updateComponentRates() {
        // Zero out rates to reset further down
        internalRate.set(0.0)
        rateToSendToVC.set(0.0)

        // If two booms are on use the `bothBoomsOnRate`, otherwise use the default rate
        val bothBoomsOn = components.getValue(Component.TWINS_PY).state.getValue() && components.getValue(Component.TWINS_MY).state.getValue()

        for (c in components.values) {
            internalRate.increase(c.inRate.getValue().activeRate(bothBoomsOn))
            rateToSendToVC.increase(c.outRate.getValue().activeRate(bothBoomsOn))
        }
    }

    context (scope: TaskScope)
    suspend fun dumpInternalData(duration: Duration, internalVolumeToDump: Double, vcVolumeToDump: Double) {
        val internalDumpRate = internalVolumeToDump / (duration ratioOver SECOND)
        val sendToVCDumpRate = vcVolumeToDump / (duration ratioOver SECOND)

        internalRate.decrease(internalDumpRate)
        rateToSendToVC.decrease(sendToVCDumpRate)
        delay(duration)
        internalRate.increase(internalDumpRate)
        rateToSendToVC.increase(sendToVCDumpRate)
    }

    context (scope: TaskScope)
    suspend fun dumpInternalData(duration: Duration) {
        dumpInternalData(duration, internalVolume.getValue(), volumeToSendToVC.getValue())
    }

    companion object {
        val LIMIT_RESOLUTION = 0.0001
    }
}