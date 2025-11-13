package gov.nasa.jpl.pyre.examples.lander.models.data

import gov.nasa.jpl.pyre.examples.lander.models.data.DataConfig.ChannelName
import gov.nasa.jpl.pyre.examples.lander.models.wake.WakeModel.WakeType
import gov.nasa.jpl.pyre.general.resources.polynomial.IntegralResource
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResource
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.asPolynomial
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.constant
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.greaterThanOrEquals
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.integral
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.registeredIntegral
import gov.nasa.jpl.pyre.foundation.reporting.Reporting.register
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResourceOperations.and
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResourceOperations.not
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad.pure
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.greaterThan
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.lessThan
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.resources.discrete.DoubleResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DoubleResourceOperations.decrease
import gov.nasa.jpl.pyre.foundation.resources.discrete.DoubleResourceOperations.increase
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDoubleResource
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.tasks.Reactions.whenever
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.spawn
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.subContext
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import kotlin.math.min


class DataModel(context: InitScope) {
    private val virtualChannelMap: Map<ChannelName, VirtualChannel>
    val apidModelMap: Map<DataConfig.APID, APIDModel>

    private val activeFPT: DiscreteResource<DataConfig.FPT>
    val defaultFPT: DiscreteResource<DataConfig.FPT>
    val defaultDART: DiscreteResource<DataConfig.DART>
    val hkModel: HKModel

    init {
        with (context) {
            hkModel = HKModel(subContext("hk"))

            virtualChannelMap = mapOf(
                ChannelName.RETX to VirtualChannel(subContext(ChannelName.RETX.toString()), 1000.0, ChannelName.DISCARD),
                ChannelName.VC00 to VirtualChannel(subContext(ChannelName.VC00.toString()), 240.0, ChannelName.DISCARD),
                ChannelName.VC01 to VirtualChannel(subContext(ChannelName.VC01.toString()), 50.0, ChannelName.VC06),
                ChannelName.VC02 to VirtualChannel(subContext(ChannelName.VC02.toString()), 100.0, ChannelName.VC05),
                ChannelName.VC03 to VirtualChannel(subContext(ChannelName.VC03.toString()), 50.0, ChannelName.VC06),
                ChannelName.VC04 to VirtualChannel(subContext(ChannelName.VC04.toString()), 50.0, ChannelName.VC06),
                ChannelName.VC05 to VirtualChannel(subContext(ChannelName.VC05.toString()), 300.0, ChannelName.VC06),
                ChannelName.VC06 to VirtualChannel(subContext(ChannelName.VC06.toString()), 50.0, ChannelName.DISCARD),
                ChannelName.VC07 to VirtualChannel(subContext(ChannelName.VC07.toString()), 200.0, ChannelName.DISCARD),
                ChannelName.VC08 to VirtualChannel(subContext(ChannelName.VC08.toString()), 100.0, ChannelName.VC05),
                ChannelName.VC09 to VirtualChannel(subContext(ChannelName.VC09.toString()), 600.0, ChannelName.VC06),
                ChannelName.VC10 to VirtualChannel(subContext(ChannelName.VC10.toString()), 0.03, ChannelName.DISCARD),
                ChannelName.VC11 to VirtualChannel(subContext(ChannelName.VC11.toString()), 0.03, ChannelName.DISCARD),
                ChannelName.VC12 to VirtualChannel(subContext(ChannelName.VC12.toString()), 0.03, ChannelName.DISCARD)
            )

            subContext("apids") {
                apidModelMap = DataConfig.APID.entries.associateWith {
                    APIDModel(subContext(it.toString()), virtualChannelMap)
                }
            }

            activeFPT = registeredDiscreteResource("activeFPT", DataConfig.FPT.Companion.DEFAULT)
            defaultFPT = registeredDiscreteResource("defaultFPT", DataConfig.FPT.Companion.DEFAULT)
            defaultDART = registeredDiscreteResource("defaultDART", DataConfig.DART.Companion.DEFAULT)
        }
    }

