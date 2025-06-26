package gov.nasa.jpl.pyre.examples.model_interfaces.lander.models.data

import gov.nasa.jpl.pyre.examples.model_interfaces.lander.models.data.DataConfig.APID
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext

class HKModel(context: SparkInitContext, basePath: String) {
    // 52 bits/second; 0.1872 Mbits/hour
    val APSS: InstrumentHKChannel

    // 27 bits/second; 0.0972 Mbits/hour
    val IDC: InstrumentHKChannel

    // 31 bits/second; 0.1116 Mbits/hour
    val IDA: InstrumentHKChannel

    // 16 bits/second; 0.0576 Mbits/hour
    val HeatProbe: InstrumentHKChannel

    // 18 bits/second; 0.0648 Mbits/hour
    val HeatProbe_NON_CHAN: InstrumentHKChannel

    // 66 bits/second; 0.2376 Mbits/hour
    val SEIS: InstrumentHKChannel

    // 63 bits/second; 0.2268 Mbits/hour, no non-channelized data for diagnostic wakes
    val SEIS_NON_CHAN: InstrumentHKChannel

    val DUMP_CMD_HISTORY: InstrumentHKChannel

    val allChannels: List<InstrumentHKChannel>

    init {
        APSS = InstrumentHKChannel(context, APID.APID_CHAN_003, 0.1872, 0.1872, "$basePath/APSS")
        IDC = InstrumentHKChannel(context, APID.APID_CHAN_005, 0.0972, 0.0972, "$basePath/IDC")
        IDA = InstrumentHKChannel(context, APID.APID_CHAN_006, 0.1116, 0.1116, "$basePath/IDA")
        HeatProbe = InstrumentHKChannel(context, APID.APID_CHAN_004, 0.0576, 0.0576, "$basePath/HeatProbe")
        HeatProbe_NON_CHAN = InstrumentHKChannel(context, APID.APID_HeatProbe_ENG, 0.0648, 0.0648, "$basePath/HeatProbe_NON_CHAN")
        SEIS = InstrumentHKChannel(context, APID.APID_CHAN_007, 0.2376, 0.2376, "$basePath/SEIS")
        SEIS_NON_CHAN = InstrumentHKChannel(context, APID.APID_SEIS_ENG, 0.2268, 0.0, "$basePath/SEIS_NON_CHAN")
        DUMP_CMD_HISTORY = InstrumentHKChannel(context, APID.APID_DUMP_CMD_HISTORY, 0.3123, 0.3123, "$basePath/DUMP_CMD_HISTORY")

        allChannels = listOf(
            APSS,
            IDC,
            IDA,
            HeatProbe,
            HeatProbe_NON_CHAN,
            SEIS,
            SEIS_NON_CHAN,
            DUMP_CMD_HISTORY
        )
    }
}