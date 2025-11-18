package gov.nasa.jpl.pyre.examples.sequencing.sequence_engine

import gov.nasa.jpl.pyre.kernel.Duration.Companion.MILLISECOND
import gov.nasa.jpl.pyre.kernel.times
import gov.nasa.jpl.pyre.examples.sequencing.SequencingDemo
import gov.nasa.jpl.pyre.examples.sequencing.fsw.FswModel.GlobalIntVarName
import gov.nasa.jpl.pyre.examples.sequencing.primeness.SideIndicator.PRIME
import gov.nasa.jpl.pyre.examples.sequencing.sequence_engine.SequenceEngine.BranchIndicator
import gov.nasa.jpl.pyre.examples.sequencing.sequence_engine.SequenceEngine.CommandBlockDescription
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.subContext
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import kotlin.collections.map
import kotlin.ranges.IntRange

class SequencingModel(
    val commandHandlers: Map<String, CommandBehavior>,
    model: SequencingDemo,
    context: InitScope,
    val numberOfEngines: Int = 32,
    blockTypes: Map<String, CommandBlockDescription> = defaultBlockTypes(model),
) {
    val engines: List<SequenceEngine>

    init {
        with (context) {
            subContext("sequence_engine") {
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
        fun defaultBlockTypes(model: SequencingDemo): Map<String, CommandBlockDescription> = mapOf(
            "IF" to CommandBlockDescription(
                start = mapOf(
                    "SEQ_IF" to { command ->
                        val variable = GlobalIntVarName.valueOf((command.args[0] as Command.Arg.StringArg).value)
                        val value = model.fsw.globals[PRIME].ints[variable.ordinal].getValue()
                        if (value != 0) {
                            BranchIndicator.CONTINUE
                        } else {
                            BranchIndicator.NEXT
                        }
                    },
                ),
                branch = mapOf(
                    "SEQ_ELSE" to { BranchIndicator.CONTINUE },
                ),
                end = mapOf(
                    "SEQ_END_IF" to { BranchIndicator.CONTINUE },
                ),
            ),
            "WHILE" to CommandBlockDescription(
                start = mapOf(
                    "SEQ_WHILE" to { command ->
                        val variable = GlobalIntVarName.valueOf((command.args[0] as Command.Arg.StringArg).value)
                        val value = model.fsw.globals[PRIME].ints[variable.ordinal].getValue()
                        if (value != 0) {
                            BranchIndicator.CONTINUE
                        } else {
                            BranchIndicator.EXIT
                        }
                   },
                ),
                branch = mapOf(
                    "SEQ_BREAK" to { BranchIndicator.EXIT },
                    "SEQ_CONTINUE" to { BranchIndicator.START },
                ),
                end = mapOf(
                    "SEQ_END_WHILE" to { BranchIndicator.START },
                ),
            )
        )
        val DISPATCH_PERIOD = 125 * MILLISECOND
    }
}