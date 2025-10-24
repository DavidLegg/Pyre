package gov.nasa.jpl.pyre.examples.sequencing

import gov.nasa.jpl.pyre.utilities.InvertibleFunction
import gov.nasa.jpl.pyre.kernel.Serialization.alias
import gov.nasa.jpl.pyre.examples.sequencing.activities.ActivateSequence
import gov.nasa.jpl.pyre.examples.sequencing.activities.LoadSequence
import gov.nasa.jpl.pyre.examples.sequencing.activities.UnloadSequence
import gov.nasa.jpl.pyre.examples.sequencing.commands.ALL_MODELED_COMMANDS
import gov.nasa.jpl.pyre.examples.sequencing.fsw.FswModel
import gov.nasa.jpl.pyre.examples.sequencing.sequence_engine.SequencingModel
import gov.nasa.jpl.pyre.examples.sequencing.telecom.TelecomModel
import gov.nasa.jpl.pyre.flame.plans.activities
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.subContext
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
                this@SequencingDemo,
                subContext("sequencing"),
            )
        }
    }

    companion object {
        val JSON_FORMAT = Json {
            serializersModule = SerializersModule {
                contextual(Instant::class, String.serializer().alias(
                    InvertibleFunction.of(Instant::parse, Instant::toString)))
                activities {
                    // Planning activities
                    activity(LoadSequence::class)
                    activity(ActivateSequence::class)
                    activity(UnloadSequence::class)

                    // Include modeled commands
                    ALL_MODELED_COMMANDS.includeModeledCommands()
                }
            }
        }
    }
}