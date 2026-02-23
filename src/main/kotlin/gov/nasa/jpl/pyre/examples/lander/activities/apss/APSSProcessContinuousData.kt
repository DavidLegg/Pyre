package gov.nasa.jpl.pyre.examples.lander.activities.apss

import gov.nasa.jpl.pyre.examples.lander.Mission
import gov.nasa.jpl.pyre.examples.lander.models.apss.APSSModel.Companion.LIMIT_RESOLUTION
import gov.nasa.jpl.pyre.examples.lander.models.data.DataConfig.APID.APID_APSS_CONTINUOUS_SCI
import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.foundation.resources.discrete.DoubleResourceOperations.decrease
import gov.nasa.jpl.pyre.foundation.resources.discrete.DoubleResourceOperations.increase
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import kotlinx.serialization.Serializable
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Serializable
class APSSProcessContinuousData(
    val timeout: Duration = 20.minutes,
): Activity<Mission> {
    context (scope: TaskScope)
    override suspend fun effectModel(model: Mission) {
        val apssModel = model.apssModel
        val internalVolume = apssModel.internalVolume.getValue()

        // If the transfer rate is low or the duration too short, we might not get all the data
        if (apssModel.paePoweredOn.getValue() && internalVolume > LIMIT_RESOLUTION) {
            // Volume is in Mbits, rate is in Mbit/sec.
            val internalTransferVolume = min(internalVolume, (timeout / 1.seconds) * apssModel.transferRate.getValue())
            val dataTransferredRatio = internalTransferVolume / internalVolume
            val transferredVolume = apssModel.volumeToSendToVC.getValue() * dataTransferredRatio
            val dataRate = transferredVolume / (timeout / 1.seconds)
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