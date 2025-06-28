package gov.nasa.jpl.pyre.examples.lander.activities.apss

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.ember.JsonValue.*
import gov.nasa.jpl.pyre.ember.Serializer
import gov.nasa.jpl.pyre.ember.times
import gov.nasa.jpl.pyre.examples.lander.Mission
import gov.nasa.jpl.pyre.examples.lander.models.apss.APSSModel
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.flame.serialization.asDouble
import gov.nasa.jpl.pyre.flame.serialization.get
import gov.nasa.jpl.pyre.spark.reporting.BasicSerializers.enumSerializer
import gov.nasa.jpl.pyre.spark.reporting.BasicSerializers.mapSerializer
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope

class APSSContinuousConfigFileUpdate(
    val duration: Duration = 20 * MINUTE,
    val componentInRates: Map<APSSModel.Component, APSSModel.ComponentRate> = mapOf(),
    val componentOutRates: Map<APSSModel.Component, APSSModel.ComponentRate> = mapOf(),
    val transferCoef: Double = 0.2088, // Mbit/s
): Activity<Mission, Unit> {

    context(SparkTaskScope@SparkTaskScope<Unit>)
    override suspend fun effectModel(model: Mission) {
        model.apssModel.transferRate.set(transferCoef)

        // Set all defined in and out rates
        for ((component, rate) in componentInRates) {
            model.apssModel.components.getValue(component).inRate.set(rate)
        }
        for ((component, rate) in componentOutRates) {
            model.apssModel.components.getValue(component).outRate.set(rate)
        }
    }

    companion object {
        private val componentRateSerializer: Serializer<Map<APSSModel.Component, APSSModel.ComponentRate>> =
            mapSerializer(
                InvertibleFunction.of(APSSModel.Component::toString, ::enumValueOf),
                APSSModel.ComponentRate.SERIALIZER)
        val SERIALIZER: Serializer<APSSContinuousConfigFileUpdate> = Serializer.of(InvertibleFunction.of(
            {
                JsonMap(mapOf(
                    "duration" to Duration.serializer().serialize(it.duration),
                    "componentInRates" to componentRateSerializer.serialize(it.componentInRates),
                    "componentOutRates" to componentRateSerializer.serialize(it.componentOutRates),
                    "transferCoef" to JsonDouble(it.transferCoef),
                ))
            },
            {
                APSSContinuousConfigFileUpdate(
                    Duration.serializer().deserialize(it["duration"]),
                    componentRateSerializer.deserialize(it["componentInRates"]),
                    componentRateSerializer.deserialize(it["componentOutRates"]),
                    it["transferCoef"].asDouble(),
                )
            }
        ))
    }
}