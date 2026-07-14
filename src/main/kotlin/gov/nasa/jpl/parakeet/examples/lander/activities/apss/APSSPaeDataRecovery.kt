package gov.nasa.jpl.parakeet.examples.lander.activities.apss

import gov.nasa.jpl.parakeet.examples.lander.Mission
import gov.nasa.jpl.parakeet.examples.lander.models.data.DataConfig.APID.APID_APSS_CONTINUOUS_SCI
import gov.nasa.jpl.parakeet.foundation.plans.Activity
import gov.nasa.jpl.parakeet.foundation.tasks.TaskScope
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Serializable
class APSSPaeDataRecovery(
    val duration: Duration = 27.minutes,
    val dataVolume: Double = 0.0, // Mbits
): Activity<Mission> {
    context (scope: TaskScope)
    override suspend fun effectModel(model: Mission) {
        val dataRate = dataVolume / (duration / 1.seconds)
        model.dataModel.apidModelMap.getValue(APID_APSS_CONTINUOUS_SCI).increaseDataRate(dataRate)
        // Dump internal data invokes a delay
        model.apssModel.dumpInternalData(duration)
        model.dataModel.apidModelMap.getValue(APID_APSS_CONTINUOUS_SCI).increaseDataRate(-dataRate)
    }
}