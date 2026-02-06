package gov.nasa.jpl.pyre.incremental

import gov.nasa.jpl.pyre.incremental.KernelIncrementalSimulator.FrontierAction.*
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
import gov.nasa.jpl.pyre.kernel.toKotlinDuration
import gov.nasa.jpl.pyre.kernel.toPyreDuration
import gov.nasa.jpl.pyre.utilities.compose
import gov.nasa.jpl.pyre.utilities.identity
import java.io.File
import java.util.PriorityQueue
import java.util.TreeMap
import java.util.TreeSet
import kotlin.collections.plusAssign
import kotlin.reflect.KType
import kotlin.time.Instant

// TODO: Look for opportunities to refactor node creation (e.g. an "insert after" operator that does the link modification).

/**
 * Support for [GraphIncrementalPlanSimulation], which implements graph-based incremental simulation at the kernel level.
 */
class KernelIncrementalSimulator(
    planStart: Instant,
    private val planEnd: Instant,
    constructPlan: context (BasicInitScope) () -> List<KernelActivity>,
    private val reportHandler: IncrementalReportHandler
) {
    private var nextNodeId: Int = 0
    /** All cell nodes allocated in the DAG */
    private val cellNodes: MutableMap<Cell<*>, TreeMap<SimulationTime, CellNode<*>>> = mutableMapOf()
    // TODO: This taskNodes set is likely overkill, but useful for debugging. Get rid of it once we have confidence in the simulator.
    /** All task nodes allocated in the DAG */
    private val taskNodes: MutableSet<TaskNode> = mutableSetOf()
    // TODO: Find a way to prevent taskBranch from growing indefinitely as tasks are added and removed
    /** The permanently-assigned branch number for each task. */
    private val taskBranch: MutableMap<Task<*>, Int> = mutableMapOf()
    /** The work list of actions to resolve the DAG. */
    private val frontier: TreeSet<FrontierAction> = TreeSet(
            // Primarily sort frontier actions by time, as incremental re-work is most efficient when done in time order.
        compareBy<FrontierAction>(FrontierAction::time)
            // Secondarily assign an arbitrary priority to each kind of action, to allow different actions on a single node,
            // while de-duplicating instances of the same action on the same node.
            .thenBy {
                when (it) {
                    is CheckCell -> 1
                    is CheckCondition -> 2
                    is StartTask -> 3
                    is RunTask -> 4
                    is RevokeMergeOpportunity -> 5
                }
            }
            // Finally, use the serial ID to permit the same action at the same time on different nodes.
            // This doesn't happen in a well-formed DAG, but has been observed ephemerally while rewriting the DAG.
            .thenBy { it.node.serialId }
    )

    /** Root task nodes corresponding to activities in the plan, recorded to facilitate revoking tasks. */
    private val planTaskNodes: MutableMap<KernelActivity, RootTaskNode> = mutableMapOf()
    /** Root nodes with which we may merge restart requests, rather than re-running. */
    private val rootMergeOpportunities: MutableMap<Task<*>, RootTaskNode> = mutableMapOf()

    private enum class DebugLevel { NONE, MAJOR, MINOR, ALL }
    private val DEBUG = DebugLevel.NONE
    private var debugMajorStep = 0
    private var debugMinorStep = 0
    private fun dumpDotToFile(debugLevel: DebugLevel, highlightNode: SGNode?, checkIntegrity: Boolean = true) {
        if (DEBUG >= debugLevel) {
            if (debugLevel <= DebugLevel.MAJOR) {
                ++debugMajorStep
                debugMinorStep = 0
            } else if (debugLevel <= DebugLevel.MINOR) {
                ++debugMinorStep
            }

            File("/Users/dlegg/Code/Pyre/tmp/tmp" +
                    debugMajorStep.toString().padStart(4, '0') +
                    "." +
                    debugMinorStep.toString().padStart(4, '0') +
                    ".dot"
            ).writeText(dumpDot(highlightNode = highlightNode, checkIntegrity = checkIntegrity))
        }
    }

    init {
        // Init happens before any tasks, at plan start.
        var cellAllocTime = SimulationTime(planStart).cellSteppingBatch()
        // Put initial reports on a different branch from cell allocation.
        // This shouldn't be strictly necessary, but it makes debugging easier by giving everything a unique time.
        val firstReportTime = cellAllocTime.copy(branch = 1)
        var startTime = cellAllocTime.nextTaskBatch()
        var lastReport: ReportNode<*>? = null

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
                        put(cellAllocTime, CellWriteNode(nextNodeId++, cellAllocTime, it, value, null, identity()))
                        // Advance the cell allocation time so that each initial cell write node has a unique time
                        cellAllocTime = cellAllocTime.nextStep()
                    }
                }

            override fun <T> spawn(name: Name, step: PureTaskStep<T>) {
                // Schedule the task on its own branch, as part of the first batch.
                val task = Task.of(name, step)
                frontier += StartTask(RootTaskNode(nextNodeId++, startTime.branchFor(task), task).also {
                    taskNodes += it
                })
            }

            override fun <T> read(cell: Cell<T>): T {
                // Since there cannot be effects during init,
                // we may safely assume the first node to be the write node added during allocation
                return (getCellNodes(cell).firstEntry().value as CellWriteNode<T>).value
            }

            override fun <T> report(value: T) {
                lastReport = ReportNode(nextNodeId++, lastReport?.time?.nextStep() ?: firstReportTime, lastReport, value).also { lastReport?.next = it }
                reportHandler.report(lastReport)
            }
        }
        val activities = constructPlan(basicInitScope)
        if (DEBUG >= DebugLevel.MAJOR) File("/Users/dlegg/Code/Pyre/tmp/tmp-init.dot").writeText(dumpDot())
        run(KernelPlanEdits(additions = activities))
    }

    fun run(planEdits: KernelPlanEdits) {
        planEdits.removals.forEach {
            val rootTaskNode = requireNotNull(planTaskNodes[it]) {
                "Activity $it not found in the plan."
            }
            fullyRevokeTask(rootTaskNode)
        }
        for (activity in planEdits.additions) {
            require(activity.time < planEnd) {
                "Cannot add activity $activity at or after plan ends at $planEnd"
            }
            val task = Task.of(activity.name, activity.task)
            frontier += StartTask(
                RootTaskNode(
                    nextNodeId++,
                    SimulationTime(activity.time).branchFor(task),
                    task,
                ).also {
                    taskNodes += it
                    planTaskNodes[activity] = it
                }
            )
        }
        try {
            resolve()
        } finally {
            // DEBUG
            // Disable the integrity check on this run, so we can get a final graph even if it's invalid.
            if (DEBUG >= DebugLevel.MAJOR) File("/Users/dlegg/Code/Pyre/tmp/tmp-final.dot").writeText(dumpDot(checkIntegrity = false))
        }
    }

    private fun resolve() {
        while (true) {
            // DEBUG
            dumpDotToFile(DebugLevel.MAJOR, frontier.firstOrNull()?.node)
            when (val action = frontier.pollFirst() ?: break) {
                is StartTask -> {
                    // Start the job in the next step. Roots are already assigned to their own branches.
                    action.node.next = StepBeginNode(
                        nextNodeId++,
                        action.time.nextStep(),
                        action.node,
                        action.node.task,
                    ).also {
                        frontier += RunTask(it)
                        taskNodes += it
                    }
                }

                is RunTask -> if (action.node is YieldingStepNode && action.node.continuation != null) {
                    // We're resuming a task normally, with no "future" we need to revoke
                    val continuation = action.node.continuation!!
                    // Forget the continuation since we can only resume from this continuation once
                    // Also search the prior task nodes, and delete any instances of the continuation there.
                    // This happens when an Await node gets duplicated to check conditions.
                    var n: TaskNode? = action.node
                    while (n is YieldingStepNode) {
                        if (n.continuation === continuation) n.continuation = null
                        n = n.prior
                    }
                    // Then run the continuation directly
                    action.node.continueWith(continuation::runStep)
                } else {
                    // We're re-running a task we've previously ran.
                    // Do this by revoking the future, replaying the past, and then running the present normally.

                    // Revoke the rest of this task, up to any repeat.
                    // If it repeats, add that repeat as a merge opportunity.
                    val continueFrom: TaskNode
                    if (action.node is StepBeginNode) {
                        // StepBeginNodes are placed to indicate the end of a prior step, like an await, that "occupies time".
                        // The task did not take an action at this time, it completed an action at a prior time.
                        // They should not be revoked; doing so would require re-doing the prior step, which wasn't requested.
                        action.node.next?.let { revokeWithMergeOpportunity(it) }
                        continueFrom = action.node
                    } else {
                        revokeWithMergeOpportunity(action.node)
                        continueFrom = checkNotNull(action.node.prior) {
                            "Internal error! Cannot rerun the first node in a task chain."
                        }
                    }
                    // Find the root node from which to replay this task
                    val root = generateSequence(continueFrom) { it.prior }.first { it is RootTaskNode } as RootTaskNode
                    continueFrom.continueWith { realActions ->
                        // We replay the task by continuing from prior, but with a more complex continuation function.
                        // That continuation function will intercept all actions taken by the task,
                        // matching them to the task nodes from root through prior.
                        // Once we've exhausted the task nodes we want to replay, we defer to realActions,
                        // which switches us to just running normally.
                        var next: TaskNode? = root.next
                        var nextTask: Task<*> = root.task
                        // The first node after the root should be a "begin" node. Skip it.
                        check(next is StepBeginNode) {
                            "Internal error! Step following a root task node was not a ${StepBeginNode::class.simpleName}"
                        }
                        next = next.next
                        var stepResult: Task.TaskStepResult<*>
                        while (true) {
                            dumpDotToFile(DebugLevel.MINOR, next, checkIntegrity = false)
                            stepResult = nextTask.runStep(object : Task.BasicTaskActions {
                                override fun <V> read(cell: Cell<V>): V = if (next != null) {
                                    check(next is ReadNode) {
                                        "Internal error! Task replay (read) did not align with history (${next!!::class.simpleName})"
                                    }
                                    @Suppress("UNCHECKED_CAST")
                                    // Replay the value we read last time, and advance next
                                    val result = ((next as ReadNode).cell as CellNode<V>).value
                                    next = next?.next
                                    dumpDotToFile(DebugLevel.MINOR, next, checkIntegrity = false)
                                    result
                                } else {
                                    realActions.read(cell)
                                }

                                override fun <V> emit(cell: Cell<V>, effect: Effect<V>) = if (next != null) {
                                    check(next is WriteNode) {
                                        "Internal error! Task replay (write) did not align with history (${next!!::class.simpleName})"
                                    }
                                    // Ignore the write and advance next
                                    next = next?.next
                                    dumpDotToFile(DebugLevel.MINOR, next, checkIntegrity = false)
                                } else {
                                    realActions.emit(cell, effect)
                                }

                                override fun <V> report(value: V) = if (next != null) {
                                    check(next is ReportNode<*>) {
                                        "Internal error! Task replay (report) did not align with history (${next!!::class.simpleName})"
                                    }
                                    // Ignore the report and advance next
                                    next = next?.next
                                    dumpDotToFile(DebugLevel.MINOR, next, checkIntegrity = false)
                                } else {
                                    realActions.report(value)
                                }
                            })
                            // If we exhausted the replay doing that runStep, return this as our final result.
                            if (next == null) break
                            // Otherwise, match that result to the next node, advance next to skip the yielding action, and go again.
                            when (stepResult) {
                                is Task.TaskStepResult.Await<*> -> {
                                    check(next is AwaitNode) {
                                        "Internal error! Task replay (await) did not align with history (${next!!::class.simpleName})"
                                    }
                                    // Awaiting once can produce multiple Await nodes, as the condition is re-checked.
                                    // Skip all of them.
                                    while (next is AwaitNode) next = next?.next
                                    // Then skip the begin node that follows an await-chain
                                    check(next is StepBeginNode) {
                                        "Internal error! Await-chain not followed by Begin node"
                                    }
                                    next = next?.next
                                    nextTask = stepResult.continuation
                                }

                                is Task.TaskStepResult.Complete<*> -> {
                                    check(false) {
                                        "Internal error! Task replay (complete) did not align with history (${next!!::class.simpleName})"
                                    }
                                }

                                is Task.TaskStepResult.Restart<*> -> {
                                    // We only ever replay part of a single task.
                                    // The task should never restart while replaying.
                                    check(false) {
                                        "Internal error! Task replay (restart) did not align with history (${next!!::class.simpleName})"
                                    }
                                }

                                is Task.TaskStepResult.Spawn<*, *> -> {
                                    check(next is SpawnNode) {
                                        "Internal error! Task replay (spawn) did not align with history (${next!!::class.simpleName})"
                                    }
                                    next = next?.next
                                    nextTask = stepResult.continuation
                                }
                            }
                        }
                        stepResult
                    }
                }

                is RevokeMergeOpportunity -> {
                    // We use the RevokeMergeOpportunity frontier action instead of directly calling fullyRevokeTask
                    // when we're doing incremental resimulation, hoping to merge an existing RootTaskNode
                    // with a re-simulated one.
                    // For example, the reporting task for a resource repeats, placing a new root exactly where the old
                    // root was, and that can be merged instead of re-done.
                    // If we merge, we remove the RevokeMergeOpportunity action.
                    // Hence if we run the RevokeMergeOpportunity action, we failed to merge this root.
                    // Remove that root, but optimistically add its downstream repeat as a merge opportunity.
                    rootMergeOpportunities.remove(action.node.task)
                    revokeWithMergeOpportunity(action.node)
                }

                is CheckCell -> {
                    checkCell(action.node)
                }

                is CheckCondition -> {
                    action.node.reads.clear()
                    val readActions = object : ReadActions {
                        override fun <V> read(cell: Cell<V>): V {
                            // Get the appropriate cell node to read:
                            val cellNode = getCellNode(cell, action.time)
                            // Record bidirectionally that this read happened:
                            cellNode.awaiters += action.node
                            action.node.reads[cellNode] = cellNode.value
                            // Finally, return the value we read
                            return cellNode.value
                        }
                    }
                    // Schedule the next evaluation or the continuation, as appropriate
                    var result = action.node.condition(readActions)
                    var resultTime = result.time?.let {
                        if (it > ZERO) {
                            SimulationTime(
                                action.time.instant + it.toKotlinDuration(),
                                branch = action.time.branch)
                                // Filter out results after the end of the plan
                                .takeIf { it.instant < planEnd }
                        } else {
                            action.time.nextStep()
                        }
                    }
                    fun SimulationTime.beforeResultTime(): Boolean =
                        resultTime?.let { this isCausallyBefore it } ?: true
                    var isSatisfied = result is SatisfiedAt

                    // Find all the cell nodes we might read between now and result.time
                    val frontierCellNodes = PriorityQueue<CellNode<*>>(compareBy { it.time })
                    // Start exploring all cell nodes after the read cell nodes.
                    action.node.reads.keys.flatMapTo(frontierCellNodes) { it.next }
                    while (true) {
                        // Only explore until resultTime
                        val node = frontierCellNodes.poll()?.takeIf { it.time.beforeResultTime() } ?: break
                        when (node) {
                            is CellStepNode -> {
                                // If we find a step node, add it as read by this awaiter.
                                action.node.reads[node] = node.value
                                node.awaiters += action.node
                                // Also explore its next nodes
                                frontierCellNodes += node.next
                            }
                            is CellMergeNode, is CellWriteNode -> {
                                // If we find a write or merge, then the read cell changes while we're awaiting.
                                // This would cancel the await on the result, causing re-evaluation in response to the write.
                                // That's equivalent to just being unsatisfied until the time of this write
                                isSatisfied = false
                                // We're ignoring the time in result below, and using resultTime instead.
                                // Set that to the tasks reacting to the cell node.
                                // Note that if there are concurrent cell writes, all of them have the same
                                // nextTaskBatch, so it suffices to find any one of them and stop.
                                resultTime = node.time.nextTaskBatch().copy(branch = action.time.branch)
                            }
                        }
                    }

                    action.node.next?.let {
                        // If we have a next node, and we shouldn't, revoke it.
                        val correctType = if (isSatisfied) it is StepBeginNode else it is AwaitNode
                        if (resultTime == null || !correctType || it.time != resultTime) {
                            revokeWithMergeOpportunity(it)
                        }
                    }

                    // If we want a next node and still have one, it passed the filter above, so just re-use it.
                    // Otherwise, if we want a next node but don't have one, build it.
                    if (resultTime != null && action.node.next == null) {
                        action.node.next = if (isSatisfied) {
                            StepBeginNode(
                                nextNodeId++,
                                resultTime,
                                action.node,
                                action.node.continuation,
                            ).also {
                                frontier += RunTask(it)
                                taskNodes += it
                            }
                        } else {
                            AwaitNode(
                                nextNodeId++,
                                resultTime,
                                action.node,
                                action.node.condition,
                                continuation = action.node.continuation
                            ).also {
                                frontier += CheckCondition(it)
                                taskNodes += it
                            }
                        }
                    }
                }
            }
        }
    }

    private fun <T> checkCell(node: CellNode<T>) {
        val computedValue = when (node) {
            is CellMergeNode<T> -> {
                // Roll up the net effect of each branch, and merge according to this cell's merge rule.
                val netEffect = node.prior
                    .map { it.branchNetEffect(node.batchStart) }
                    .reduce(node.cell.mergeConcurrentEffects)
                // Compute the value this node should have
                netEffect(node.batchStart.value)
            }
            is CellStepNode<T> -> {
                // Apply the step duration to the prior cell value.
                node.cell.stepBy(node.prior.value, node.step)
            }
            is CellWriteNode<T> -> {
                // Apply the effect to the prior cell value.
                node.effect(checkNotNull(node.prior) {
                    "Internal error! Cannot check initial write node."
                }.value)
            }
        }
        // If the value actually changed, re-run all readers and awaiters, and check all next cell nodes.
        if (computedValue != node.value) {
            node.value = computedValue
            node.next.forEach { frontier += CheckCell(it) }
        }
        // Regardless of whether the value changed, check all reads and awaiters.
        // If the value they last saw for this cell differs from the value it has now, run it.
        for (read in node.reads) {
            if (read.value != node.value) {
                frontier += RunTask(read)
            }
        }
        // Awaiters get a CheckCondition action instead of a RunTask action.
        // That way, if the condition evaluates equivalently for the new cell value,
        // we don't needlessly re-run a task.
        for (awaiter in node.awaiters) {
            if (awaiter.reads.getValue(node) != node.value) {
                frontier += CheckCondition(awaiter)
            }
        }
    }

    // TODO: If we ever do cleanup on taskBranch to keep it from growing indefinitely,
    //   update the branch assignment rule to account for that.
    private fun SimulationTime.branchFor(task: Task<*>): SimulationTime =
        copy(branch = taskBranch.computeIfAbsent(task) { taskBranch.size })

    private fun <T> CellWriteNode<T>.branchNetEffect(batchStart: CellNode<T>) =
        // The branch comprises all our prior write nodes until batchStart
        generateSequence(this) { it.prior?.takeUnless { it === batchStart } as CellWriteNode<T>? }
            .map { it.effect }
            // Branch nodes are collected in reverse order, merge by compose instead of andThen
            .reduce(Effect<T>::compose)

    /**
     * Run [continuation], appending nodes after this [TaskNode] to record its actions.
     * Mutates the simulator as needed to record all consequences of continuing this task.
     */
    private fun TaskNode.continueWith(continuation: (Task.BasicTaskActions) -> Task.TaskStepResult<*>) {
        // Expand this task node
        var lastTaskStepNode: TaskNode = this
        // Go find the root task, to look up root merge opportunities...
        // TODO: Key off of something easier to find...
        val rootTaskNode = generateSequence(this) { it.prior }.first { it is RootTaskNode }
        val rootTask = (rootTaskNode as RootTaskNode).task
        // We can merge only if this action is happening on the same branch as the merge opportunity.
        val mergeOpportunity: RootTaskNode? = rootMergeOpportunities[rootTask]?.takeIf { it.time sameBranchAs time }
        val basicTaskActions = object : Task.BasicTaskActions {
            override fun <V> read(cell: Cell<V>): V {
                // Look up the cell node
                val cellNode = getCellNode(cell, lastTaskStepNode.time)
                // Record this read in the graph
                lastTaskStepNode = ReadNode(
                    nextNodeId++,
                    lastTaskStepNode.time.nextStep(),
                    lastTaskStepNode,
                    cellNode,
                    cellNode.value
                ).also {
                    lastTaskStepNode.next = it
                    cellNode.reads += it
                    taskNodes += it
                }
                dumpDotToFile(DebugLevel.MINOR, lastTaskStepNode)
                // Return the value
                return cellNode.value
            }

            override fun <V> emit(cell: Cell<V>, effect: Effect<V>) {
                // Look up the cell node we're writing to
                val writeTime = lastTaskStepNode.time.nextStep()
                val priorCellNode = getCellNode(cell, writeTime)
                val writeNode = CellWriteNode(
                    nextNodeId++,
                    writeTime,
                    cell,
                    effect(priorCellNode.value),
                    priorCellNode,
                    effect,
                )
                getCellNodes(cell)[writeNode.time] = writeNode
                // Migrate any readers and awaiters that need it, from prior to write
                migrateDependentTasks(priorCellNode, writeNode)
                // Schedule the write node to be checked
                frontier += CheckCell(writeNode)

                // Deal with any branching that may have happened.
                when (priorCellNode.next.size) {
                    0 -> {
                        // No additional links need to be changed, we're at the edge of the graph.
                    }
                    1 -> {
                        val adjacentCellNode = priorCellNode.next.single()
                        if (adjacentCellNode.time isConcurrentWith writeNode.time) {
                            // There's exactly one branch, and it's concurrent with this write node.
                            // Follow the concurrent branch to its tip
                            val concurrentTip = generateSequence(adjacentCellNode) { it.next.singleOrNull() }
                                .first { it.next.singleOrNull()?.let { it.time isCausallyAfter writeNode.time } ?: true }
                            // Then build and insert a merge node at the tip of that branch
                            val mergeNode = CellMergeNode(
                                nextNodeId++,
                                writeTime.nextCellBatch().copy(branch = 0),
                                cell,
                                // Use the concurrent tip's value, which was the value observed at this time before this insertion.
                                concurrentTip.value,
                                priorCellNode,
                                mutableListOf(),
                            )
                            cellNodes.getValue(cell)[mergeNode.time] = mergeNode
                            // If the concurrent branch tip has a next node, link that next node to the merge instead
                            // TODO: Is it possible for the concurrent branch tip to have multiple next nodes?
                            //    I think so, in the case of spawning a parallel child task...
                            //    Reproduce this situation in a test.
                            concurrentTip.next.forEach { afterWrite ->
                                mergeNode.next += afterWrite
                                when (afterWrite) {
                                    is CellMergeNode<V> -> check (false) {
                                        "Internal error! A single branch should not end with a merge node."
                                    }
                                    is CellStepNode<V> -> {
                                        afterWrite.prior = mergeNode
                                    }
                                    is CellWriteNode<V> -> {
                                        afterWrite.prior = mergeNode
                                    }
                                }
                            }
                            // Migrate readers and awaiters from branch tips to the merge node, as needed
                            migrateDependentTasks(concurrentTip, mergeNode)
                            migrateDependentTasks(writeNode, mergeNode)
                            // Then, connect both this and the concurrent branch to the merge node
                            concurrentTip.next.clear()
                            concurrentTip.next += mergeNode
                            mergeNode.prior += concurrentTip as CellWriteNode
                            writeNode.next += mergeNode
                            mergeNode.prior += writeNode
                            // Finally, schedule mergeNode to be checked once this branch is done,
                            // which will actually merge the branches' net effects.
                            frontier += CheckCell(mergeNode)
                        } else {
                            // We're not adding a concurrent branch, so just insert the write directly.
                            when (adjacentCellNode) {
                                is CellMergeNode -> {
                                    adjacentCellNode.prior -= priorCellNode as CellWriteNode
                                    adjacentCellNode.prior += writeNode
                                }
                                is CellStepNode -> {
                                    adjacentCellNode.prior = writeNode
                                }
                                is CellWriteNode -> {
                                    adjacentCellNode.prior = writeNode
                                }
                            }
                            priorCellNode.next -= adjacentCellNode
                            writeNode.next += adjacentCellNode
                            // Finally, add an action to re-check adjacent, as we've inserted a write node ahead of it.
                            frontier += CheckCell(adjacentCellNode)
                        }
                    }
                    else -> {
                        val merge = generateSequence(priorCellNode.next.first()) { it.next.single() }
                            .first { it is CellMergeNode } as CellMergeNode
                        // Link write in as a new branch of the merge node
                        writeNode.next += merge
                        merge.prior += writeNode
                        // Then schedule the merge node to be re-checked
                        frontier += CheckCell(merge)
                    }
                }
                priorCellNode.next += writeNode

                // Having constructed the cell's write node, now construct the next step node for the task.
                lastTaskStepNode = WriteNode(
                    nextNodeId++,
                    writeTime,
                    lastTaskStepNode,
                    writeNode,
                ).also {
                    // Also add the edges from the prior task step and from the cell write node to this.
                    lastTaskStepNode.next = it
                    writeNode.writer = it
                    taskNodes += it
                }
                dumpDotToFile(DebugLevel.MINOR, lastTaskStepNode)
            }

            override fun <V> report(value: V) {
                // Record this report in the task graph and issue it to the reportHandler
                lastTaskStepNode = ReportNode(
                    nextNodeId++,
                    lastTaskStepNode.time.nextStep(),
                    lastTaskStepNode,
                    value,
                ).also {
                    lastTaskStepNode.next = it
                    taskNodes += it
                    reportHandler.report(it)
                }
                dumpDotToFile(DebugLevel.MINOR, lastTaskStepNode)
            }
        }
        when (val result = continuation(basicTaskActions)) {
            is Task.TaskStepResult.Await<*> -> {
                // Create an await node and add it to the frontier, to be checked in the next batch.
                lastTaskStepNode = AwaitNode(
                    nextNodeId++,
                    lastTaskStepNode.time.nextTaskBatch(),
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
            is Task.TaskStepResult.Complete<*> -> {
                // Nothing to do.
                // Note that if there was a merge opportunity, there's also a scheduled FrontierAction
                // to revoke it. We'll let that frontierAction handle things.
            }
            is Task.TaskStepResult.Restart<*> -> {
                if (mergeOpportunity != null) {
                    // Accept the opportunity to merge instead of re-run, by
                    // removing the scheduled action to revoke the merge opportunity.
                    frontier -= RevokeMergeOpportunity(mergeOpportunity)
                    // Link the merge opportunity as the next step of the task
                    mergeOpportunity.prior = lastTaskStepNode
                    lastTaskStepNode.next = mergeOpportunity
                    // Renumber the steps of the merged task to be consistent with the prior task node
                    renumberSteps(mergeOpportunity, lastTaskStepNode.time.step + 1)
                } else {
                    // Add a new root task, from which we can restart the next task at any time.
                    lastTaskStepNode = RootTaskNode(
                        nextNodeId++,
                        // Restarting does not yield to the engine, it's the next step of this task.
                        lastTaskStepNode.time.nextStep(),
                        result.continuation,
                        lastTaskStepNode,
                    ).also {
                        lastTaskStepNode.next = it
                        frontier += StartTask(it)
                        taskNodes += it
                    }
                }
            }
            is Task.TaskStepResult.Spawn<*, *> -> {
                // Spawning is a yielding action, so add our continuation to the next batch.
                // Also add the child task to the next batch, as a root task node so it can be independently restarted.
                lastTaskStepNode = SpawnNode(
                    nextNodeId++,
                    lastTaskStepNode.time.nextTaskBatch(),
                    lastTaskStepNode,
                    RootTaskNode(
                        nextNodeId++,
                        lastTaskStepNode.time.nextTaskBatch().branchFor(result.child),
                        result.child,
                    ).also {
                        frontier += StartTask(it)
                        taskNodes += it
                    },
                    result.continuation,
                    next = mergeOpportunity,
                ).also {
                    lastTaskStepNode.next = it
                    mergeOpportunity?.prior = it
                    it.child.prior = it
                    frontier += RunTask(it)
                    taskNodes += it
                }
            }
        }
    }

    /**
     * Migrate dependent tasks (readers and awaiters) from a cell node to another (write or merge) cell node,
     * which is being injected immediately after "from".
     */
    private fun <T> migrateDependentTasks(from: CellNode<T>, to: CellNode<T>) {
        val readIterator = from.reads.iterator()
        while (readIterator.hasNext()) {
            val read = readIterator.next()
            if (read.time isCausallyAfter to.time) {
                // Switch the read over to this cell node
                to.reads += read
                read.cell = to
                readIterator.remove()
                // Preserve the read value when switching it over;
                // if the value the reader read disagrees with this write, it'll get re-checked by CheckCell.
            }
        }

        // Make a shallow copy of from.awaiters to iterate over, because we may remove more than just the current iterated element
        for (awaiter in from.awaiters.toList()) {
            if (awaiter.time isCausallyAfter to.time) {
                // If awaiter is after write, replace prior with write as the cell node to read
                // Preserve the read value as we do so. CheckCell will re-evaluate the condition if needed.
                awaiter.reads[to] = awaiter.reads.remove(from)
                from.awaiters -= awaiter
                to.awaiters += awaiter
            } else if (awaiter.next?.let { it.time isCausallyAfter to.time } ?: true) {
                // Awaiter is causally before or concurrent with "to", since it's not causally after "to".
                // If awaiter has a next causally after "to", or has no next,
                // then the awaiter is active when "to" happens.
                // The condition needs to be re-evaluated in the next task batch after this write.

                // If awaiter.next is such an await node, just swap out the read cell node for this cell.
                // Otherwise, build such an await node and schedule it to be checked.
                val recheckTime = to.time.nextTaskBatch().copy(branch = awaiter.time.branch)
                if (awaiter.next?.let { it.time == recheckTime } ?: false) {
                    (awaiter.next as? AwaitNode)?.let { n ->
                        // Replace n's read of prior with a read of write, preserving the value read.
                        // CheckCell will re-run the await node if needed.
                        n.reads[to] = n.reads.remove(from)
                        from.awaiters -= n
                        to.awaiters += n
                    }
                    // If awaiter.next is not an AwaitNode, condition was satisfied concurrent with write.
                    // In this edge case, write is not observed, and condition remains satisfied.
                    // Nothing to do.
                } else {
                    // The next step of the awaiter was originally after recheckTime, or does not exist.
                    // Hence, write interrupts the wait that happened before, injecting a new await node.
                    awaiter.next = AwaitNode(
                        nextNodeId++,
                        recheckTime,
                        awaiter,
                        awaiter.condition,
                        continuation = awaiter.continuation,
                        next = awaiter.next
                    ).also {
                        it.next?.prior = it
                        taskNodes += it
                        frontier += CheckCondition(it)
                    }
                }
                // TODO: Write a test case stress-testing dense writes to an awaited cell
            }
        }
    }

    private fun renumberSteps(task: TaskNode, stepNumber: Int) {
        var node: TaskNode? = task
        var n = stepNumber
        while (node != null) {
            node.time.step = n++
            node = node.next?.takeIf { it.time sameBranchAs task.time }
        }
    }

    /**
     * Revoke all task nodes starting from [task].
     * This includes repetitions and children spawned by this task.
     */
    private fun fullyRevokeTask(task: TaskNode) {
        var next: TaskNode? = task
        while (next != null) {
            next = revokeSingleTask(next)
        }
    }

    private fun revokeWithMergeOpportunity(task: TaskNode): RootTaskNode? = revokeSingleTask(task)?.also {
        // Unlink the merge opportunity from any prior task nodes, so those nodes can be garbage collected...
        it.prior = null
        // Renumber the merge opportunity to exist "late" on the branch,
        // giving any running tasks an opportunity to merge with it before the Revoke happens.
        renumberSteps(it, Int.MAX_VALUE / 2)
        frontier += RevokeMergeOpportunity(it)
        rootMergeOpportunities[it.task] = it
    }

    /**
     * Revoke all task nodes starting from [task], up to but not including its repeat (if applicable).
     * Children spawned by [task] will be fully revoked.
     *
     * @return RootTaskNodes following [task]
     */
    private fun revokeSingleTask(task: TaskNode): RootTaskNode? {
        // Remove our record of this node
        taskNodes -= task
        // Remove any frontier action(s) related to this node
        frontier -= RunTask(task)
        if (task is RootTaskNode) frontier -= StartTask(task)
        if (task is AwaitNode) frontier -= CheckCondition(task)
        // Unlink this node from its prior
        // The 'if' is in case we're unlinking a root node from a spawn.
        // In that case, task is the spawn's child, not its next.
        // To revoke a root task spawned by a parent task, we must be revoking the parent task too, so no need to unlink the child.
        task.prior?.apply { if (next === task) next = null }
        // Do any special actions based on this node type
        when (task) {
            is ReadNode -> task.cell.reads -= task
            is ReportNode<*> -> reportHandler.revoke(task)
            is WriteNode -> revokeCell(task.cell)
            is RootTaskNode -> { /* Nothing to do */ }
            is AwaitNode -> task.reads.keys.forEach { it.awaiters -= task }
            // The child of a revoked parent can never be merged.
            // If the root of this parent wasn't merged, it's because this task and any candidate for merging
            // couldn't be proven to be in the same state. Since children (may) inherit state from their parents,
            // any child spawned from possibly-different parents may itself be possibly-different, so cannot be merged.
            is SpawnNode -> fullyRevokeTask(task.child)
            is StepBeginNode -> { /* Nothing to do */ }
        }
        // Don't check integrity now, we're purposely breaking the integrity of the next node by removing this node.
        dumpDotToFile(DebugLevel.MINOR, task.next, checkIntegrity = false)
        // Continue revoking the rest of this task
        return task.next?.let { it as? RootTaskNode ?: revokeSingleTask(it) }
    }

    private fun <T> revokeCell(cell: CellNode<T>) {
        // Remove the cell node from cellNodes
        getCellNodes(cell.cell) -= cell.time
        // Remove any pending actions related to this node
        frontier -= CheckCell(cell)
        // Repair the DAG, connecting nodes before and after this cell node
        val prior: CellNode<T>
        when (cell) {
            is CellWriteNode -> {
                prior = checkNotNull(cell.prior) {
                    "Cannot revoke initial cell write node!"
                }
                prior.next -= cell
                for (next in cell.next) {
                    when (next) {
                        is CellWriteNode -> {
                            next.prior = prior
                            prior.next += next
                            frontier += CheckCell(next)
                        }
                        is CellStepNode -> {
                            next.prior = prior
                            prior.next += next
                            frontier += CheckCell(next)
                        }
                        is CellMergeNode -> {
                            next.prior -= cell
                            when {
                                prior.time sameBranchAs cell.time -> {
                                    // We're shortening this branch without removing it.
                                    // Link prior to next, then schedule next to be re-checked.
                                    next.prior += prior as CellWriteNode
                                    prior.next += next
                                    frontier += CheckCell(next)
                                }
                                next.prior.size <= 1 -> {
                                    // We've removed the second-to-last branch, revoke the merge itself too.
                                    revokeCell(next)
                                }
                                else -> {
                                    // We removed a branch, leaving at least two branches. Keep the merge.
                                    frontier += CheckCell(next)
                                }
                            }
                        }
                    }
                }
                // A CellWriteNode is revoked only when the writer is revoked. Nothing to do for cell.writer
            }
            is CellStepNode -> TODO("Revoking cell step nodes")
            is CellMergeNode -> {
                prior = checkNotNull(cell.prior.singleOrNull()) {
                    "Internal error! Only single-branch merge nodes can be revoked."
                }
                // Disconnect the branch from this merge
                prior.next -= cell
                // For each successor of this merge, connect it directly to the branch and schedule it to be rechecked.
                for (next in cell.next) {
                    when (next) {
                        is CellWriteNode -> {
                            next.prior = prior
                            prior.next += next
                            frontier += CheckCell(next)
                        }
                        is CellStepNode -> {
                            next.prior = prior
                            prior.next += next
                            frontier += CheckCell(next)
                        }
                        is CellMergeNode -> {
                            check(false) {
                                "Internal error! Merge nodes cannot contain empty branches."
                            }
                        }
                    }
                }
            }
        }
        // Any reads on the revoked cell node should instead point at the prior cell node.
        cell.reads.forEach {
            it.cell = prior
            prior.reads += it
        }
        // Anything awaiting this cell should instead await prior
        // Preserve the read value when moving this link, to be checked by CheckCell.
        cell.awaiters.forEach {
            // TODO: When could we remove an await node, rather than just re-assign it?
            it.reads[prior] = it.reads.remove(cell)
            prior.awaiters += it
        }
        // Finally, schedule the prior cell node to be re-checked, to check the reads and awaits we just re-assigned.
        frontier += CheckCell(prior)
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
        if (t isConcurrentWith time) {
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
                nextNodeId++,
                time.cellSteppingBatch(),
                cell,
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
                if (awaiter.next?.let { it.time isCausallyAfter stepNode.time } ?: true) {
                    stepNode.awaiters += awaiter
                    // TODO: Think carefully about this edge. It violates causal order!
                    awaiter.reads[stepNode] = stepNode.value
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

    private fun SimulationTime.nextStep() = copy(step = step + 1)

    private fun SimulationTime.nextTaskBatch() =
        // "Task batches" are always even, so add (batch + 1 mod 2) to correct if we're coming from an odd batch number
        copy(batch = batch + 1 + ((batch + 1) % 2), step = 0)

    private fun SimulationTime.nextCellBatch() =
        // "Cell batches" are always odd, so add (batch mod 2) to correct if we're coming from an even batch number
        copy(batch = batch + 1 + (batch % 2), step = 0)

    private fun SimulationTime.batchStart() = copy(branch = 0, step = 0)

    private fun SimulationTime.cellSteppingBatch() =
        // Conceptually, cells are stepped in a special "batch", before any tasks are run
        copy(batch = -1, branch = 0, step = 0)

    // TODO: Consider cleaning up time comparisons with overloads that work on SGNode

    infix fun SimulationTime.isConcurrentWith(other: SimulationTime): Boolean =
        instant == other.instant && batch == other.batch && branch != other.branch

    infix fun SimulationTime.isCausallyBefore(other: SimulationTime): Boolean =
        this < other && !(this isConcurrentWith other)

    infix fun SimulationTime.isCausallyAfter(other: SimulationTime): Boolean =
        this > other && !(this isConcurrentWith other)

    infix fun SimulationTime.sameBranchAs(other: SimulationTime): Boolean =
        instant == other.instant && batch == other.batch && branch == other.branch

    private sealed interface FrontierAction {
        val node: SGNode
        val time: SimulationTime get() = node.time

        data class StartTask(override val node: RootTaskNode) : FrontierAction
        data class RunTask(override val node: TaskNode) : FrontierAction
        data class CheckCell(override val node: CellNode<*>) : FrontierAction
        data class CheckCondition(override val node: AwaitNode) : FrontierAction
        data class RevokeMergeOpportunity(override val node: RootTaskNode) : FrontierAction
    }

    /**
     * Debugging function which dumps the current simulation DAG as a Graphviz (dot) file
     *
     * @param checkIntegrity Raise an [IllegalStateException] if the graph doesn't meet standard integrity checks.
     */
    private fun dumpDot(
        highlightNode: SGNode? = null,
        checkIntegrity: Boolean = true,
    ): String {
        fun checkIntegrity(condition: Boolean, lazyMessage: () -> String) =
            check (!checkIntegrity || condition) { lazyMessage() + " integrity check failed" }

        val graphBuilder = StringBuilder("digraph ${KernelIncrementalSimulator::class.simpleName} {\n")
        graphBuilder.append("  concentrate = true\n")
        var n = 0
        val cellDotId = mutableMapOf<CellNode<*>, String>()
        val taskDotId = mutableMapOf<TaskNode, String>()
        val cells = mutableMapOf<CellNode<*>, Cell<*>>()
        val ranks = TreeMap<SimulationTime, MutableList<SGNode>>()
        // The "rank" is just the time ignoring the branch
        fun SimulationTime.rank() = copy(branch = 0)
        // The "file" is the horizontal position within the rank
        fun SimulationTime.file() = branch
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
                val fillColor = when {
                    node === highlightNode -> "#69aa7c"
                    node in frontierModifier -> "#50a0f4"
                    else -> null
                }
                val styling = fillColor?.let { ", style = filled, fillcolor = \"$it\"" } ?: ""
                val labelSuffix = frontierModifier[node]?.let{ "** $it\\l" } ?: ""
                val label: String
                val shape: String
                val id: String
                when (node) {
                    is CellNode<*> -> {
                        val cell = cells.getValue(node)
                        label = when (node) {
                            is CellMergeNode<*> -> "Merge ${cell.name}"
                            is CellStepNode<*> -> "Step ${cell.name}"
                            is CellWriteNode<*> -> "Write ${cell.name}"
                        } + "\\l${node.value}"
                        shape = "ellipse"
                        id = cellDotId.getValue(node)
                    }
                    is TaskNode -> {
                        label = when (node) {
                            is ReadNode -> "Read ${node.cell.cell.name}"
                            is ReportNode<*> -> "Report ${node.content}"
                            is WriteNode -> "Write '${node.cell.effect}'"
                            is RootTaskNode -> "Root ${node.task.id.name}"
                            is AwaitNode -> "Await ${node.condition}" + (if (node.continuation != null) " ++" else "")
                            is SpawnNode -> "Spawn" + (if (node.continuation != null) " ++" else "")
                            is StepBeginNode -> "Begin" + (if (node.continuation != null) " ++" else "")
                        }
                        shape = "box"
                        id = taskDotId.getValue(node)
                    }
                }
                graphBuilder.append("    ", id, " [shape = $shape$styling, label = \"$label\\l${node.time}\\l$labelSuffix\"]\n")
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
                    node.reads.keys.forEach {
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

/**
 * A generalization of [ReportHandler] which allows the simulator to revoke a report it issued previously,
 * in response to incremental changes to the simulation.
 */
interface IncrementalReportHandler {
    fun report(report: ReportNode<*>)
    fun revoke(report: ReportNode<*>)
}
