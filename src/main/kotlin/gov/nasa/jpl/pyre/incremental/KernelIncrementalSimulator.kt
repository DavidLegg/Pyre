package gov.nasa.jpl.pyre.incremental

import gov.nasa.jpl.pyre.incremental.KernelIncrementalSimulator.FrontierAction.*
import gov.nasa.jpl.pyre.incremental.KernelIncrementalSimulator.SimulationTimeIncrement.*
import gov.nasa.jpl.pyre.incremental.SimulationGraph.*
import gov.nasa.jpl.pyre.kernel.BasicInitScope
import gov.nasa.jpl.pyre.kernel.Cell
import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.Duration.Companion.ZERO
import gov.nasa.jpl.pyre.kernel.Effect
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.PureTaskStep
import gov.nasa.jpl.pyre.kernel.ReadActions
import gov.nasa.jpl.pyre.kernel.ReportHandler
import gov.nasa.jpl.pyre.kernel.SatisfiedAt
import gov.nasa.jpl.pyre.kernel.Task
import gov.nasa.jpl.pyre.kernel.UnsatisfiedUntil
import gov.nasa.jpl.pyre.kernel.toKotlinDuration
import gov.nasa.jpl.pyre.kernel.toPyreDuration
import gov.nasa.jpl.pyre.utilities.identity
import java.io.File
import java.util.PriorityQueue
import java.util.TreeMap
import java.util.stream.Stream.iterate
import kotlin.collections.plusAssign
import kotlin.reflect.KType
import kotlin.time.Instant

/**
 * Support for [GraphIncrementalPlanSimulation], which implements graph-based incremental simulation at the kernel level.
 */
