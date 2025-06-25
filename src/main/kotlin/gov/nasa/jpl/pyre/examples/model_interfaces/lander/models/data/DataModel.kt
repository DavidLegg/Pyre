package gov.nasa.jpl.pyre.examples.model_interfaces.lander.models.data

import gov.nasa.jpl.pyre.examples.model_interfaces.lander.models.data.DataConfig.ChannelName
import gov.nasa.jpl.pyre.flame.resources.polynomial.IntegralResource
import gov.nasa.jpl.pyre.flame.resources.polynomial.PolynomialResource
import gov.nasa.jpl.pyre.flame.resources.polynomial.PolynomialResourceOperations.asPolynomial
import gov.nasa.jpl.pyre.flame.resources.polynomial.PolynomialResourceOperations.greaterThanOrEquals
import gov.nasa.jpl.pyre.flame.resources.polynomial.PolynomialResourceOperations.integral
import gov.nasa.jpl.pyre.flame.resources.polynomial.PolynomialResourceOperations.lessThan
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResourceOperations.and
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResourceOperations.not
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.greaterThan
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.lessThan
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.decrease
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.discreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.increase
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableDoubleResource
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import gov.nasa.jpl.pyre.spark.tasks.whenever
import kotlin.math.min


class DataModel(context: SparkInitContext) {
    private val virtualChannelMap: Map<ChannelName, VirtualChannel>
    private val apidModelMap: Map<APID, APIDModel>

    private val activeFPT: DiscreteResource<FPT>
    val defaultFPT: DiscreteResource<FPT>
    val defaultDART: DiscreteResource<DART>
    val hkModel: HKModel = HKModel()

    init {
        with (context) {
            virtualChannelMap = mapOf(
                ChannelName.RETX to VirtualChannel(ChannelName.RETX, 1000.0, ChannelName.DISCARD),
                ChannelName.VC00 to VirtualChannel(ChannelName.VC00, 240.0, ChannelName.DISCARD),
                ChannelName.VC01 to VirtualChannel(ChannelName.VC01, 50.0, ChannelName.VC06),
                ChannelName.VC02 to VirtualChannel(ChannelName.VC02, 100.0, ChannelName.VC05),
                ChannelName.VC03 to VirtualChannel(ChannelName.VC03, 50.0, ChannelName.VC06),
                ChannelName.VC04 to VirtualChannel(ChannelName.VC04, 50.0, ChannelName.VC06),
                ChannelName.VC05 to VirtualChannel(ChannelName.VC05, 300.0, ChannelName.VC06),
                ChannelName.VC06 to VirtualChannel(ChannelName.VC06, 50.0, ChannelName.DISCARD),
                ChannelName.VC07 to VirtualChannel(ChannelName.VC07, 200.0, ChannelName.DISCARD),
                ChannelName.VC08 to VirtualChannel(ChannelName.VC08, 100.0, ChannelName.VC05),
                ChannelName.VC09 to VirtualChannel(ChannelName.VC09, 600.0, ChannelName.VC06),
                ChannelName.VC10 to VirtualChannel(ChannelName.VC10, 0.03, ChannelName.DISCARD),
                ChannelName.VC11 to VirtualChannel(ChannelName.VC11, 0.03, ChannelName.DISCARD),
                ChannelName.VC12 to VirtualChannel(ChannelName.VC12, 0.03, ChannelName.DISCARD)
            )
        }
    }

    context(SparkInitContext)
    class VirtualChannel(
        val name: ChannelName,
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
            rate = discreteResource("$name.rate", 0.0)
            volume = rate.asPolynomial().integral("$name.volume", 0.0)

            overflowRate = discreteResource("$name.overflowRate", 0.0)
            overflow = overflowRate.asPolynomial().integral("$name.volume", 0.0)

            val isFull = volume greaterThanOrEquals limit
            val isOverflowing = overflowRate greaterThan 0.0

            spawn("Monitor $name for overflow", whenever (isFull and (rate greaterThan 0.0)) {
                // Transfer flow to overflow
                val r = rate.getValue()
                overflowRate.increase(r)
                // Use a "decrease by r" instead of "set to 0", in case a parallel task also adjusts the rate
                rate.decrease(r)
            })

            spawn("Monitor $name for limiting overflow reduction", whenever(isOverflowing and isFull and (rate lessThan 0.0)) {
                // Transfer some overflow back to normal flow.
                // Clamp the amount of flow transferred by the available margin in rate.
                val r = min(overflowRate.getValue(), -rate.getValue())
                overflowRate.decrease(r)
                rate.increase(r)
            })

            spawn("Monitor $name for unlimited overflow reduction", whenever(isOverflowing and isFull.not()) {
                // Since volume is not full, all overflow can be transferred back to normal flow
                val r = overflow.getValue()
                overflowRate.decrease(r)
                rate.increase(r)
            })
        }
    }
}