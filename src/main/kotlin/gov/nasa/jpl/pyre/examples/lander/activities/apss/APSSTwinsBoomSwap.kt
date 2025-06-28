package gov.nasa.jpl.pyre.examples.lander.activities.apss

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.ember.JsonValue
import gov.nasa.jpl.pyre.ember.JsonValue.*
import gov.nasa.jpl.pyre.ember.Serializer
import gov.nasa.jpl.pyre.ember.plus
import gov.nasa.jpl.pyre.ember.times
import gov.nasa.jpl.pyre.examples.lander.Mission
import gov.nasa.jpl.pyre.examples.lander.models.apss.APSSModel.Component.*
import gov.nasa.jpl.pyre.examples.lander.models.power.PowerModel.PelItem
import gov.nasa.jpl.pyre.examples.lander.models.power.PowerModel.PelItem.*
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.flame.serialization.get
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.resources.set
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope
import gov.nasa.jpl.pyre.spark.tasks.delayUntil
import java.util.function.Consumer

class APSSTwinsBoomSwap(
    val duration: Duration = 20 * MINUTE,
): Activity<Mission, Unit> {
    context(SparkTaskScope@SparkTaskScope<Unit>)
    override suspend fun effectModel(model: Mission) {
        val end = simulationClock.getValue() + duration

        val twinsMyOn = model.apssModel.components.getValue(TWINS_MY).state
        val twinsPyOn = model.apssModel.components.getValue(TWINS_PY).state

        val twinsMyPelItems = listOf(
            APSS_TWINSPY_EXT,
            APSS_TWINSPY_PAE,
            APSS_HEATPY_EXT,
            APSS_HEATPY_PAE,
        )
        val twinsPyPelItems = listOf(
            APSS_TWINSMY_EXT,
            APSS_TWINSMY_PAE,
            APSS_HEATMY_EXT,
            APSS_HEATMY_PAE
        )

        // If not exactly one boom is on, we exit without doing anything and trigger a constraint
        if (twinsMyOn.getValue() && twinsPyOn.getValue()) {
            // If +Y is off, turn it on
            twinsPyOn.set(true)
            twinsPyPelItems.forEach { model.powerModel.pelStates.getValue(it).set("on") }
            // Turn -Y off
            twinsMyOn.set(false)
            twinsMyPelItems.forEach { model.powerModel.pelStates.getValue(it).set("off") }
        } else if (!twinsMyOn.getValue() && twinsPyOn.getValue()) {
            // If -Y is off, turn it on
            twinsMyOn.set(true)
            twinsMyPelItems.forEach { model.powerModel.pelStates.getValue(it).set("on") }
            // Turn +Y off
            twinsPyOn.set(false)
            twinsPyPelItems.forEach { model.powerModel.pelStates.getValue(it).set("off") }
        }

        delayUntil(end)
    }

    companion object {
        val SERIALIZER: Serializer<APSSTwinsBoomSwap> = Serializer.of(InvertibleFunction.of(
            {
                JsonMap(mapOf(
                    "duration" to Duration.serializer().serialize(it.duration),
                ))
            },
            {
                APSSTwinsBoomSwap(
                    Duration.serializer().deserialize(it["duration"]),
                )
            }
        ))
    }
}