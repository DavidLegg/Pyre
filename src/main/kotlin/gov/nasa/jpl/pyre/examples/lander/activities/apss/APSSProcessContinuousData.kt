package gov.nasa.jpl.pyre.examples.lander.activities.apss

import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.kernel.Duration.Companion.SECOND
import gov.nasa.jpl.pyre.kernel.ratioOver
import gov.nasa.jpl.pyre.kernel.times
import gov.nasa.jpl.pyre.examples.lander.Mission
import gov.nasa.jpl.pyre.examples.lander.models.apss.APSSModel.Companion.LIMIT_RESOLUTION
import gov.nasa.jpl.pyre.examples.lander.models.data.DataConfig.APID.APID_APSS_CONTINUOUS_SCI
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.foundation.resources.discrete.DoubleResourceOperations.decrease
import gov.nasa.jpl.pyre.foundation.resources.discrete.DoubleResourceOperations.increase
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import kotlinx.serialization.Serializable
import kotlin.math.min

@Serializable
class APSSProcessContinuousData(
    val timeout: Duration = 20 * MINUTE,
): Activity<Mission> {
    context (scope: TaskScope)
    override suspend fun effectModel(model: Mission) {
        val apssModel = model.apssModel
        val internalVolume = apssModel.internalVolume.getValue()

        // If the transfer rate is low or the duration too short, we might not get all the data
        if (apssModel.paePoweredOn.getValue() && internalVolume > LIMIT_RESOLUTION) {
            // Volume is in Mbits, rate is in Mbit/sec.
            val internalTransferVolume = min(internalVolume, (timeout ratioOver SECOND) * apssModel.transferRate.getValue())
            val dataTransferredRatio = internalTransferVolume / internalVolume
            val transferredVolume = apssModel.volumeToSendToVC.getValue() * dataTransferredRatio
            val dataRate = transferredVolume / (timeout ratioOver SECOND)
            model.dataModel.apidModelMap.getValue(APID_APSS_CONTINUOUS_SCI).increaseDataRate(dataRate)

            // Track the amount of data sent to VC for this particular activity
            apssModel.continuousDataSentIn.increase(transferredVolume)

            // Dump internal data invokes a delay
            apssModel.dumpInternalData(timeout, internalTransferVolume, transferredVolume)

            apssModel.continuousDataSentIn.decrease(transferredVolume)
            model.dataModel.apidModelMap.getValue(APID_APSS_CONTINUOUS_SCI).increaseDataRate(-dataRate)
        }
    }
}