package gov.nasa.jpl.pyre.examples.lander.activities.apss

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.ember.Duration.Companion.SECOND
import gov.nasa.jpl.pyre.ember.JsonValue.*
import gov.nasa.jpl.pyre.ember.Serializer
import gov.nasa.jpl.pyre.ember.ratioOver
import gov.nasa.jpl.pyre.ember.times
import gov.nasa.jpl.pyre.examples.lander.Mission
import gov.nasa.jpl.pyre.examples.lander.models.data.DataConfig.APID.APID_APSS_CONTINUOUS_SCI
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.flame.serialization.asDouble
import gov.nasa.jpl.pyre.flame.serialization.get
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope

class APSSPaeDataRecovery(
    val duration: Duration = 27 * MINUTE,
    val dataVolume: Double = 0.0, // Mbits
): Activity<Mission, Unit> {
    context(SparkTaskScope@SparkTaskScope<Unit>)
    override suspend fun effectModel(model: Mission) {
        val dataRate = dataVolume / (duration ratioOver SECOND)
        model.dataModel.apidModelMap.getValue(APID_APSS_CONTINUOUS_SCI).increaseDataRate(dataRate)
        // Dump internal data invokes a delay
        model.apssModel.dumpInternalData(duration)
        model.dataModel.apidModelMap.getValue(APID_APSS_CONTINUOUS_SCI).increaseDataRate(-dataRate)
    }

    companion object {
        val SERIALIZER: Serializer<APSSPaeDataRecovery> = Serializer.of(InvertibleFunction.of(
            {
                JsonMap(mapOf(
                    "duration" to Duration.serializer().serialize(it.duration),
                    "dataVolume" to JsonDouble(it.dataVolume),
                ))
            },
            {
                APSSPaeDataRecovery(
                    Duration.serializer().deserialize(it["duration"]),
                    it["dataVolume"].asDouble(),
                )
            }
        ))
    }
}