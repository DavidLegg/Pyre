package gov.nasa.jpl.pyre.examples.lander.models.apss

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.Duration.Companion.SECOND
import gov.nasa.jpl.pyre.ember.JsonValue.JsonDouble
import gov.nasa.jpl.pyre.ember.JsonValue.JsonMap
import gov.nasa.jpl.pyre.ember.Serializer
import gov.nasa.jpl.pyre.ember.ratioOver
import gov.nasa.jpl.pyre.flame.composition.subContext
import gov.nasa.jpl.pyre.flame.resources.polynomial.PolynomialResource
import gov.nasa.jpl.pyre.flame.resources.polynomial.PolynomialResourceOperations.asPolynomial
import gov.nasa.jpl.pyre.flame.resources.polynomial.PolynomialResourceOperations.registeredIntegral
import gov.nasa.jpl.pyre.flame.serialization.asDouble
import gov.nasa.jpl.pyre.flame.serialization.get
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.decrease
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.increase
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableBooleanResource
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableDoubleResource
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope

class APSSModel(
    context: SparkInitContext,
) {
    enum class Component {
        TWINS_PY,
        TWINS_MY,
        P,
        IFG,
        APSS_BUS_V
    }

    data class ComponentRate(
        val defaultRate: Double,
        val bothBoomsOnRate: Double,
    ) {
        fun activeRate(bothBoomsOn: Boolean) = if (bothBoomsOn) bothBoomsOnRate else defaultRate

        companion object {
            val SERIALIZER: Serializer<ComponentRate> = Serializer.of(InvertibleFunction.of(
                {
                    JsonMap(mapOf(
                        "defaultRate" to JsonDouble(it.defaultRate),
                        "bothBoomsOnRate" to JsonDouble(it.bothBoomsOnRate),
                    ))
                },
                {
                    ComponentRate(
                        it["defaultRate"].asDouble(),
                        it["bothBoomsOnRate"].asDouble()
                    )
                }
            ))
        }
    }

    class ComponentModel(
        context: SparkInitContext,
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
                inRate = discreteResource("inRate", initialInRate, ComponentRate.SERIALIZER)
                outRate = discreteResource("outRate", initialOutRate, ComponentRate.SERIALIZER)
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

    context(SparkTaskScope<*>)
    suspend fun setComponentState(component: Component, state: Boolean) {
        components.getValue(component).state.set(state)
        updateComponentRates()
    }

    context(SparkTaskScope<*>)
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

    context(SparkTaskScope<*>)
    suspend fun dumpInternalData(duration: Duration, internalVolumeToDump: Double, vcVolumeToDump: Double) {
        val internalDumpRate = internalVolumeToDump / (duration ratioOver SECOND)
        val sendToVCDumpRate = vcVolumeToDump / (duration ratioOver SECOND)

        internalRate.decrease(internalDumpRate)
        rateToSendToVC.decrease(sendToVCDumpRate)
        delay(duration)
        internalRate.increase(internalDumpRate)
        rateToSendToVC.increase(sendToVCDumpRate)
    }

    context(SparkTaskScope<*>)
    suspend fun dumpInternalData(duration: Duration) {
        dumpInternalData(duration, internalVolume.getValue(), volumeToSendToVC.getValue())
    }
}