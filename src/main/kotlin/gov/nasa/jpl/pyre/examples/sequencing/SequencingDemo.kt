package gov.nasa.jpl.pyre.examples.sequencing

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.Serialization.alias
import gov.nasa.jpl.pyre.examples.sequencing.activities.LoadSequence
import gov.nasa.jpl.pyre.examples.sequencing.commands.ModeledCommands.modeledCommands
import gov.nasa.jpl.pyre.examples.sequencing.commands.telecom.*
import gov.nasa.jpl.pyre.examples.sequencing.sequence_engine.SequencingModel
import gov.nasa.jpl.pyre.examples.sequencing.telecom.TelecomModel
import gov.nasa.jpl.pyre.flame.plans.activity
import gov.nasa.jpl.pyre.flame.plans.activitySerializersModule
import gov.nasa.jpl.pyre.flame.tasks.subContext
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlin.time.Instant

class SequencingDemo(
    context: SparkInitContext,
) {
    val telecom: TelecomModel
    val sequencing: SequencingModel

    init {
        with (context) {
            telecom = TelecomModel(subContext("telecom"))
            sequencing = SequencingModel(
                modeledCommands(this@SequencingDemo),
                subContext("sequencing"),
            )
        }
    }

    companion object {
        val JSON_FORMAT = Json {
            serializersModule = SerializersModule {
                contextual(Instant::class, String.serializer().alias(
                    InvertibleFunction.of(Instant::parse, Instant::toString)))
                include(activitySerializersModule {
                    // Planning activities
                    activity(LoadSequence::class)

                    // Command activities
                    activity(RadioPowerOn::class)
                    activity(RadioPowerOff::class)
                    activity(TwtaPowerOn::class)
                    activity(TwtaPowerOff::class)
                })
            }
        }
    }
}