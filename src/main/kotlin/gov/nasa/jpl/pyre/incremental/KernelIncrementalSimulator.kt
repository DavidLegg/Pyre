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
import java.util.TreeSet
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
    /** All cell nodes allocated in the DAG */
    private val cellNodes: MutableMap<Cell<*>, TreeMap<SimulationTime, CellNode<*>>> = mutableMapOf()
    // TODO: This taskNodes set is likely overkill, but useful for debugging. Get rid of it once we have confidence in the simulator.
    /** All task nodes allocated in the DAG */
    private val taskNodes: MutableSet<TaskNode> = mutableSetOf()
    /** The number of branches ever created at this time. For correct operation, use only batchStart() times as keys. */
    private val branches: TreeMap<SimulationTime, Int> = TreeMap()
    /** The work list of actions to resolve the DAG. */
    private val frontier: PriorityQueue<FrontierAction> = PriorityQueue(compareBy { it.time })

    init {
        // Init happens before any tasks, at plan start.
        val cellAllocTime = SimulationTimeImpl(planStart, batch = -1)
        var initTime = cellAllocTime
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
                        put(initTime, CellWriteNode(cellAllocTime, value, null, identity()))
                    }
                }

            override fun <T> spawn(name: Name, step: PureTaskStep<T>) {
                // Schedule the task on its own branch, as part of the first batch.
                frontier += StartTask(RootTaskNode(nextAvailableBranch(startTime), Task.of(name, step)).also(taskNodes::add))
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
                    nextAvailableBranch(SimulationTimeImpl(activity.time)),
                    Task.of(activity.name, activity.task),
                ).also(taskNodes::add)
            )
        }
        try {
            resolve()
        } finally {
            // DEBUG
            // Disable the integrity check on this run, so we can get a final graph even if it's invalid.
            File("/Users/dlegg/Code/Pyre/tmp/tmp-final.dot").writeText(dumpDot(checkIntegrity = false))
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
                    // Start the job in the next step. Roots are already assigned to their own branches.
                    action.node.next = StepBeginNode(
                        action.time + STEP,
                        action.node,
                        action.node.task,
                    ).also {
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
                                awaiter.next?.takeIf { it.time isCausallyAfter writeTime }?.let {
                                    // If the awaiter continues causally after this write, revoke that continuation.
                                    // This will include clearing awaiter.next.
                                    revokeTask(it)
                                }
                                if (awaiter.next == null) {
                                    // The awaiter is active (it had no next node, or the next node was revoked).
                                    // Build a new await node and schedule it for the reaction to this batch
                                    awaiter.next = AwaitNode(
                                        nextAvailableBranch(writeNode.time + BATCH).reactionsStep(),
                                        awaiter,
                                        awaiter.condition,
                                        continuation = awaiter.continuation,
                                    ).also {
                                        frontier += CheckCondition(it);
                                        taskNodes += it
                                    }
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
                            // Spawning is a yielding action, so add our continuation to the next batch.
                            // Also add the child task to the next batch, as a root task node so it can be independently restarted.
                            lastTaskStepNode = SpawnNode(
                                nextAvailableBranch(lastTaskStepNode.time + BATCH),
                                lastTaskStepNode,
                                RootTaskNode(
                                    nextAvailableBranch(lastTaskStepNode.time + BATCH),
                                    result.child,
                                ).also {
                                    frontier += StartTask(it)
                                    taskNodes += it
                                },
                                result.continuation,
                            ).also {
                                lastTaskStepNode.next = it
                                it.child.prior = it
                                frontier += ContinueTask(it)
                                taskNodes += it
                            }
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
                            } else {
                                action.time + STEP
                            }
                            action.node.next = StepBeginNode(
                                satisfiedTime,
                                action.node,
                                action.node.continuation,
                            ).also {
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

    private fun nextAvailableBranch(time: SimulationTime): SimulationTime = when (val batch = time.batchStart()) {
        is SimulationTimeImpl -> batch.copy(branch = branches.compute(batch) { _, n -> (n ?: -1) + 1 }!!)
    }

    private fun revokeTask(task: TaskNode) {
        // Remove our record of this node
        taskNodes -= task
        // Remove any frontier action(s) related to this node
        frontier -= RerunTask(task)
        if (task is RootTaskNode) frontier -= StartTask(task)
        if (task is YieldingStepNode) frontier -= ContinueTask(task)
        if (task is AwaitNode) frontier -= CheckCondition(task)
        // Unlink this node from its prior
        // The 'if' is in case we're unlinking a root node from a spawn.
        // In that case, task is the spawn's child, not its next.
        // To revoke a root task spawned by a parent task, we must be revoking the parent task too, so no need to unlink the child.
        task.prior?.apply { if (next === task) next = null }
        // Do any special actions based on this node type
        when (task) {
            is ReadNode -> task.cell.reads -= task
            is ReportNode -> reportHandler.revoke(task.report)
            is WriteNode -> revokeCell(task.cell)
            is RootTaskNode -> { /* Nothing to do */ }
            is AwaitNode -> task.reads.forEach { it.awaiters -= task }
            is SpawnNode -> revokeTask(task.child)
            is StepBeginNode -> { /* Nothing to do */ }
        }
        // Continue revoking the rest of this task
        task.next?.let(::revokeTask)
    }

    private fun <T> revokeCell(cell: CellNode<T>) {
        TODO("revokeCell")
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
     *
     * @param checkIntegrity Raise an [IllegalStateException] if the graph doesn't meet standard integrity checks.
     */
    private fun dumpDot(checkIntegrity: Boolean = true): String {
        val checkIntegrity: (Boolean, () -> String) -> Unit = if (checkIntegrity) { condition, lazyMessage ->
            check(condition) { lazyMessage() + " integrity check failed" }
        } else { c, l -> }

        val graphBuilder = StringBuilder("digraph ${KernelIncrementalSimulator::class.simpleName} {\n")
        graphBuilder.append("  concentrate = true\n")
        var n = 0
        val cellDotId = mutableMapOf<CellNode<*>, String>()
        val taskDotId = mutableMapOf<TaskNode, String>()
        val cells = mutableMapOf<CellNode<*>, Cell<*>>()
        val ranks = TreeMap<SimulationTime, MutableList<SGNode>>()
        // The "rank" is just the time ignoring the branch
        fun SimulationTime.rank() = when (this) { is SimulationTimeImpl -> copy(branch = 0) }
        // The "file" is the horizontal position within the rank
        fun SimulationTime.file() = when (this) { is SimulationTimeImpl -> branch }
        fun collect(node: SGNode) = ranks.computeIfAbsent(node.time.rank()) { mutableListOf() }.add(node)
        val frontierModifier = frontier.groupBy { it.node }
            .mapValues { (_, actions) -> actions.joinToString(", ") { it::class.simpleName!! } }
        for ((cell, cellTree) in cellNodes) {
            for (node in cellTree.values) {
                cellDotId[node] = "cell${n++}"
                cells[node] = cell
                collect(node)
            }
        }
        for (node in taskNodes) {
            taskDotId[node] = "task${n++}"
            collect(node)
        }

        for ((i, rank) in ranks.values.withIndex()) {
            if (i > 0) {
                // Add invisible edges between representative nodes on each rank, to enforce rank order
                graphBuilder.append("  r", i - 1, " -> r", i, " [style = invis]\n")
            }
            graphBuilder
                .append("  {\n")
                .append("    // Rank ", i, "\n")
                .append("    rank = same\n")
                .append("    rankdir = LR\n")
                // Add an invisible representative node, to enforce rank ordering, so edges between them don't interfere
                .append("    r", i, " [style = invis]\n")

            for ((j, node) in rank.sortedBy { it.time.file() }.withIndex()) {
                // TODO: Clean up this node generation logic
                val id = when (node) {
                    is CellNode<*> -> {
                        val cell = cells.getValue(node)
                        val label = when (node) {
                            is CellMergeNode<*> -> "Merge ${cell.name}"
                            is CellStepNode<*> -> "Step ${cell.name}"
                            is CellWriteNode<*> -> "Write ${cell.name}"
                        }
                        val id = cellDotId.getValue(node)
                        if (node in frontierModifier)  {
                            val fillColor = if (node === frontier.firstOrNull()?.node) "#69aa7c" else "#50a0f4"
                            graphBuilder.append("    ", id, " [shape = ellipse, style = filled, fillcolor = \"$fillColor\", label = \"$label\\l${node.value}\\l${node.time}\\l** ${frontierModifier.getValue(node)}\\l\"]\n")
                        } else {
                            graphBuilder.append("    ", id, " [shape = ellipse, label = \"$label\\l${node.value}\\l${node.time}\\l\"]\n")
                        }
                        id
                    }
                    is TaskNode -> {
                        val label = when (node) {
                            is ReadNode -> "Read"
                            is ReportNode -> "Report ${node.report.content}"
                            is WriteNode -> "Write '${node.cell.effect}'"
                            is RootTaskNode -> "Root ${node.task.id.name}"
                            is AwaitNode -> "Await"
                            is SpawnNode -> "Spawn"
                            is StepBeginNode -> "Begin"
                        }
                        val id = taskDotId.getValue(node)
                        if (node in frontierModifier) {
                            val fillColor = if (node === frontier.firstOrNull()?.node) "#69aa7c" else "#50a0f4"
                            graphBuilder.append("    ", id, " [shape = box, style = filled, fillcolor = \"$fillColor\", label = \"$label\\l${node.time}\\l** ${frontierModifier.getValue(node)}\\l\"]\n")
                        } else {
                            graphBuilder.append("    ", id, " [shape = box, label = \"$label\\l${node.time}\\l\"]\n")
                        }
                        id
                    }
                }
                if (j > 0) {
                    // Add an invisible edge between the last file and this node to enforce file order
                    graphBuilder.append("    f_", i, "_", j - 1, " -> ", id, " [style = invis]\n")
                }
                // Add an invisible file node, and an invisible edge from the last node to this file node
                // We use invisible nodes instead of drawing edges between the visible nodes so the invisible ordering edges
                // don't merge with and hide the real edges.
                graphBuilder
                    .append("    f_", i, "_", j, " [ style = invis ]\n")
                    .append("    ", id, " -> f_", i, "_", j, " [style = invis]\n")
            }

            graphBuilder
                .append("  }\n")
        }

        // Now fill in the edges
        for ((node, nodeId) in cellDotId) {
            for (next in node.next) {
                graphBuilder.append("  ", nodeId, " -> ", cellDotId.getValue(next), "\n")
                checkIntegrity(when (next) {
                    is CellMergeNode<*> -> next.prior.any { it === node }
                    is CellStepNode<*> -> node === next.prior
                    is CellWriteNode<*> -> node === next.prior
                }) { "Cell prior" }
            }
            for (read in node.reads) {
                graphBuilder.append("  ", nodeId, " -> ", taskDotId.getValue(read), "\n")
                checkIntegrity(read.cell === node) { "Cell read" }
            }
            for (awaiter in node.awaiters) {
                graphBuilder.append("  ", nodeId, " -> ", taskDotId.getValue(awaiter), "\n")
                checkIntegrity(node in awaiter.reads) { "Cell awaiter" }
            }
            when (node) {
                is CellMergeNode<*> -> node.prior.forEach {
                    checkIntegrity(it in cellDotId) { "Cell merge prior" }
                    checkIntegrity(node in it.next) { "Cell merge prior" }
                }
                is CellStepNode<*> -> {
                    checkIntegrity(node.prior in cellDotId) { "Cell step prior" }
                    checkIntegrity(node in node.prior.next) { "Cell step prior" }
                }
                is CellWriteNode<*> -> {
                    node.prior?.let {
                        checkIntegrity(it in cellDotId) { "Cell write prior" }
                        checkIntegrity(node in it.next) { "Cell write prior" }
                    }
                    node.writer?.let {
                        checkIntegrity(it in taskDotId) { "Cell writer" }
                        checkIntegrity(it.cell === node) { "Cell writer" }
                    }
                }
            }
        }
        for ((node, nodeId) in taskDotId) {
            node.next?.let {
                graphBuilder.append("  ", nodeId, " -> ", taskDotId.getValue(it), "\n")
                checkIntegrity(it.prior === node) { "Task next" }
            }
            when (node) {
                is SpawnNode -> {
                    graphBuilder.append("  ", nodeId, " -> ", taskDotId.getValue(node.child), "\n")
                    checkIntegrity(node.child.prior === node) { "Task spawn" }
                }
                is WriteNode -> {
                    graphBuilder.append("  ", nodeId, " -> ", cellDotId.getValue(node.cell), "\n")
                    checkIntegrity(node.cell.writer === node) { "Task write" }
                }
                is ReadNode -> {
                    checkIntegrity(node.cell in cellDotId) { "Task read" }
                    checkIntegrity(node in node.cell.reads) { "Task read" }
                }
                is AwaitNode -> {
                    node.reads.forEach {
                        checkIntegrity(it in cellDotId) { "Task await" }
                        checkIntegrity(node in it.awaiters) { "Task await" }
                    }
                }
                else -> Unit
            }
            node.prior?.let {
                checkIntegrity(it in taskDotId) { "Task prior" }
                checkIntegrity(it.next === node || (it as? SpawnNode)?.child === node) { "Task prior" }
            }
        }
        graphBuilder.append("}\n")
        return graphBuilder.toString()
    }
}

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
