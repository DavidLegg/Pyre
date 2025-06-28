package gov.nasa.jpl.pyre.examples.lander.activities.apss

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.ember.Duration.Companion.SECOND
import gov.nasa.jpl.pyre.ember.JsonValue
import gov.nasa.jpl.pyre.ember.JsonValue.*
import gov.nasa.jpl.pyre.ember.Serializer
import gov.nasa.jpl.pyre.ember.ratioOver
import gov.nasa.jpl.pyre.ember.times
import gov.nasa.jpl.pyre.examples.lander.Mission
import gov.nasa.jpl.pyre.examples.lander.models.apss.APSSModel.Companion.LIMIT_RESOLUTION
import gov.nasa.jpl.pyre.examples.lander.models.data.DataConfig.APID.APID_APSS_CONTINUOUS_SCI
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.flame.serialization.get
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.decrease
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.increase
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope
import kotlin.math.min

class APSSProcessContinuousData(
    val timeout: Duration = 20 * MINUTE,
): Activity<Mission, Unit> {
    context(SparkTaskScope@SparkTaskScope<Unit>)
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

    companion object {
        val SERIALIZER: Serializer<APSSProcessContinuousData> = Serializer.of(InvertibleFunction.of(
            {
                JsonMap(mapOf(
                    "timeout" to Duration.serializer().serialize(it.timeout),
                ))
            },
            {
                APSSProcessContinuousData(
                    Duration.serializer().deserialize(it["timeout"]),
                )
            }
        ))
    }
}