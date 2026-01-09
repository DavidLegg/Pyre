package gov.nasa.jpl.pyre.incremental

import gov.nasa.jpl.pyre.incremental.KernelIncrementalSimulator.FrontierAction.*
import gov.nasa.jpl.pyre.incremental.KernelIncrementalSimulator.SimulationTimeIncrement.*
import gov.nasa.jpl.pyre.incremental.SimulationGraph.*
import gov.nasa.jpl.pyre.kernel.BasicInitScope
import gov.nasa.jpl.pyre.kernel.Cell
import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.Effect
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.PureTaskStep
import gov.nasa.jpl.pyre.kernel.ReportHandler
import gov.nasa.jpl.pyre.kernel.Task
import gov.nasa.jpl.pyre.utilities.identity
import java.util.PriorityQueue
import java.util.TreeMap
import kotlin.collections.plusAssign
import kotlin.reflect.KType
import kotlin.time.Instant

/**
 * Support for [GraphIncrementalPlanSimulation], which implements graph-based incremental simulation at the kernel level.
 */
class KernelIncrementalSimulator(
    constructModel: context (BasicInitScope) () -> Unit,
    kernelPlan: KernelPlan,
    private val reportHandler: IncrementalReportHandler
) {
    private val cellNodes: MutableMap<Cell<*>, TreeMap<SimulationTime, CellNode<*>>> = mutableMapOf()
    private val taskNodes: TreeMap<SimulationTime, TaskNode> = TreeMap()
    private val frontier: PriorityQueue<FrontierAction> = PriorityQueue(compareBy { it.time })

    init {
        var time = SimulationTimeImpl(kernelPlan.planStart)
        val basicInitScope = object : BasicInitScope {
            override fun <T : Any> allocate(
                name: Name,
                value: T,
                valueType: KType,
                stepBy: (T, Duration) -> T,
                mergeConcurrentEffects: (Effect<T>, Effect<T>) -> Effect<T>
            ): Cell<T> =
                // Create an incremental cell, and add its initial value to the graph
                IncrementalCellImpl(name, valueType, stepBy, mergeConcurrentEffects).also {
                    cellNodes[it] = TreeMap<SimulationTime, CellNode<*>>().apply {
                        put(time, CellWriteNode(time, value, null, identity()))
                        time += STEP
                    }
                }

            override fun <T> spawn(name: Name, step: PureTaskStep<T>) {
                // Add the task node into the graph. Implicitly, the continuation is not expanded.
                val rootTaskNode = RootTaskNode(time, name, step)
                time += STEP
                frontier += StartTask(rootTaskNode)
            }

            override fun <T> read(cell: Cell<T>): T {
                // Since there cannot be effects during init,
                // we may safely assume the first node to be the write node added during allocation
                return (getCellNodes(cell).firstEntry().value as CellWriteNode<T>).value
            }

            override fun <T> report(value: T) {
                // Create the uniquely-identifiable incremental report
                val report = IncrementalReport(time, value)
                time += STEP
                reportHandler.report(report)
            }
        }
        constructModel(basicInitScope)
        resolve()
    }

    fun run(planEdits: KernelPlanEdits) {
        TODO("Not yet implemented")
    }

    private fun resolve() {
        while (true) {
            when (val action = frontier.poll() ?: break) {
                is StartTask -> {
                    // Find an unoccupied branch of the appropriate batch to schedule this job in
                    var time = action.node.time + BATCH
                    while (taskNodes.containsKey(time)) {
                        time += BRANCH
                    }
                    val runTaskNode = RunTaskNode(
                        time,
                        action.node,
                        Task.of(action.node.name, action.node.task)
                    )
                    // Note that this new task exists
                    taskNodes[time] = runTaskNode
                    // Record it as the continuation of the root
                    action.node.continuation = runTaskNode
                    // Add it to the frontier, to be processed at an appropriate later time
                    frontier += ContinueTask(runTaskNode)
                }
                is ContinueTask -> {
                    @Suppress("UNCHECKED_CAST")
                    fun <V> getCellNode(cell: Cell<V>): CellNode<V> {
                        val thisCellsNodes = cellNodes.getValue(cell)
                        // Get the most recent cell node.
                        val (t, cellNode) = checkNotNull(thisCellsNodes.floorEntry(action.node.time))
                        // This may have come from another task running in this batch though!
                        if ((t as SimulationTimeImpl).isConcurrentWith(action.node.time)) {
                            // If so, set the time to the first possible time of this batch,
                            // and ask for the cell node before that. That'll get the last cell node
                            // of the batch before this, which must be a merge or step or something.
                            return checkNotNull(thisCellsNodes.lowerEntry(
                                action.node.time.batchStart())).value as CellNode<V>
                        }
                        return cellNode as CellNode<V>
                    }

                    // Expand this task node
                    val basicTaskActions = object : Task.BasicTaskActions {
                        // TODO: How do we handle stepping cells up?
                        // This code assumes that the cell is already stepped up to this time!
                        // But injecting a ton of step nodes is likely inefficient...
                        override fun <V> read(cell: Cell<V>): V = getCellNode(cell).also {
                            // Record the read in the graph
                            it.reads += action.node
                            action.node.reads += it
                        }.value

                        override fun <V> emit(
                            cell: Cell<V>,
                            effect: Effect<V>
                        ) {
                            // Look up the cell node we're writing to
                            val priorCellNode: CellNode<V> = getCellNode(cell)
                            // Build this write node
                            val writeNode: CellWriteNode<V> = CellWriteNode(
                                action.time,
                                effect(priorCellNode.value),
                                priorCellNode,
                                effect,
                                next = priorCellNode.next.toMutableList(),
                            )
                            // Fix the cell edges leading to this write node
                            priorCellNode.next.clear()
                            priorCellNode.next += writeNode
                            // Fix the cell edges leaving from this write node
                            // TODO: check if these nodes need re-processing
                            for (nextCellNode in writeNode.next) {
                                when (nextCellNode) {
                                    is CellMergeNode<V> -> {
                                        nextCellNode.prior.remove(priorCellNode)
                                        nextCellNode.prior.add(writeNode)
                                    }
                                    is CellStepNode<V> -> {
                                        nextCellNode.prior = writeNode
                                    }
                                    is CellWriteNode<V> -> {
                                        nextCellNode.prior = writeNode
                                    }
                                }
                                // Also schedule all of these nodes to be checked
                                frontier += CheckCell(nextCellNode)
                            }
                            for (read in priorCellNode.reads) {
                                if (read.time isCausallyAfter writeNode.time) {
                                    // This read could be affected by this write!

                                }
                            }
                            TODO()
                        }

                        override fun <V> report(value: V) {
                            TODO("Not yet implemented")
                        }
                    }
                    val result = checkNotNull(action.node.task) {
                        "Internal error! Task nodes on the frontier must have a non-null task."
                    }.runStep(basicTaskActions)
                    when (result) {
                        is Task.TaskStepResult.Await<*> -> TODO()
                        is Task.TaskStepResult.Complete<*> -> TODO()
                        is Task.TaskStepResult.NoOp<*> -> TODO()
                        is Task.TaskStepResult.Spawn<*, *> -> TODO()
                    }
                }
                is RerunTask -> {
                    // TODO: Remove this task node, all its continuations, and all its children (recursively)
                    // TODO: Remove all their reports
                    // TODO: Remove all their CellWriteNodes
                    // TODO: Mark all nodes that are after a removed CellWrite to be reprocessed
                    TODO()
                }
            }
        }
    }

    private fun <T> PureTaskStep<T>.toTask(name: Name): Task<T> = Task.of(name, this)

    private class IncrementalCellImpl<T>(
        override val name: Name,
        override val valueType: KType,
        override val stepBy: (T, Duration) -> T,
        override val mergeConcurrentEffects: (Effect<T>, Effect<T>) -> Effect<T>,
    ) : Cell<T>
    @Suppress("UNCHECKED_CAST")
    private fun <T> getCellNodes(cell: Cell<T>) = cellNodes.getValue(cell) as TreeMap<Instant, CellNode<T>>

    // Time within the simulator is primarily the Instant at which a task runs.
    // Within a single instant, there's a series of job batches.
    // All the jobs in a batch run in parallel.
    // The ordering of steps between two parallel jobs is meaningless, but we can impose an arbitrary order for sorting purposes.
    // Finally, within a job, there are a series of steps.
    private data class SimulationTimeImpl(
        override val instant: Instant,
        val batch: Int = 0,
        val branch: Int = 0,
        val step: Int = 0,
    ) : SimulationTime {
        override fun compareTo(other: SimulationTime): Int = when (other) {
            is SimulationTimeImpl -> {
                var n = instant.compareTo(other.instant)
                if (n == 0) n = batch.compareTo(other.batch)
                if (n == 0) n = branch.compareTo(other.branch)
                if (n == 0) n = step.compareTo(other.step)
                n
            }
        }
    }

    enum class SimulationTimeIncrement {
        BATCH,
        BRANCH,
        STEP
    }

    /**
     * Generally used as "+=", increments the time in the indicated field and resets later fields to 0.
     */
    private operator fun SimulationTime.plus(inc: SimulationTimeIncrement) = when (this) {
        is SimulationTimeImpl -> when (inc) {
            BATCH -> copy(batch = batch + 1, branch = 0, step = 0)
            BRANCH -> copy(branch = branch + 1, step = 0)
            STEP -> copy(step = step + 1)
        }
    }

    private fun SimulationTime.batchStart() = when (this) {
        is SimulationTimeImpl -> copy(branch = 0, step = 0)
    }

    infix fun SimulationTime.isConcurrentWith(other: SimulationTime): Boolean = when (this) {
        is SimulationTimeImpl -> when (other) {
            is SimulationTimeImpl -> instant == other.instant
                    && batch == other.batch
                    && branch != other.branch
        }
    }

    infix fun SimulationTime.isCausallyBefore(other: SimulationTime): Boolean = when (this) {
        is SimulationTimeImpl -> when (other) {
            is SimulationTimeImpl -> this < other && !(this isConcurrentWith other)
        }
    }

    infix fun SimulationTime.isCausallyAfter(other: SimulationTime): Boolean = when (this) {
        is SimulationTimeImpl -> when (other) {
            is SimulationTimeImpl -> this > other && !(this isConcurrentWith other)
        }
    }

    private sealed interface FrontierAction {
        val node: SGNode
        val time: SimulationTime get() = node.time

        data class StartTask(override val node: RootTaskNode) : FrontierAction
        data class ContinueTask(override val node: RunTaskNode) : FrontierAction
        data class RerunTask(override val node: RunTaskNode) : FrontierAction
        data class CheckCell(override val node: CellNode<*>) : FrontierAction
    }
}

// TODO: Consider pushing time-of-report into the kernel generally, instead of waiting for foundation to introduce that.
//   This may be especially easy if we switch to Instant-based times everywhere.
// TODO: There's also a problem here with doing incremental, fine-grained report modification.
//   What I mean by that is multiple reports, issued from one task during one batch.
//   Those reports are ordered, and that order should be reportable and preserved.
/** Wrapper around a report to give every report a unique identity, so they can later be revoked. */
class IncrementalReport<T>(
    val time: SimulationTime,
    val content: T,
) {
    override fun toString(): String = "$time: $content"
}

sealed interface SimulationTime : Comparable<SimulationTime> {
    val instant: Instant
}

/**
 * A generalization of [ReportHandler] which allows the simulator to revoke a report it issued previously,
 * in response to incremental changes to the simulation.
 */
interface IncrementalReportHandler {
    fun report(report: IncrementalReport<*>)
    fun revoke(report: IncrementalReport<*>)
}