class KernelIncrementalSimulator(
    planStart: Instant,
    constructPlan: context (BasicInitScope) () -> List<KernelActivity>,
    private val reportHandler: IncrementalReportHandler
) {
    private val cellNodes: MutableMap<Cell<*>, TreeMap<SimulationTime, CellNode<*>>> = mutableMapOf()
    // TODO: This taskNodes set is likely overkill, but useful for debugging. Get rid of it once we have confidence in the simulator.
    private val taskNodes: MutableSet<TaskNode> = mutableSetOf()
    private val occupiedBranches: MutableSet<SimulationTime> = mutableSetOf()
    private val frontier: PriorityQueue<FrontierAction> = PriorityQueue(compareBy { it.time })

    init {
        // Init happens before any tasks, at plan start.
        var initTime = SimulationTimeImpl(planStart, batch = -1)
        var startTime = initTime + BATCH

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
                        put(initTime, CellWriteNode(initTime, value, null, identity()))
                        initTime += STEP
                    }
                }

            override fun <T> spawn(name: Name, step: PureTaskStep<T>) {
                // Schedule the task on its own branch, as part of the first batch.
                occupyBranch(startTime)
                // Note that root task nodes go into the "reactions" step, ahead of regular task steps,
                // so that the first real step can happen at step 0.
                frontier += StartTask(RootTaskNode(startTime.reactionsStep(), Task.of(name, step)).also(taskNodes::add))
                // Since we're doing init, we can sequentially assign branches rather than search for available branches.
                startTime += BRANCH
            }

            override fun <T> read(cell: Cell<T>): T {
                // Since there cannot be effects during init,
                // we may safely assume the first node to be the write node added during allocation
                return (getCellNodes(cell).firstEntry().value as CellWriteNode<T>).value
            }

            override fun <T> report(value: T) {
                // Init reports can be issued directly, without a DAG node. There's no task to associate them with anyways.
                reportHandler.report(IncrementalReport(initTime, value))
                initTime += STEP
            }
        }
        val activities = constructPlan(basicInitScope)
        run(KernelPlanEdits(additions = activities))
    }

    fun run(planEdits: KernelPlanEdits) {
        planEdits.removals.forEach { TODO("kernel plan removals") }
        for (activity in planEdits.additions) {
            frontier += StartTask(
                RootTaskNode(
                    nextAvailableBranch(SimulationTimeImpl(activity.time)).reactionsStep(),
                    Task.of(activity.name, activity.task),
                ).also(taskNodes::add)
            ).also { occupyBranch(it.time) }
        }
        try {
            resolve()
        } finally {
            // DEBUG
            File("/Users/dlegg/Code/Pyre/tmp/tmp-final.dot").writeText(dumpDot())
        }
    }

    private fun resolve() {
        var debugStep = 0
        while (true) {
            // DEBUG
            File("/Users/dlegg/Code/Pyre/tmp/tmp${debugStep++.toString().padStart(6, '0')}.dot").writeText(dumpDot())
            when (val action = frontier.poll() ?: break) {
                is StartTask -> {
                    action.node.next?.let {
                        // If this root has already been expanded, revoke that expansion
                        revokeTask(it)
                    }
                    // Add the first step of the job in an unoccupied branch, and schedule it to run
                    action.node.next = StepBeginNode(
                        nextAvailableBranch(action.time + BATCH),
                        action.node,
                        action.node.task,
                    ).also {
                        occupyBranch(it.time)
                        frontier += ContinueTask(it)
                        taskNodes += it
                    }
                }

                is ContinueTask -> {
                    // Expand this task node
                    var lastTaskStepNode: TaskNode = action.node

                    val basicTaskActions = object : Task.BasicTaskActions {
                        override fun <V> read(cell: Cell<V>): V {
                            // Look up the cell node
                            val cellNode = getCellNode(cell, lastTaskStepNode.time + STEP)
                            // Record this read in the graph
                            lastTaskStepNode = ReadNode(
                                lastTaskStepNode.time + STEP,
                                lastTaskStepNode,
                                cellNode,
                            ).also {
                                lastTaskStepNode.next = it
                                cellNode.reads += it
                                taskNodes += it
                            }
                            // Return the value
                            return cellNode.value
                        }

                        override fun <V> emit(
                            cell: Cell<V>,
                            effect: Effect<V>
                        ) {
                            // Look up the cell node we're writing to
                            val writeTime = lastTaskStepNode.time + STEP
                            val priorCellNode = getCellNode(cell, writeTime)
                            // Filter out the nodes causally after this write operation
                            val it = priorCellNode.next.iterator()
                            val nodesAfterWrite = mutableListOf<CellNode<V>>()
                            while (it.hasNext()) {
                                val next = it.next()
                                if (next.time isCausallyAfter writeTime) {
                                    it.remove()
                                    nodesAfterWrite += next
                                }
                            }
                            // Build this write node
                            val writeNode = CellWriteNode(
                                writeTime,
                                effect(priorCellNode.value),
                                priorCellNode,
                                effect,
                                next = nodesAfterWrite
                            )
                            // Add the edge from prior to this write node
                            priorCellNode.next += writeNode
                            // Fix the cell edges leaving from this write node
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

                            // Add the write node to the global cell map for quick lookups later.
                            getCellNodes(cell)[writeNode.time] = writeNode

                            // Schedule any reads that were disturbed by this write
                            for (read in priorCellNode.reads) {
                                if (read.time isCausallyAfter writeNode.time) {
                                    // Our write intercedes between the read and the value it read.
                                    // Presuming this task should now read a different value, it must be re-run.
                                    frontier += RerunTask(read)
                                }
                            }

                            for (awaiter in priorCellNode.awaiters) {
                                // If the awaiter has a future action depending on this cell, revoke it.
                                awaiter.next?.takeIf { it.time isCausallyAfter writeTime }?.let{
                                    // If we're planning on re-checking this condition later, don't.
                                    if (it is AwaitNode) frontier -= CheckCondition(it)
                                    // If we're planning on continuing the task later (because the condition would be satisfied then), don't.
                                    if (it is YieldingStepNode) frontier -= ContinueTask(it)
                                    // If we're disturbing the results of a previous run in any way, revoke it.
                                    revokeTask(it)
                                }
                                // Regardless, build a new await node and schedule it for the reaction to this batch
                                awaiter.next = AwaitNode(
                                    nextAvailableBranch(writeNode.time + BATCH).reactionsStep(),
                                    awaiter,
                                    awaiter.condition,
                                    continuation = awaiter.continuation,
                                ).also {
                                    occupyBranch(it.time)
                                    frontier += CheckCondition(it);
                                    taskNodes += it
                                }
                            }

                            // Having constructed the cell's write node, now construct the next step node for the task.
                            lastTaskStepNode = WriteNode(
                                writeTime,
                                lastTaskStepNode,
                                writeNode,
                            ).also {
                                // Also add the edges from the prior task step and from the cell write node to this.
                                lastTaskStepNode.next = it
                                writeNode.writer = it
                                taskNodes += it
                            }
                        }

                        override fun <V> report(value: V) {
                            // Record this report in the task graph and issue it to the reportHandler
                            lastTaskStepNode = ReportNode(
                                lastTaskStepNode,
                                IncrementalReport(lastTaskStepNode.time + STEP, value)
                                    .also(reportHandler::report),
                            ).also {
                                lastTaskStepNode.next = it
                                taskNodes += it
                            }
                        }
                    }
                    when (val result = action.node.continuation.runStep(basicTaskActions)) {
                        is Task.TaskStepResult.Await<*> -> {
                            // Create an await node and add it to the frontier, to be checked at the end of the batch.
                            lastTaskStepNode = AwaitNode(
                                // The "reactions" to this batch are just the reactionsStep of the next batch.
                                nextAvailableBranch(lastTaskStepNode.time + BATCH).reactionsStep(),
                                lastTaskStepNode,
                                result.condition,
                                continuation = result.continuation,
                            ).also {
                                // Having constructed the node, link it to chain of task step nodes
                                lastTaskStepNode.next = it
                                // Schedule the condition to be checked
                                frontier += CheckCondition(it)
                                // And mark the branch as occupied
                                occupyBranch(it.time)
                                taskNodes += it
                            }
                        }
                        is Task.TaskStepResult.Complete<*> -> { /* Nothing to do */ }
                        is Task.TaskStepResult.Restart<*> -> {
                            // Add a new root task, from which we can restart the next task at any time.
                            lastTaskStepNode = RootTaskNode(
                                // Restarting does not yield to the engine, it's the next step of this task.
                                lastTaskStepNode.time + STEP,
                                result.continuation,
                                lastTaskStepNode,
                            ).also {
                                lastTaskStepNode.next = it
                                frontier += StartTask(it)
                                taskNodes += it
                            }
                        }
                        is Task.TaskStepResult.Spawn<*, *> -> {
                            // Spawning is a yielding action, so add our continuation to the next batch:
                            // TODO: Spawning
                            TODO()
                        }
                    }
                }

                is RerunTask -> {
                    // TODO: Remove this task node, all its continuations, and all its children (recursively)
                    // TODO: Remove all their reports
                    // TODO: Remove all their CellWriteNodes
                    // TODO: Mark all nodes that are after a removed CellWrite to be reprocessed
                    TODO()
                }

                is CheckCell -> {
                    TODO()
                }

                is CheckCondition -> {
                    val readActions = object : ReadActions {
                        override fun <V> read(cell: Cell<V>): V {
                            // Get the appropriate cell node to read:
                            val cellNode = getCellNode(cell, action.time)
                            // Record bidirectionally that this read happened:
                            cellNode.awaiters += action.node
                            action.node.reads += cellNode
                            // Finally, return the value we read
                            return cellNode.value
                        }
                    }
                    // Schedule the next evaluation or the continuation, as appropriate
                    when (val result = action.node.condition(readActions)) {
                        is SatisfiedAt -> {
                            // Schedule the continuation of the task for the time indicated
                            // If that time is in the future, find and occupy a branch in batch 0 then.
                            // If that time is now, run in the next step; this await already occupies a branch.
                            val satisfiedTime = if (result.time > ZERO) {
                                nextAvailableBranch(SimulationTimeImpl(action.time.instant + result.time.toKotlinDuration()))
                                    .also { occupyBranch(it) }
                            } else {
                                action.time + STEP
                            }
                            action.node.next = StepBeginNode(
                                satisfiedTime,
                                action.node,
                                action.node.continuation,
                            ).also {
                                occupyBranch(it.time)
                                frontier += ContinueTask(it)
                                taskNodes += it
                            }
                        }
                        is UnsatisfiedUntil -> {
                            // If we're unsatisfied only for a finite time, add and schedule an await for that time
                            result.time?.let { t ->
                                action.node.next = AwaitNode(
                                    SimulationTimeImpl(action.time.instant + t.toKotlinDuration()),
                                    action.node,
                                    action.node.condition,
                                    continuation = action.node.continuation,
                                ).also {
                                    occupyBranch(it.time)
                                    frontier += CheckCondition(it)
                                    taskNodes += it
                                }
                            }
                            // If we're unsatisfied indefinitely, nothing to do for now.
                            // In either case, cell writes to any of the read cells will reschedule awaits as needed.
                        }
                    }
                }
            }
        }
    }

    private fun occupyBranch(time: SimulationTime) {
        occupiedBranches += time.branchStart()
    }

    private fun nextAvailableBranch(time: SimulationTime): SimulationTime =
        iterate(time.branchStart()) { it + BRANCH }
            .filter { it !in occupiedBranches }
            .findFirst()
            .get()

    private fun revokeTask(task: TaskNode) {
        TODO("revoking a task in the simulation DAG")
    }

    private class IncrementalCellImpl<T>(
        override val name: Name,
        override val valueType: KType,
        override val stepBy: (T, Duration) -> T,
        override val mergeConcurrentEffects: (Effect<T>, Effect<T>) -> Effect<T>,
    ) : Cell<T>
    @Suppress("UNCHECKED_CAST")
    private fun <T> getCellNodes(cell: Cell<T>) = cellNodes.getValue(cell) as TreeMap<SimulationTime, CellNode<T>>

    fun <V> getCellNode(cell: Cell<V>, time: SimulationTime): CellNode<V> {
        val thisCellsNodes = getCellNodes(cell)
        // Get the most recent cell node.
        var (t, cellNode) = checkNotNull(thisCellsNodes.floorEntry(time))
        // This may have come from another task running in this batch though!
        if ((t as SimulationTimeImpl).isConcurrentWith(time)) {
            // If so, set the time to the first possible time of this batch,
            // and ask for the cell node before that. That'll get the last cell node
            // of the batch before this, which must be a merge or step or something.
            cellNode = checkNotNull(thisCellsNodes.lowerEntry(time.batchStart())).value
        }
        // Now, check if we need to step up this cell node to the requested time
        if (cellNode.time.instant < time.instant) {
            val stepSize = (time.instant - cellNode.time.instant).toPyreDuration()
            // Build the new step node
            val stepNode = CellStepNode(
                time.cellSteppingBatch(),
                cell.stepBy(cellNode.value, stepSize),
                cellNode,
                stepSize,
                cellNode.next.toMutableList(),
                // Leave the reads as-is, they happen before the stepping
            )
            // Fix the edges leading to this step node
            cellNode.next.clear()
            cellNode.next += stepNode
            // Fix the edges leaving from this step node
            for (nextNode in stepNode.next) {
                // TODO: Prove that only step nodes can be next.
                check(nextNode is CellStepNode<V>) {
                    "Internal error! Node following an injected step node was not itself a step node."
                }
                nextNode.prior = stepNode
            }

            // Add anything that was awaiting the prior node as awaiting this node too.
            // Just stepping a node doesn't disturb the awaiters, though.
            for (awaiter in cellNode.awaiters) {
                // If the awaiter has no registered next node, or its next node is after this step,
                // then this awaiter can read this step as well.
                if (awaiter.next?.run { time isCausallyAfter stepNode.time } ?: true) {
                    stepNode.awaiters += awaiter
                    // TODO: Think carefully about this edge. It violates causal order!
                    awaiter.reads += stepNode
                }
                // Otherwise, the awaiter completed before this step happens, so don't connect it.
            }

            // Let the global cell map know about this node for quick lookup later
            thisCellsNodes[stepNode.time] = stepNode
            // TODO: Consider how (and when) to "collapse" step nodes together.
            return stepNode
        } else {
            // Since no stepping is required, return the node we found
            return cellNode
        }
    }

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

        override fun toString(): String = "$instant::$batch/$branch/$step"
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

    private fun SimulationTime.branchStart() = when (this) {
        is SimulationTimeImpl -> copy(step = 0)
    }

    private fun SimulationTime.cellSteppingBatch() = when (this) {
        // Conceptually, cells are stepped in a special "batch", before any tasks are run
        is SimulationTimeImpl -> copy(batch = -1, branch = 0, step = 0)
    }

    private fun SimulationTime.reactionsStep() = when (this) {
        // Conceptually, reactions happen in a special step before regular tasks, so their task can run in that step
        is SimulationTimeImpl -> copy(step = -1)
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
        data class ContinueTask(override val node: YieldingStepNode) : FrontierAction
        data class RerunTask(override val node: TaskNode) : FrontierAction
        data class CheckCell(override val node: CellNode<*>) : FrontierAction
        data class CheckCondition(override val node: AwaitNode) : FrontierAction
    }

    /**
     * Debugging function which dumps the current simulation DAG as a Graphviz (dot) file
     */
    private fun dumpDot(): String {
        val graphBuilder = StringBuilder("digraph ${KernelIncrementalSimulator::class.simpleName} {\n")
        graphBuilder.append("  concentrate = true\n")
        var n = 0
        val cellDotId = mutableMapOf<CellNode<*>, String>()
        val taskDotId = mutableMapOf<TaskNode, String>()
        val rankClusters = TreeMap<SimulationTime, StringBuilder>()
        val frontierModifier = frontier.groupBy { it.node }
            .mapValues { (_, actions) -> actions.joinToString(", ") { it::class.simpleName!! } }
        // The "rank" is just the time ignoring the branch
        fun SimulationTime.rank() = when (this) { is SimulationTimeImpl -> copy(branch = 0) }
        for ((cell, cellTree) in cellNodes) {
            for (node in cellTree.values) {
                val id = "cell${n++}"
                cellDotId[node] = id

                val rankBuilder = rankClusters.computeIfAbsent(node.time.rank()) { StringBuilder() }
                val label = when (node) {
                    is CellMergeNode<*> -> "Merge ${cell.name}"
                    is CellStepNode<*> -> "Step ${cell.name}"
                    is CellWriteNode<*> -> "Write ${cell.name}"
                }
                if (node in frontierModifier)  {
                    val fillColor = if (node === frontier.firstOrNull()?.node) "#69aa7c" else "#50a0f4"
                    rankBuilder.append("    ", id, " [shape = ellipse, style = filled, fillcolor = \"$fillColor\", label = \"$label\\l${node.value}\\l${node.time}\\l** ${frontierModifier.getValue(node)}\\l\"]\n")
                } else {
                    rankBuilder.append("    ", id, " [shape = ellipse, label = \"$label\\l${node.value}\\l${node.time}\\l\"]\n")
                }
            }
        }
        for (node in taskNodes) {
            val id = "task${n++}"
            taskDotId[node] = id
            val rankBuilder = rankClusters.computeIfAbsent(node.time.rank()) { StringBuilder() }

            val label = when (node) {
                is ReadNode -> "Read"
                is ReportNode -> "Report ${node.report.content}"
                is WriteNode -> "Write"
                is RootTaskNode -> "Root ${node.task.id.name}"
                is AwaitNode -> "Await"
                is SpawnNode -> "Spawn"
                is StepBeginNode -> "Begin"
            }
            if (node in frontierModifier) {
                val fillColor = if (node === frontier.firstOrNull()?.node) "#69aa7c" else "#50a0f4"
                rankBuilder.append("    ", id, " [shape = box, style = filled, fillcolor = \"$fillColor\", label = \"$label\\l${node.time}\\l** ${frontierModifier.getValue(node)}\\l\"]\n")
            } else {
                rankBuilder.append("    ", id, " [shape = box, label = \"$label\\l${node.time}\\l\"]\n")
            }
        }

        for ((i, rankBuilder) in rankClusters.values.withIndex()) {
            if (i > 0) {
                // Add invisible edges between representative nodes on each rank, to enforce rank order
                graphBuilder.append("  r", i - 1, " -> r", i, " [ style = invis ]\n")
            }
            graphBuilder
                .append("  {\n")
                .append("    // Rank ", i, "\n")
                .append("    rank = same\n")
                // Add an invisible representative node, to enforce rank ordering, so edges between them don't interfere
                .append("    r", i, " [ style = invis ]\n")
                .append("    ", rankBuilder)
                .append("  }\n")
        }

        // Now fill in the edges
        for ((node, nodeId) in cellDotId) {
            for (next in node.next) {
                graphBuilder.append("  ", nodeId, " -> ", cellDotId.getValue(next), "\n")
                // Sanity check
                check(when (next) {
                    is CellMergeNode<*> -> next.prior.any { it === node }
                    is CellStepNode<*> -> node === next.prior
                    is CellWriteNode<*> -> node === next.prior
                })
            }
            for (read in node.reads) {
                graphBuilder.append("  ", nodeId, " -> ", taskDotId.getValue(read), "\n")
                // Sanity check
                check(read.cell === node)
            }
            for (awaiter in node.awaiters) {
                graphBuilder.append("  ", nodeId, " -> ", taskDotId.getValue(awaiter), "\n")
                // Sanity check
                check(node in awaiter.reads)
            }
            // Sanity check
            when (node) {
                is CellMergeNode<*> -> check(node.prior.all { it in cellDotId })
                is CellStepNode<*> -> check(node.prior in cellDotId)
                is CellWriteNode<*> -> {
                    node.prior?.let { check(it in cellDotId) }
                    node.writer?.let { check(it in taskDotId) }
                }
            }
        }
        for ((node, nodeId) in taskDotId) {
            node.next?.let {
                graphBuilder.append("  ", nodeId, " -> ", taskDotId.getValue(it), "\n")
                // Sanity check
                check(it.prior == node)
            }
            when (node) {
                is SpawnNode -> {
                    graphBuilder.append("  ", nodeId, " -> ", taskDotId.getValue(node.child), "\n")
                    // Sanity check
                    check(node.child.prior === node)
                }
                is WriteNode -> {
                    graphBuilder.append("  ", nodeId, " -> ", cellDotId.getValue(node.cell), "\n")
                    // Sanity check
                    check(node.cell.writer === node)
                }
                is ReadNode -> {
                    // Sanity check
                    check(node.cell in cellDotId)
                }
                is AwaitNode -> {
                    // Sanity check
                    check(node.reads.all { it in cellDotId })
                }
                else -> Unit
            }
            node.prior?.let {
                // Sanity check
                check(it in taskDotId)
            }
        }
        graphBuilder.append("}\n")
        return graphBuilder.toString()
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
