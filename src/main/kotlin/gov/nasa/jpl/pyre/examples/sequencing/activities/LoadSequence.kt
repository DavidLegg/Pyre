package gov.nasa.jpl.pyre.examples.sequencing.activities

import gov.nasa.jpl.pyre.examples.sequencing.SequencingDemo
import gov.nasa.jpl.pyre.examples.sequencing.sequence_engine.Sequence
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.spark.tasks.TaskScope
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromStream
import kotlin.io.path.inputStream

@Serializable
@SerialName("LoadSequence")
class LoadSequence(
    val sequenceFile: String
): Activity<SequencingDemo> {

    @OptIn(ExperimentalSerializationApi::class)
    context(scope: TaskScope)
    override suspend fun effectModel(model: SequencingDemo) {
        val sequence: Sequence = model.rootDir.resolve(sequenceFile).inputStream().use {
            SequencingDemo.JSON_FORMAT.decodeFromStream(it)
        }
        val engine = requireNotNull(model.sequencing.nextAvailable()) {
            "No available sequence engine!"
        }
        engine.load(sequence)
    }
}