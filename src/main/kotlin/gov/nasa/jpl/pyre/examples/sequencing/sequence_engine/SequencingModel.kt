package gov.nasa.jpl.pyre.examples.sequencing.sequence_engine

import gov.nasa.jpl.pyre.ember.Duration.Companion.MILLISECOND
import gov.nasa.jpl.pyre.ember.times
import gov.nasa.jpl.pyre.examples.sequencing.sequence_engine.SequenceEngine.BranchIndicator
import gov.nasa.jpl.pyre.examples.sequencing.sequence_engine.SequenceEngine.CommandBlockDescription
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.tasks.SparkInitScope
import gov.nasa.jpl.pyre.spark.tasks.SparkInitScope.Companion.subContext
import gov.nasa.jpl.pyre.spark.tasks.TaskScope
import kotlin.collections.map
import kotlin.ranges.IntRange

class SequencingModel(
    val commandHandlers: Map<String, CommandBehavior>,
    context: SparkInitScope,
    val numberOfEngines: Int = 32,
    blockTypes: Map<String, CommandBlockDescription> = DEFAULT_BLOCK_TYPES,
) {
    // TODO: Global variable modeling

    val engines: List<SequenceEngine>

    init {
        with (context) {
            with (subContext("sequence_engine")) {
                engines = IntRange(0, numberOfEngines).map { i ->
                    SequenceEngine(
                        blockTypes,
                        commandHandlers,
                        DISPATCH_PERIOD,
                        subContext(i.toString()),
                    )
                }
            }
        }
    }

    context (scope: TaskScope)
    suspend fun nextAvailable(): SequenceEngine? =
        engines.firstOrNull { !it.isLoaded.getValue() }

    companion object {
        // TODO: Heuristics for control flow
        val DEFAULT_BLOCK_TYPES: Map<String, CommandBlockDescription> = mapOf(
            "IF" to CommandBlockDescription(
                start = mapOf(
                    "IF" to { BranchIndicator.CONTINUE },
                ),
                branch = mapOf(
                    "ELSE" to { BranchIndicator.END },
                ),
                end = mapOf(
                    "END_IF" to { BranchIndicator.EXIT },
                ),
            ),
            "WHILE" to CommandBlockDescription(
                start = mapOf(
                    "WHILE" to { BranchIndicator.EXIT },
                ),
                branch = mapOf(
                    "BREAK" to { BranchIndicator.EXIT },
                    "CONTINUE" to { BranchIndicator.START },
                ),
                end = mapOf(
                    "END_WHILE" to { BranchIndicator.START },
                ),
            )
        )
        val DISPATCH_PERIOD = 125 * MILLISECOND
    }
}