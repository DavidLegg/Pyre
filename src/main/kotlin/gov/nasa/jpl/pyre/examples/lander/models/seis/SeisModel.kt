package gov.nasa.jpl.pyre.examples.lander.models.seis

import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.Duration.Companion.SECOND
import gov.nasa.jpl.pyre.kernel.ratioOver
import gov.nasa.jpl.pyre.examples.lander.models.power.PowerModel
import gov.nasa.jpl.pyre.examples.lander.models.power.PowerModel.PelItem
import gov.nasa.jpl.pyre.examples.lander.models.seis.SeisConfig.*
import gov.nasa.jpl.pyre.foundation.reporting.Reporting.registered
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResource
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.asPolynomial
import gov.nasa.jpl.pyre.foundation.resources.discrete.*
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.resources.discrete.DoubleResourceOperations.decrease
import gov.nasa.jpl.pyre.foundation.resources.discrete.DoubleResourceOperations.increase
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.subContext
import gov.nasa.jpl.pyre.foundation.tasks.TaskOperations.delay
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.general.resources.polynomial.IntegralResource
import gov.nasa.jpl.pyre.general.resources.polynomial.Polynomial
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.integral


class SeisModel(
    context: InitScope,
) {
    val poweredOn: MutableBooleanResource
    val mdeShouldBeOn: MutableBooleanResource

    val deviceOn: Map<Device, MutableBooleanResource>
    val channelRates: Map<Channel, MutableDiscreteResource<ChannelRate>>
    val deviceTypeMetrics: Map<DeviceType, DeviceTypeMetrics>

    // Register encapsulates entire list since the list must be mutable
    val combinedChannelOutRates: MutableDiscreteResource<List<ChannelOutRateGroup>>

    val internalRate: MutableDoubleResource
    val internalVolume: PolynomialResource

    val rateToSendToVC: MutableDoubleResource
    val volumeToSendToVC: PolynomialResource

    val continuousDataSentIn: MutableDoubleResource

    // Data rate for how fast SEIS can process data and pass to lander in current configuration
    val transferRate: MutableDoubleResource // Mbits/sec

    val vbbMode: MutableDiscreteResource<VBBMode>

    init {
        with (context) {
            poweredOn = discreteResource("power_on", false).registered()
            mdeShouldBeOn = discreteResource("mde_should_be_on", false).registered()
            subContext("internal_volume") {
                internalRate = discreteResource("rate", 0.0)
                internalVolume = internalRate.asPolynomial().integral("volume", 0.0)
                    .registered()
            }
            subContext("volume_to_send_to_vc") {
                rateToSendToVC = discreteResource("rate", 0.0).registered()
                volumeToSendToVC = rateToSendToVC.asPolynomial().integral("volume", 0.0)
                    .registered()
            }
            continuousDataSentIn = discreteResource("continuous_data_sent_in", 0.0).registered()
            transferRate = discreteResource("transfer_rate", 1666.66/3600.0).registered()
            vbbMode = discreteResource("vbb_mode", VBBMode.SCI).registered()
            subContext("channel") {
                channelRates = Channel.entries.associateWith {
                    discreteResource(it.toString(), ChannelRate(0.0, 0.0)).registered()
                }
            }
            subContext("device_type") {
                deviceTypeMetrics = DeviceType.entries.associateWith {
                    DeviceTypeMetrics(subContext(it.toString()))
                }
            }
            subContext("device_on") {
                deviceOn = Device.entries.associateWith {
                    discreteResource(it.toString(), false)
                }
            }
            combinedChannelOutRates = discreteResource("combined_channel_out_rates", emptyList())
        }
    }


    context (scope: TaskScope)
    suspend fun setDeviceState(device: Device, state: Boolean) {
        deviceOn.getValue(device).set(state)
        updateChannelRates()
    }

    context (scope: TaskScope)
    suspend fun setChannelRates(newChannelRates: Map<Channel, ChannelRate>) {
        for ((channel, rate) in newChannelRates) {
            channelRates.getValue(channel).set(rate)
        }
        updateChannelRates()
    }

    context (scope: TaskScope)
    suspend fun setCombinedChannelOutRates(newCombinedChannelOutRates: List<ChannelOutRateGroup>) {
        combinedChannelOutRates.set(newCombinedChannelOutRates)
        updateChannelRates()
    }

    context (scope: TaskScope)
    suspend fun setSamplingRate(deviceType: DeviceType, samplingRate: Double) {
        deviceTypeMetrics.getValue(deviceType).samplingRate.set(samplingRate)
        updateChannelRates()
    }

    context (scope: TaskScope)
    suspend fun setGain(deviceType: DeviceType, gain: Gain) {
        deviceTypeMetrics.getValue(deviceType).gain.set(gain)
        updateChannelRates()
    }

    context (scope: TaskScope)
    private suspend fun updateChannelRates() {
        // Zero out rates to reset further down
        internalRate.set(0.0)
        rateToSendToVC.set(0.0)

        // INTERNAL VOLUME

        for (rate in channelRates.values) {
            internalRate.increase(rate.getValue().inRate)
        }

        // VOLUME TO SEND TO VC

        val channelsOn: MutableSet<Channel> = mutableSetOf() // Keep track of all channels that are on

        // VBB
        val vbbDevices = listOf(Device.VBB1, Device.VBB2, Device.VBB3).filter {
            deviceOn.getValue(it).getValue()
        }
        val vbbModeAbbrev = vbbMode.getValue().abbrev

        val velMetrics = deviceTypeMetrics.getValue(DeviceType.VEL)
        val velRate = velMetrics.samplingRate.getValue()
        if (velRate != 0.0) {
            val velFreq = if (velRate == 20.0) "LR" else "HR"
            val velGain = velMetrics.gain.getValue().abbrev
            vbbDevices.mapTo(channelsOn) {
                enumValueOf<Channel>("${it}_VEL_${velFreq}_${velGain}_${vbbModeAbbrev}")
            }
        }

        val posMetrics = deviceTypeMetrics.getValue(DeviceType.POS)
        val posRate = posMetrics.samplingRate.getValue()
        if (posRate != 0.0) {
            val posFreq = if (posRate == 0.1) "LR" else "HR"
            val posGain = posMetrics.gain.getValue().abbrev
            vbbDevices.mapTo(channelsOn) {
                enumValueOf<Channel>("${it}_POS_${posFreq}_${posGain}_${vbbModeAbbrev}")
            }
        }

        val tempRate = deviceTypeMetrics.getValue(DeviceType.TEMP).samplingRate.getValue()
        if (tempRate != 0.0) {
            val tempFreq = if (tempRate == 0.1) "LR" else "HR"
            vbbDevices.mapTo(channelsOn) {
                enumValueOf<Channel>("${it}_TMP_${tempFreq}")
            }
        }

        // SP
        val spDevices = listOf(Device.SP1, Device.SP2, Device.SP3).filter {
            deviceOn.getValue(it).getValue()
        }
        val metrics = deviceTypeMetrics.getValue(DeviceType.SP)
        val spRate = metrics.samplingRate.getValue()
        if (spRate != 0.0) {
            val spFreq = if (spRate == 20.0) "LR" else "HR"
            val spGain = metrics.gain.getValue().abbrev
            spDevices.mapTo(channelsOn) {
                enumValueOf<Channel>("${it}_${spFreq}_${spGain}")
            }
        }

        // SCIT
        val scitRate = deviceTypeMetrics.getValue(DeviceType.SCIT).samplingRate.getValue()
        channelsOn.add(if (scitRate == 0.1) Channel.SCIT_LR else Channel.SCIT_HR)

        // Sum all channel rates
        for (channel in channelsOn) {
            rateToSendToVC.increase(channelRates.getValue(channel).getValue().outRate)
        }

        // Add combined channel out rates if all channels within a group are on
        for (group in combinedChannelOutRates.getValue()) {
            if (channelsOn.containsAll(group.channels)) {
                rateToSendToVC.increase(group.outRate)
            }
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
    suspend fun runMDEStateMachine(powerModel: PowerModel) {
        var state = "off"

        if (poweredOn.getValue() && mdeShouldBeOn.getValue()) {
            // "on" uses the most power, followed by "startup" then "htr", so we check for them in that order
            state = "on"
        } else if (poweredOn.getValue() && powerModel.pelStates.getValue(PelItem.SEIS_MDEHTR_EBOX).getValue() == "on") {
            // if heater is on and higher-power MDE-modes are not active, MDE is in heater mode
            state = "htr"
        }

        powerModel.pelStates.getValue(PelItem.SEIS_MDE_EXT).set(state)
        powerModel.pelStates.getValue(PelItem.SEIS_MDE_EBOX).set(state)
    }
}