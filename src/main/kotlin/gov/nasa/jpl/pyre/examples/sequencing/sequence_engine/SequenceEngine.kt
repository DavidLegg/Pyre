package gov.nasa.jpl.pyre.examples.sequencing.sequence_engine

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.plus
import gov.nasa.jpl.pyre.ember.toPyreDuration
import gov.nasa.jpl.pyre.examples.sequencing.sequence_engine.TimeTag.Absolute
import gov.nasa.jpl.pyre.examples.sequencing.sequence_engine.TimeTag.CommandComplete
import gov.nasa.jpl.pyre.examples.sequencing.sequence_engine.TimeTag.Relative
import gov.nasa.jpl.pyre.spark.reporting.Reporting.register
import gov.nasa.jpl.pyre.spark.reporting.Reporting.report
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResource
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResourceOperations.and
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResourceOperations.not
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResourceOperations.or
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.isNotNull
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.spark.resources.discrete.IntResourceOperations.increment
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableBooleanResource
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableIntResource
import gov.nasa.jpl.pyre.spark.resources.discrete.StringResource
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.resources.named
import gov.nasa.jpl.pyre.spark.resources.timer.TimerResourceOperations.greaterThanOrEquals
import gov.nasa.jpl.pyre.spark.tasks.Reactions.await
import gov.nasa.jpl.pyre.spark.tasks.Reactions.whenever
import gov.nasa.jpl.pyre.spark.tasks.ResourceScope.Companion.now
import gov.nasa.jpl.pyre.spark.tasks.InitScope
import gov.nasa.jpl.pyre.spark.tasks.SparkScope.Companion.simulationClock
import gov.nasa.jpl.pyre.spark.tasks.SparkScope.Companion.simulationEpoch
import gov.nasa.jpl.pyre.spark.tasks.TaskScope
import gov.nasa.jpl.pyre.spark.tasks.TaskScope.Companion.spawn
import gov.nasa.jpl.pyre.spark.tasks.task
import kotlinx.serialization.Serializable
import kotlin.time.Instant