    context (scope: TaskScope)
    suspend fun setInstrumentHKRate(
        wakeType: WakeType,
        hkChannel: InstrumentHKChannel,
        fullRate: Double,
        diagnosticRate: Double
    ) {
        val deltaRate = when (wakeType) {
            WakeType.FULL -> fullRate - hkChannel.fullWakeRate.getValue()
            WakeType.DIAGNOSTIC -> diagnosticRate - hkChannel.diagnosticWakeRate.getValue()
            WakeType.NONE -> 0.0
        }

        apidModelMap.getValue(hkChannel.apid).increaseDataRate(deltaRate)

        hkChannel.fullWakeRate.set(fullRate)
        hkChannel.diagnosticWakeRate.set(diagnosticRate)
    }


    class VirtualChannel(
        context: InitScope,
        val limit: Double,
        val overflowChannelId: ChannelName
    ) {
        // Opening the type on rate and volume allows outside actors to affect these
        val rate: MutableDoubleResource
        val volume: IntegralResource
        // Restricting the type on overflowRate and overflow means we fully control these, as a function of rate and volume.
        val overflowRate: DoubleResource
        val overflow: PolynomialResource

        init {
            with (context) {
                rate = registeredDiscreteResource("rate", 0.0)
                volume = rate.asPolynomial().registeredIntegral("volume", 0.0)

                overflowRate = discreteResource("overflowRate", 0.0)
                overflow = overflowRate.asPolynomial().integral("volume", 0.0)

                val isFull = volume greaterThanOrEquals limit
                val isOverflowing = overflowRate greaterThan 0.0

                spawn("Monitor for overflow", whenever(isFull and (rate greaterThan 0.0)) {
                    // Transfer flow to overflow
                    val r = rate.getValue()
                    overflowRate.increase(r)
                    // Use a "decrease by r" instead of "set to 0", in case a parallel task also adjusts the rate
                    rate.decrease(r)
                })

                spawn(
                    "Monitor for limiting overflow reduction",
                    whenever(isOverflowing and isFull and (rate lessThan 0.0)) {
                        // Transfer some overflow back to normal flow.
                        // Clamp the amount of flow transferred by the available margin in rate.
                        val r = min(overflowRate.getValue(), -rate.getValue())
                        overflowRate.decrease(r)
                        rate.increase(r)
                    })

                spawn("Monitor for unlimited overflow reduction", whenever(isOverflowing and isFull.not()) {
                    // Since volume is not full, all overflow can be transferred back to normal flow
                    val r = overflow.getValue()
                    overflowRate.decrease(r)
                    rate.increase(r)
                })

                val registeredOverflowRate: DoubleResource
                val registeredOverflowVolume: PolynomialResource
                val registeredDiscardRate: DoubleResource
                val registeredDiscardVolume: PolynomialResource
                if (overflowChannelId != ChannelName.DISCARD) {
                    registeredOverflowRate = overflowRate
                    registeredOverflowVolume = overflow
                    registeredDiscardRate = pure(0.0)
                    registeredDiscardVolume = constant(0.0)
                } else {
                    registeredOverflowRate = pure(0.0)
                    registeredOverflowVolume = constant(0.0)
                    registeredDiscardRate = overflowRate
                    registeredDiscardVolume = overflow
                }
                subContext("overflow") {
                    register("rate", registeredOverflowRate)
                    register("volume", registeredOverflowVolume)
                }
                subContext("discard") {
                    register("rate", registeredDiscardRate)
                    register("volume", registeredDiscardVolume)
                }
            }
        }
    }

    class APIDModel(
        context: InitScope,
        private val virtualChannelMap: Map<ChannelName, VirtualChannel>,
    ) {
        private val routedVC: MutableDiscreteResource<ChannelName>
        private val dataRate: MutableDoubleResource

        init {
            with (context) {
                routedVC = registeredDiscreteResource("routedVC", ChannelName.VC00)
                dataRate = registeredDiscreteResource("dataRate", 0.0)
            }
        }

        context (scope: TaskScope)
        suspend fun increaseDataRate(dRate: Double) {
            dataRate.increase(dRate)
            virtualChannelMap.getValue(routedVC.getValue()).rate.increase(dRate)
        }

        context (scope: TaskScope)
        suspend fun updateRoute(channelName: ChannelName) {
            val dRate = dataRate.getValue()
            virtualChannelMap.getValue(routedVC.getValue()).rate.decrease(dRate)
            routedVC.set(channelName)
            virtualChannelMap.getValue(channelName).rate.increase(dRate)
        }
    }
}