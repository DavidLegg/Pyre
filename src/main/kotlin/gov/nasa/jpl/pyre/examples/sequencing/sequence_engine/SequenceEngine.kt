package gov.nasa.jpl.pyre.examples.sequencing.sequence_engine

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.plus
import gov.nasa.jpl.pyre.ember.toPyreDuration
import gov.nasa.jpl.pyre.examples.sequencing.sequence_engine.SequenceEngine.BranchIndicator.*
import gov.nasa.jpl.pyre.examples.sequencing.sequence_engine.TimeTag.*
import gov.nasa.jpl.pyre.flame.tasks.await
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResource
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResourceOperations.and
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResourceOperations.not
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResourceOperations.or
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.isNotNull
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.spark.resources.discrete.IntResourceOperations.increment
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableBooleanResource
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableIntResource
import gov.nasa.jpl.pyre.spark.resources.discrete.StringResource
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.resources.timer.TimerResourceOperations.greaterThanOrEquals
import gov.nasa.jpl.pyre.spark.tasks.SparkContextExtensions.now
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope
import gov.nasa.jpl.pyre.spark.tasks.task
import gov.nasa.jpl.pyre.spark.tasks.whenever
import kotlin.collections.forEach
import kotlin.time.Instant

class SequenceEngine(
    val blockTypes: List<CommandBlockDescription> = emptyList(),
    val commandHandlers: Map<String, CommandBehavior>,
    val dispatchPeriod: Duration,
    context: SparkInitContext,
) {
    /**
     * Describes a "block" of commands, used to perform structured control flow within a sequence.
     *
     * @param start Map from stems indicating the start of this kind of block to a function which indicates which branch of the block to take.
     * @param end Map from stems indicating the end of this kind of block to a function which indicates which branch of the block to take.
     * @param branch Map from stems indicating an intermediate branch in this kind of block to a function which indicates which branch of the block to take.
     */
    class CommandBlockDescription(
        val start: Map<String, (Command) -> BranchIndicator>,
        val end: Map<String, (Command) -> BranchIndicator>,
        val branch: Map<String, (Command) -> BranchIndicator> = emptyMap(),
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

    private val loadedSequence: MutableDiscreteResource<Sequence?>
    val loadedSequenceName: StringResource
    val isLoaded: BooleanResource
    private val commandIndex: MutableIntResource
    // Slightly dangerous pattern: private mutable state not in a cell.
    // This is a performance-enhancing cache, and does not store critical state.
    // Upon a save/restore cycle, this will be reset to null, and re-calculated the next time it's needed.
    // The behavior of this class will thus be identical across a save/restore cycle.
    // IMPORTANT: If you do not understand why it's safe to keep this state outside a cell, do not copy this pattern.
    private var sequenceBehavior: Array<CommandBehavior>? = null
    private val _isActive: MutableBooleanResource
    val isActive: BooleanResource get() = _isActive

    private val lastDispatchTime: MutableDiscreteResource<Instant?>
    private val lastDispatchedCommandIndex: MutableIntResource
    private val lastDispatchedCommandComplete: MutableBooleanResource

    // TODO: Local variable modeling

    init {
        with (context) {
            loadedSequence = discreteResource("loaded_sequence", null)
            loadedSequenceName = map(loadedSequence) { it?.name ?: "" }
            isLoaded = loadedSequence.isNotNull()
            commandIndex = discreteResource("command_index", 0)
            _isActive = discreteResource("is_active", false)

            lastDispatchTime = discreteResource("last_dispatch_time", null)
            lastDispatchedCommandIndex = discreteResource("last_dispatched_command_index", -1)
            lastDispatchedCommandComplete = discreteResource("last_dispatched_command_complete", false)

            spawn("Run Sequence Engine", whenever(isLoaded and isActive) {
                val sequence = loadedSequence.getValue()!!
                if (sequenceBehavior == null) {
                    // UNSAFE BLOCK! Do not put any cell reads/writes in this block
                    // Doing so may cause the save/restore cycle to break
                    sequenceBehavior = determineSequenceBehavior(sequence)
                }
                val i = commandIndex.getValue()
                sequenceBehavior?.let { sequenceBehavior ->
                    if (i in sequenceBehavior.indices) {
                        // TODO: Build an "interruptible task" utility to clean this up
                        val (timeTag, command) = sequence.commands[i]
                        val dispatchReady = when (timeTag) {
                            CommandComplete -> lastDispatchedCommandComplete
                            is Relative -> simulationClock greaterThanOrEquals (timeTag.duration + simulationClock.getValue())
                            is Absolute -> simulationClock greaterThanOrEquals (timeTag.time - simulationEpoch).toPyreDuration()
                        }
                        val dispatchPeriodElapsed = simulationClock greaterThanOrEquals (dispatchPeriod + simulationClock.getValue())
                        await((dispatchPeriodElapsed and dispatchReady and isActive) or isLoaded.not())
                        // Check that the engine is still loaded, to handle unloading the engine unexpectedly
                        if (isLoaded.getValue()) {
                            spawn("Run Command $i", task {
                                lastDispatchTime.set(now())
                                lastDispatchedCommandIndex.set(i)
                                lastDispatchedCommandComplete.set(false)
                                sequenceBehavior[i].effectModel(command)
                                // Check that another command hasn't been dispatched in the meantime
                                if (lastDispatchedCommandIndex.getValue() == i) {
                                    lastDispatchedCommandComplete.set(true)
                                }
                            })
                        }
                    } else {
                        unload()
                    }
                }
            })
        }
    }

    private class CommandBlock(
        val type: CommandBlockDescription,
        var startCommandIndex: Int? = null,
        var endCommandIndex: Int? = null,
        val branchCommandIndices: MutableList<Int> = mutableListOf(),
    )

    context (scope: SparkTaskScope)
    suspend fun load(sequence: Sequence) {
        require(!isLoaded.getValue()) { "Sequence engine is already loaded!" }
        loadedSequence.set(sequence)
        commandIndex.set(0)
        lastDispatchedCommandIndex.set(-1)
        lastDispatchedCommandComplete.set(true)

        if (sequence.loadAndGo) activate()
    }

    context (scope: SparkTaskScope)
    suspend fun activate() {
        _isActive.set(true)
    }

    context (scope: SparkTaskScope)
    suspend fun deactivate() {
        _isActive.set(false)
    }

    context (scope: SparkTaskScope)
    suspend fun unload() {
        deactivate()
        loadedSequence.set(null)
        commandIndex.set(0)
        lastDispatchedCommandIndex.set(-1)
        lastDispatchedCommandComplete.set(false)
        sequenceBehavior = null
    }

    private fun determineSequenceBehavior(sequence: Sequence): Array<CommandBehavior> {
        // Default behavior for every command is just to run the handler and increment the command index
        val commandBehavior: Array<CommandBehavior> = sequence.commands.map { (timeTag, command) ->
            val behavior = commandHandlers.getOrDefault(command.stem, CommandBehavior.ignore)
            CommandBehavior { c ->
                behavior.effectModel(c)
                commandIndex.increment()
            }
        }.toTypedArray()

        // Augment the default behavior by detecting control flow:
        val blockStack: MutableList<CommandBlock> = mutableListOf()
        for ((index, timedCommand) in sequence.commands.withIndex()) {
            // The top block in the stack might be ending:
            blockStack.lastOrNull()?.let { block ->
                if (timedCommand.command.stem in block.type.end) {
                    block.endCommandIndex = index
                    // Having completed this block, process all the behaviors for it
                    (block.branchCommandIndices + block.startCommandIndex!! + block.endCommandIndex!!).forEach {
                        commandBehavior[it] = controlFlowCommandBehavior(it, sequence, block)
                    }
                    // Then, remove the block from the stack
                    blockStack.removeLast()
                }
            }

            // Any block in the stack might be splitting (!)
            // This allows for things like while... if... continue... end if... end while...
            // Match the split to the top-most matching block
            for (block in blockStack.asReversed()) {
                if (timedCommand.command.stem in block.type.branch) {
                    block.branchCommandIndices += index
                    break
                }
            }

            // Or a new block could be starting
            for (blockType in blockTypes) {
                if (timedCommand.command.stem in blockType.start) {
                    blockStack += CommandBlock(
                        blockType,
                        startCommandIndex = index,
                    )
                }
            }
        }

        return commandBehavior
    }

    private fun controlFlowCommandBehavior(
        index: Int,
        sequence: Sequence,
        activeBlock: CommandBlock,
    ): CommandBehavior {
        val command = sequence.commands[index].command
        val generalBehavior = commandHandlers.getOrDefault(command.stem, CommandBehavior.ignore)
        val controlFlowBehavior: (Command) -> BranchIndicator = when (index) {
            activeBlock.startCommandIndex -> activeBlock.type.start.getValue(command.stem)
            activeBlock.endCommandIndex -> activeBlock.type.end.getValue(command.stem)
            else -> activeBlock.type.branch.getValue(command.stem)
        }
        return CommandBehavior { c ->
            generalBehavior.effectModel(c)
            val nextCommandIndex = when (controlFlowBehavior(c)) {
                CONTINUE -> index + 1
                START -> activeBlock.startCommandIndex!!
                END -> activeBlock.endCommandIndex!!
                EXIT -> activeBlock.endCommandIndex!! + 1
                NEXT -> (listOf(activeBlock.startCommandIndex!!)
                        + activeBlock.branchCommandIndices
                        + activeBlock.endCommandIndex!!
                        ).first { it > index }
            }
            commandIndex.set(nextCommandIndex)
        }
    }
}