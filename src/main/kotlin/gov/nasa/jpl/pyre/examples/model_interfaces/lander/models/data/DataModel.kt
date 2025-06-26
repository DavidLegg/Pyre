package gov.nasa.jpl.pyre.examples.model_interfaces.lander.models.data

import gov.nasa.jpl.pyre.examples.model_interfaces.lander.models.data.DataConfig.ChannelName
import gov.nasa.jpl.pyre.examples.model_interfaces.lander.models.data.DataConfig.FPT
import gov.nasa.jpl.pyre.examples.model_interfaces.lander.models.data.DataConfig.APID
import gov.nasa.jpl.pyre.examples.model_interfaces.lander.models.data.DataConfig.DART
import gov.nasa.jpl.pyre.flame.resources.polynomial.IntegralResource
import gov.nasa.jpl.pyre.flame.resources.polynomial.PolynomialResource
import gov.nasa.jpl.pyre.flame.resources.polynomial.PolynomialResourceOperations.asPolynomial
import gov.nasa.jpl.pyre.flame.resources.polynomial.PolynomialResourceOperations.constant
import gov.nasa.jpl.pyre.flame.resources.polynomial.PolynomialResourceOperations.greaterThanOrEquals
import gov.nasa.jpl.pyre.flame.resources.polynomial.PolynomialResourceOperations.integral
import gov.nasa.jpl.pyre.flame.resources.polynomial.PolynomialResourceOperations.register
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResourceOperations.and
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResourceOperations.not
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.pure
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.greaterThan
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.lessThan
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.decrease
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.discreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.increase
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.register
import gov.nasa.jpl.pyre.spark.resources.discrete.EnumResourceOperations.discreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.EnumResourceOperations.register
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableDoubleResource
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope
import gov.nasa.jpl.pyre.spark.tasks.whenever
import kotlin.math.min


class DataModel(context: SparkInitContext, basePath: String) {
    private val virtualChannelMap: Map<ChannelName, VirtualChannel>
    private val apidModelMap: Map<APID, APIDModel>

    private val activeFPT: DiscreteResource<FPT>
    val defaultFPT: DiscreteResource<FPT>
    val defaultDART: DiscreteResource<DART>
    val hkModel: HKModel

    init {
        with (context) {
            hkModel = HKModel(context, "$basePath/hk")

            virtualChannelMap = mapOf(
                ChannelName.RETX to VirtualChannel(context, 1000.0, ChannelName.DISCARD, "$basePath/${ChannelName.RETX}"),
                ChannelName.VC00 to VirtualChannel(context, 240.0, ChannelName.DISCARD, "$basePath/${ChannelName.VC00}"),
                ChannelName.VC01 to VirtualChannel(context, 50.0, ChannelName.VC06, "$basePath/${ChannelName.VC01}"),
                ChannelName.VC02 to VirtualChannel(context, 100.0, ChannelName.VC05, "$basePath/${ChannelName.VC02}"),
                ChannelName.VC03 to VirtualChannel(context, 50.0, ChannelName.VC06, "$basePath/${ChannelName.VC03}"),
                ChannelName.VC04 to VirtualChannel(context, 50.0, ChannelName.VC06, "$basePath/${ChannelName.VC04}"),
                ChannelName.VC05 to VirtualChannel(context, 300.0, ChannelName.VC06, "$basePath/${ChannelName.VC05}"),
                ChannelName.VC06 to VirtualChannel(context, 50.0, ChannelName.DISCARD, "$basePath/${ChannelName.VC06}"),
                ChannelName.VC07 to VirtualChannel(context, 200.0, ChannelName.DISCARD, "$basePath/${ChannelName.VC07}"),
                ChannelName.VC08 to VirtualChannel(context, 100.0, ChannelName.VC05, "$basePath/${ChannelName.VC08}"),
                ChannelName.VC09 to VirtualChannel(context, 600.0, ChannelName.VC06, "$basePath/${ChannelName.VC09}"),
                ChannelName.VC10 to VirtualChannel(context, 0.03, ChannelName.DISCARD, "$basePath/${ChannelName.VC10}"),
                ChannelName.VC11 to VirtualChannel(context, 0.03, ChannelName.DISCARD, "$basePath/${ChannelName.VC11}"),
                ChannelName.VC12 to VirtualChannel(context, 0.03, ChannelName.DISCARD, "$basePath/${ChannelName.VC12}")
            )

            apidModelMap = APID.entries.associateWith { APIDModel(context, virtualChannelMap, "$basePath/apids/$it") }

            activeFPT = discreteResource("$basePath/activeFPT", FPT.DEFAULT)
            defaultFPT = discreteResource("$basePath/defaultFPT", FPT.DEFAULT)
            defaultDART = discreteResource("$basePath/defaultDART", DART.DEFAULT)

            register("$basePath/activeFPT", activeFPT)
            register("$basePath/defaultFPT", defaultFPT)
            register("$basePath/defaultDART", defaultDART)
        }
    }

