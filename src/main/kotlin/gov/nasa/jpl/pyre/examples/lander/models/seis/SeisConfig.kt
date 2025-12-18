package gov.nasa.jpl.pyre.examples.lander.models.seis

import gov.nasa.jpl.pyre.foundation.reporting.Reporting.registered
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDoubleResource
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import kotlinx.serialization.Serializable

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

    @Serializable
    data class ChannelRate(val inRate: Double, val outRate: Double)

    @Serializable
    data class ChannelOutRateGroup(val outRate: Double, val channels: List<Channel>)

    class DeviceTypeMetrics(
        context: InitScope,
        samplingRate: Double,
        gain: Gain,
    ) {
        val samplingRate: MutableDoubleResource
        val gain: MutableDiscreteResource<Gain>

        init {
            with (context) {
                this@DeviceTypeMetrics.samplingRate = discreteResource("sampling_rate", samplingRate).registered()
                this@DeviceTypeMetrics.gain = discreteResource("gain", gain).registered()
            }
        }

        constructor(context: InitScope) : this(context, 0.0, Gain.HIGH)
    }
}