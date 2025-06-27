package gov.nasa.jpl.pyre.examples.lander.models.seis

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.JsonValue
import gov.nasa.jpl.pyre.ember.JsonValue.*
import gov.nasa.jpl.pyre.ember.Serializer
import gov.nasa.jpl.pyre.flame.serialization.asDouble
import gov.nasa.jpl.pyre.flame.serialization.get
import gov.nasa.jpl.pyre.spark.reporting.BasicSerializers.enumSerializer
import gov.nasa.jpl.pyre.spark.reporting.BasicSerializers.listSerializer
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.discreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.EnumResourceOperations.discreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.EnumResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableDoubleResource
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext

class SeisConfig {
    enum class Gain(val abbrev: String) {
        LOW("LG"),
        HIGH("HG");
    }

    enum class Device {
        VBB1,
        VBB2,
        VBB3,
        SP1,
        SP2,
        SP3,
        SCIT
    }

    enum class DeviceType {
        VEL,
        POS,
        TEMP,
        SP,
        SCIT
    }

    enum class Channel {
        VBB1_VEL_LR_LG_EN,
        VBB1_VEL_LR_LG_SC,
        VBB1_VEL_LR_HG_EN,
        VBB1_VEL_LR_HG_SC,
        VBB1_VEL_HR_LG_EN,
        VBB1_VEL_HR_LG_SC,
        VBB1_VEL_HR_HG_EN,
        VBB1_VEL_HR_HG_SC,
        VBB1_POS_LR_LG_EN,
        VBB1_POS_LR_LG_SC,
        VBB1_POS_LR_HG_EN,
        VBB1_POS_LR_HG_SC,
        VBB1_POS_HR_LG_EN,
        VBB1_POS_HR_LG_SC,
        VBB1_POS_HR_HG_EN,
        VBB1_POS_HR_HG_SC,
        VBB1_TMP_LR,
        VBB1_TMP_HR,
        VBB2_VEL_LR_LG_EN,
        VBB2_VEL_LR_LG_SC,
        VBB2_VEL_LR_HG_EN,
        VBB2_VEL_LR_HG_SC,
        VBB2_VEL_HR_LG_EN,
        VBB2_VEL_HR_LG_SC,
        VBB2_VEL_HR_HG_EN,
        VBB2_VEL_HR_HG_SC,
        VBB2_POS_LR_LG_EN,
        VBB2_POS_LR_LG_SC,
        VBB2_POS_LR_HG_EN,
        VBB2_POS_LR_HG_SC,
        VBB2_POS_HR_LG_EN,
        VBB2_POS_HR_LG_SC,
        VBB2_POS_HR_HG_EN,
        VBB2_POS_HR_HG_SC,
        VBB2_TMP_LR,
        VBB2_TMP_HR,
        VBB3_VEL_LR_LG_EN,
        VBB3_VEL_LR_LG_SC,
        VBB3_VEL_LR_HG_EN,
        VBB3_VEL_LR_HG_SC,
        VBB3_VEL_HR_LG_EN,
        VBB3_VEL_HR_LG_SC,
        VBB3_VEL_HR_HG_EN,
        VBB3_VEL_HR_HG_SC,
        VBB3_POS_LR_LG_EN,
        VBB3_POS_LR_LG_SC,
        VBB3_POS_LR_HG_EN,
        VBB3_POS_LR_HG_SC,
        VBB3_POS_HR_LG_EN,
        VBB3_POS_HR_LG_SC,
        VBB3_POS_HR_HG_EN,
        VBB3_POS_HR_HG_SC,
        VBB3_TMP_LR,
        VBB3_TMP_HR,
        SP1_LR_LG,
        SP1_LR_HG,
        SP1_HR_LG,
        SP1_HR_HG,
        SP2_LR_LG,
        SP2_LR_HG,
        SP2_HR_LG,
        SP2_HR_HG,
        SP3_LR_LG,
        SP3_LR_HG,
        SP3_HR_LG,
        SP3_HR_HG,
        SCIT_HR,
        SCIT_LR
    }

    enum class VBBMode(val abbrev: String) {
        SCI("SC"),
        ENG("EN");
    }

    data class ChannelRate(val inRate: Double, val outRate: Double) {
        companion object {
            val SERIALIZER: Serializer<ChannelRate> = Serializer.of(InvertibleFunction.of(
                {
                    JsonMap(mapOf(
                        "inRate" to JsonDouble(it.inRate),
                        "outRate" to JsonDouble(it.outRate)
                    ))
                },
                {
                    ChannelRate(it["inRate"].asDouble(), it["outRate"].asDouble())
                }
            ))
        }
    }

    data class ChannelOutRateGroup(val outRate: Double, val channels: List<Channel>) {
        companion object {
            private val channelsSerializer: Serializer<List<Channel>> = listSerializer(enumSerializer())

            val SERIALIZER: Serializer<ChannelOutRateGroup> = Serializer.of(InvertibleFunction.of(
                {
                    JsonMap(mapOf(
                        "outRate" to JsonDouble(it.outRate),
                        "channels" to channelsSerializer.serialize(it.channels),
                    ))
                },
                {
                    ChannelOutRateGroup(
                        it["outRate"].asDouble(),
                        channelsSerializer.deserialize(it["channels"]),
                    )
                }
            ))
        }
    }

    class DeviceTypeMetrics(
        context: SparkInitContext,
        samplingRate: Double,
        gain: Gain,
    ) {
        val samplingRate: MutableDoubleResource
        val gain: MutableDiscreteResource<Gain>

        init {
            with (context) {
                this@DeviceTypeMetrics.samplingRate = registeredDiscreteResource("sampling_rate", samplingRate)
                this@DeviceTypeMetrics.gain = registeredDiscreteResource("gain", gain)
            }
        }

        constructor(context: SparkInitContext) : this(context, 0.0, Gain.HIGH)
    }
}