    class VirtualChannel(
        context: SparkInitContext,
        val limit: Double,
        val overflowChannelId: ChannelName,
        basePath: String
    ) {
        // Opening the type on rate and volume allows outside actors to affect these
        val rate: MutableDoubleResource
        val volume: IntegralResource
        // Restricting the type on overflowRate and overflow means we fully control these, as a function of rate and volume.
        val overflowRate: DoubleResource
        val overflow: PolynomialResource

        init {
            with (context) {
                rate = discreteResource("$basePath/rate", 0.0)
                volume = rate.asPolynomial().integral("$basePath/volume", 0.0)

                overflowRate = discreteResource("$basePath/overflowRate", 0.0)
                overflow = overflowRate.asPolynomial().integral("$basePath/volume", 0.0)

                val isFull = volume greaterThanOrEquals limit
                val isOverflowing = overflowRate greaterThan 0.0

                spawn("Monitor $basePath for overflow", whenever(isFull and (rate greaterThan 0.0)) {
                    // Transfer flow to overflow
                    val r = rate.getValue()
                    overflowRate.increase(r)
                    // Use a "decrease by r" instead of "set to 0", in case a parallel task also adjusts the rate
                    rate.decrease(r)
                })

                spawn(
                    "Monitor $basePath for limiting overflow reduction",
                    whenever(isOverflowing and isFull and (rate lessThan 0.0)) {
                        // Transfer some overflow back to normal flow.
                        // Clamp the amount of flow transferred by the available margin in rate.
                        val r = min(overflowRate.getValue(), -rate.getValue())
                        overflowRate.decrease(r)
                        rate.increase(r)
                    })

                spawn("Monitor $basePath for unlimited overflow reduction", whenever(isOverflowing and isFull.not()) {
                    // Since volume is not full, all overflow can be transferred back to normal flow
                    val r = overflow.getValue()
                    overflowRate.decrease(r)
                    rate.increase(r)
                })

                register("$basePath/rate", rate)
                register("$basePath/volume", volume)
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
                register("$basePath/overflow/rate", registeredOverflowRate)
                register("$basePath/overflow/volume", registeredOverflowVolume)
                register("$basePath/discard/rate", registeredDiscardRate)
                register("$basePath/discard/volume", registeredDiscardVolume)
            }
        }
    }

    class APIDModel(
        context: SparkInitContext,
        private val virtualChannelMap: Map<ChannelName, VirtualChannel>,
        basePath: String,
    ) {
        private val routedVC: MutableDiscreteResource<ChannelName>
        private val dataRate: MutableDoubleResource

        init {
            with (context) {
                routedVC = discreteResource("$basePath/routedVC", ChannelName.VC00)
                dataRate = discreteResource("$basePath/dataRate", 0.0)

                register("$basePath/routedVC", routedVC)
                register("$basePath/dataRate", dataRate)
            }
        }

        context(SparkTaskScope<*>)
        suspend fun increaseDataRate(dRate: Double) {
            dataRate.increase(dRate)
            virtualChannelMap.getValue(routedVC.getValue()).rate.increase(dRate)
        }

        context(SparkTaskScope<*>)
        suspend fun updateRoute(channelName: ChannelName) {
            val dRate = dataRate.getValue()
            virtualChannelMap.getValue(routedVC.getValue()).rate.decrease(dRate)
            routedVC.set(channelName)
            virtualChannelMap.getValue(channelName).rate.increase(dRate)
        }
    }
}