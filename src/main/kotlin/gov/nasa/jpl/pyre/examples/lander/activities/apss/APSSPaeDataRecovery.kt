package gov.nasa.jpl.pyre.examples.lander.activities.apss

import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.kernel.Duration.Companion.SECOND
import gov.nasa.jpl.pyre.kernel.ratioOver
import gov.nasa.jpl.pyre.kernel.times
import gov.nasa.jpl.pyre.examples.lander.Mission
import gov.nasa.jpl.pyre.examples.lander.models.data.DataConfig.APID.APID_APSS_CONTINUOUS_SCI
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import kotlinx.serialization.Serializable

@Serializable
class APSSPaeDataRecovery(
    val duration: Duration = 27 * MINUTE,
    val dataVolume: Double = 0.0, // Mbits
): Activity<Mission> {
    context (scope: TaskScope)
    override suspend fun effectModel(model: Mission) {
        val dataRate = dataVolume / (duration ratioOver SECOND)
        model.dataModel.apidModelMap.getValue(APID_APSS_CONTINUOUS_SCI).increaseDataRate(dataRate)
        // Dump internal data invokes a delay
        model.apssModel.dumpInternalData(duration)
        model.dataModel.apidModelMap.getValue(APID_APSS_CONTINUOUS_SCI).increaseDataRate(-dataRate)
    }
}