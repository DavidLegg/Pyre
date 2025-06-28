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
import gov.nasa.jpl.pyre.examples.lander.models.data.DataConfig
import gov.nasa.jpl.pyre.examples.lander.models.data.DataConfig.APID.*
import gov.nasa.jpl.pyre.examples.lander.models.power.PowerModel
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.flame.serialization.asDouble
import gov.nasa.jpl.pyre.flame.serialization.get
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope

class APSSGeneric(
    val duration: Duration = 7 * MINUTE,
    val continuousSciDataVolume: Double = 0.0, // Mbits
    val fswSpecialEvrDataVolume: Double = 0.0, // Mbits
    val externalEnergyUsed: Double = 0.0, // Wh
    val eboxEnergyUsed: Double = 0.0, // Wh
): Activity<Mission, Unit> {
    context(SparkTaskScope@SparkTaskScope<Unit>)
    override suspend fun effectModel(model: Mission) {
        val durSec = duration ratioOver SECOND

        val externalWatts = (externalEnergyUsed * 60 * 60) / durSec
        val eboxWatts = (eboxEnergyUsed * 60 * 60) / durSec

        model.powerModel.genericPowerUsed.set(externalWatts + eboxWatts)
        pelItems.forEach { model.powerModel.pelStates.getValue(it).set("on") }

        val continuousSciDataRate = continuousSciDataVolume / durSec
        val fswSpecialEvrDataRate = fswSpecialEvrDataVolume / durSec
        model.dataModel.apidModelMap.getValue(APID_APSS_CONTINUOUS_SCI).increaseDataRate(continuousSciDataRate)
        model.dataModel.apidModelMap.getValue(APID_APSS_FSW_SPECIAL_EVR).increaseDataRate(fswSpecialEvrDataRate)
        delay(duration)
        model.dataModel.apidModelMap.getValue(APID_APSS_CONTINUOUS_SCI).increaseDataRate(-continuousSciDataRate)
        model.dataModel.apidModelMap.getValue(APID_APSS_FSW_SPECIAL_EVR).increaseDataRate(-fswSpecialEvrDataRate)

        model.powerModel.genericPowerUsed.set(1.0)
        pelItems.forEach { model.powerModel.pelStates.getValue(it).set("off") }
    }

    companion object {
        private val pelItems = listOf(PowerModel.PelItem.APSS_GEN_EXT, PowerModel.PelItem.APSS_GEN_PAE)

        val SERIALIZER: Serializer<APSSGeneric> = Serializer.of(InvertibleFunction.of(
            {
                JsonMap(mapOf(
                    "duration" to Duration.serializer().serialize(it.duration),
                    "continuousSciDataVolume" to JsonDouble(it.continuousSciDataVolume),
                    "fswSpecialEvrDataVolume" to JsonDouble(it.fswSpecialEvrDataVolume),
                    "externalEnergyUsed" to JsonDouble(it.externalEnergyUsed),
                    "eboxEnergyUsed" to JsonDouble(it.eboxEnergyUsed),
                ))
            },
            {
                APSSGeneric(
                    Duration.serializer().deserialize(it["duration"]),
                    it["continuousSciDataVolume"].asDouble(),
                    it["fswSpecialEvrDataVolume"].asDouble(),
                    it["externalEnergyUsed"].asDouble(),
                    it["eboxEnergyUsed"].asDouble(),
                )
            }
        ))
    }
}