class SequenceEngine(
    val blockTypes: Map<String, CommandBlockDescription> = emptyMap(),
    val commandHandlers: Map<String, CommandBehavior>,
    val dispatchPeriod: Duration,
    context: InitScope,
) {
    /**
     * Describes a "block" of commands, used to perform structured control flow within a sequence.
     *
     * @param start Map from stems indicating the start of this kind of block to a function which indicates which branch of the block to take.
     * @param end Map from stems indicating the end of this kind of block to a function which indicates which branch of the block to take.
     * @param branch Map from stems indicating an intermediate branch in this kind of block to a function which indicates which branch of the block to take.
     */
    class CommandBlockDescription(
        val start: Map<String, suspend context (TaskScope) (Command) -> BranchIndicator>,
        val end: Map<String, suspend context (TaskScope) (Command) -> BranchIndicator>,
        val branch: Map<String, suspend context (TaskScope) (Command) -> BranchIndicator> = emptyMap(),
    )

    enum class BranchIndicator {
        /**
         * Move on to the next command, no change to control flow.
         */
        CONTINUE,

        /**
         * Return to the start command for this block.
         */
        START,

        /**
         * Skip to the last command for this block.
         */
        END,

        /**
         * Skip to one after the last command for this block.
         */
        EXIT,

        /**
         * Skip to the next control-flow command for this block.
         * E.g., from "IF" skip to "ELSE"
         */
        NEXT
    }

    @Serializable
    data class ControlFlowPoint(
        val blockType: String,
        val blockLocation: BlockLocation,
        val branchResults: Map<BranchIndicator, Int>,
    )

    enum class BlockLocation { START, INNER, END }

    // TODO: Expose read-only resources as public getters

    private val loadedSequence: MutableDiscreteResource<Sequence?>
    val loadedSequenceName: StringResource
    val isLoaded: BooleanResource

    private val controlFlowPoints: MutableDiscreteResource<Map<Int, ControlFlowPoint>>
    private val lastDispatchTime: MutableDiscreteResource<Instant>
    private val lastDispatchedCommandComplete: MutableBooleanResource
    private val dispatchCounter: MutableIntResource
    private val isActive: MutableBooleanResource
    private val commandIndex: MutableIntResource

    init {
        with (context) {
            loadedSequence = discreteResource("loaded_sequence", null)
            loadedSequenceName = (map(loadedSequence) { it?.name ?: "" } named { "loaded_sequence_name" }).also { register(it) }
            isLoaded = (loadedSequence.isNotNull() named { "is_loaded" }).also { register(it) }

            controlFlowPoints = discreteResource("control_flow_points", emptyMap())
            lastDispatchTime = registeredDiscreteResource("last_dispatch_time", Instant.DISTANT_PAST)
            lastDispatchedCommandComplete = registeredDiscreteResource("last_dispatched_command_complete", false)
            dispatchCounter = registeredDiscreteResource("dispatch_counter", 0)
            isActive = registeredDiscreteResource("is_active", false)
            commandIndex = registeredDiscreteResource("command_index", 0)

            spawn("run", whenever(isLoaded and isActive) {
                val sequence = requireNotNull(loadedSequence.getValue())
                val index = commandIndex.getValue()
                if (index in sequence.commands.indices) {
                    val (timeTag, command) = sequence.commands[index]

                    // The engine always waits at least one dispatch period since the last command dispatch
                    // It also waits for the dispatch time indicated by the time tag.
                    // It also waits for the engine to be active, if it gets deactivated during the wait somehow
                    await((dispatchPeriodElapsed() and afterIndicatedDispatchTime(timeTag) and isActive) or !isLoaded)
                    // If we were unloaded while waiting to dispatch this command, abort this dispatch
                    if (!isLoaded.getValue()) return@whenever
                    dispatch(command)

                    // Look for any unusual control flow happening at this command
                    val cfPoint = controlFlowPoints.getValue()[index]
                    if (cfPoint != null) {
                        // If there is some, look up the governing control flow behavior
                        val block = blockTypes.getValue(cfPoint.blockType)
                        val controlFlow = (when (cfPoint.blockLocation) {
                            BlockLocation.START -> block.start
                            BlockLocation.INNER -> block.branch
                            BlockLocation.END -> block.end
                        }).getValue(command.stem)
                        // Apply that control flow behavior to this command
                        val branchIndicator = controlFlow(command)
                        // Interpret the abstract branch indicator in the context of this CF point
                        val nextCommandIndex = cfPoint.branchResults.getValue(branchIndicator)
                        // And finally apply the resulting index to this engine
                        commandIndex.set(nextCommandIndex)
                    } else {
                        // If no unusual control flow is happening at this point, just move to the next command
                        commandIndex.increment()
                    }
                } else {
                    unload()
                }
            })
        }
    }

    context (scope: TaskScope)
    private suspend fun dispatchPeriodElapsed(): BooleanResource {
        return after(lastDispatchTime.getValue() + dispatchPeriod)
    }

    context (scope: TaskScope)
    private suspend fun afterIndicatedDispatchTime(timeTag: TimeTag): BooleanResource = when (timeTag) {
        is Absolute -> after(timeTag.time)
        CommandComplete -> lastDispatchedCommandComplete
        is Relative -> after(lastDispatchTime.getValue() + timeTag.duration)
    }

    context (scope: TaskScope)
    private fun after(time: Instant): BooleanResource =
        simulationClock greaterThanOrEquals (time - simulationEpoch).toPyreDuration()

    context (scope: TaskScope)
    private suspend fun dispatch(command: Command) {
        lastDispatchTime.set(now())
        lastDispatchedCommandComplete.set(false)
        dispatchCounter.increment()
        val currentDispatchCounter = dispatchCounter.getValue()
        report("commands", command)
        spawn("model ${command.stem}", task {
            commandHandlers[command.stem]?.effectModel(command)
            // If no other command was dispatched in the meantime, set the command complete flag
            if (dispatchCounter.getValue() == currentDispatchCounter) {
                lastDispatchedCommandComplete.set(true)
            }
        })
    }

    context (scope: TaskScope)
    suspend fun load(sequence: Sequence) {
        loadedSequence.set(sequence)
        controlFlowPoints.set(parse(sequence))
        commandIndex.set(0)
        // The engine starts with "last command complete" -
        // Sequences starting with command complete timing dispatch the first command immediately
        lastDispatchedCommandComplete.set(true)
        if (sequence.loadAndGo) activate()
    }

    context (scope: TaskScope)
    suspend fun activate() {
        isActive.set(true)
    }

    context (scope: TaskScope)
    suspend fun deactivate() {
        isActive.set(false)
    }

    context (scope: TaskScope)
    suspend fun unload() {
        deactivate()
        loadedSequence.set(null)
        controlFlowPoints.set(emptyMap())
    }

    private data class CommandBlock(
        val blockType: String,
        val start: Int,
        val branches: MutableList<Int> = mutableListOf(),
        val end: Int,
    )

    private fun parse(sequence: Sequence): Map<Int, ControlFlowPoint> {
        val result: MutableMap<Int, ControlFlowPoint> = mutableMapOf()
        val activeBlocks: MutableList<CommandBlock> = mutableListOf()

        fun collect(block: CommandBlock) {
            fun collectPoint(loc: BlockLocation, thisIndex: Int, nextIndex: Int) {
                result[thisIndex] = ControlFlowPoint(
                    block.blockType,
                    loc,
                    mapOf(
                        BranchIndicator.CONTINUE to thisIndex + 1,
                        BranchIndicator.START to block.start,
                        BranchIndicator.END to block.end,
                        BranchIndicator.EXIT to block.end + 1,
                        BranchIndicator.NEXT to nextIndex,
                    )
                )
            }

            collectPoint(BlockLocation.START, block.start, block.branches.firstOrNull() ?: block.end)
            collectPoint(BlockLocation.END, block.end, block.end + 1)
            for ((i, branch) in block.branches.withIndex()) {
                collectPoint(BlockLocation.INNER, branch, (block.branches.getOrNull(i + 1) ?: block.end))
            }
        }

        for ((cmdIndex, tc) in sequence.commands.withIndex()) {
            var matchedActiveBlock = false
            for ((blockIndex, block) in activeBlocks.asReversed().withIndex()) {
                val blockDescription = blockTypes.getValue(block.blockType)
                if (tc.command.stem in blockDescription.end) {
                    activeBlocks.asReversed().removeAt(blockIndex)
                    collect(block.copy(end=cmdIndex))
                    matchedActiveBlock = true
                    break
                } else if (tc.command.stem in blockDescription.branch) {
                    block.branches += cmdIndex
                    matchedActiveBlock = true
                    break
                }
            }
            if (!matchedActiveBlock) {
                for ((blockType, blockDescription) in blockTypes) {
                    if (tc.command.stem in blockDescription.start) {
                        activeBlocks += CommandBlock(
                            blockType,
                            start=cmdIndex,
                            // Implicitly close any active block at the end of the sequence.
                            // Most of the time, we should be explicitly closing them instead.
                            end=sequence.commands.size,
                        )
                        break
                    }
                }
            }
        }

        // Add any implicitly closed blocks. This is nominally a no-op.
        activeBlocks.forEach { collect(it) }

        return result
    }
}