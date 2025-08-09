package gov.nasa.jpl.pyre.examples.sequencing

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.Serialization.alias
import gov.nasa.jpl.pyre.examples.sequencing.activities.ActivateSequence
import gov.nasa.jpl.pyre.examples.sequencing.activities.LoadSequence
import gov.nasa.jpl.pyre.examples.sequencing.commands.ALL_MODELED_COMMANDS
import gov.nasa.jpl.pyre.examples.sequencing.fsw.FswModel
import gov.nasa.jpl.pyre.examples.sequencing.sequence_engine.SequencingModel
import gov.nasa.jpl.pyre.examples.sequencing.telecom.TelecomModel
import gov.nasa.jpl.pyre.flame.plans.activity
import gov.nasa.jpl.pyre.flame.plans.activitySerializersModule
import gov.nasa.jpl.pyre.spark.tasks.InitScope
import gov.nasa.jpl.pyre.spark.tasks.InitScope.Companion.subContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.nio.file.Path
import kotlin.time.Instant

class SequencingDemo(
    val rootDir: Path,
    context: InitScope,
) {
    val fsw: FswModel
    val telecom: TelecomModel
    val sequencing: SequencingModel

    init {
        with (context) {
            fsw = FswModel(subContext("fsw"))
            telecom = TelecomModel(subContext("telecom"))
            sequencing = SequencingModel(
                ALL_MODELED_COMMANDS.modeledCommands(this@SequencingDemo),
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
                    activity(ActivateSequence::class)

                    // Include modeled commands
                    ALL_MODELED_COMMANDS.includeModeledCommands()
                })
            }
        }
    }
}