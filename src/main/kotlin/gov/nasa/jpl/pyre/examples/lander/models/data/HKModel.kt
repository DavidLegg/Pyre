package gov.nasa.jpl.pyre.examples.lander.models.data

import gov.nasa.jpl.pyre.flame.tasks.subContext
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext

class HKModel(context: SparkInitContext) {
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
        with (context) {
            APSS = InstrumentHKChannel(subContext("APSS"), DataConfig.APID.APID_CHAN_003, 0.1872, 0.1872)
            IDC = InstrumentHKChannel(subContext("IDC"), DataConfig.APID.APID_CHAN_005, 0.0972, 0.0972)
            IDA = InstrumentHKChannel(subContext("IDA"), DataConfig.APID.APID_CHAN_006, 0.1116, 0.1116)
            HeatProbe = InstrumentHKChannel(subContext("HeatProbe"), DataConfig.APID.APID_CHAN_004, 0.0576, 0.0576)
            HeatProbe_NON_CHAN = InstrumentHKChannel(subContext("HeatProbe_NON_CHAN"), DataConfig.APID.APID_HeatProbe_ENG, 0.0648, 0.0648)
            SEIS = InstrumentHKChannel(subContext("SEIS"), DataConfig.APID.APID_CHAN_007, 0.2376, 0.2376)
            SEIS_NON_CHAN = InstrumentHKChannel(subContext("SEIS_NON_CHAN"), DataConfig.APID.APID_SEIS_ENG, 0.2268, 0.0)
            DUMP_CMD_HISTORY = InstrumentHKChannel(subContext("DUMP_CMD_HISTORY"), DataConfig.APID.APID_DUMP_CMD_HISTORY, 0.3123, 0.3123)

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
}