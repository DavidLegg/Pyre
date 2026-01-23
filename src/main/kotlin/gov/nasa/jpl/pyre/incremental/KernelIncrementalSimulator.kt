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
import gov.nasa.jpl.pyre.kernel.UnsatisfiedUntil
import gov.nasa.jpl.pyre.kernel.toKotlinDuration
import gov.nasa.jpl.pyre.kernel.toPyreDuration
import gov.nasa.jpl.pyre.utilities.identity
import java.io.File
import java.util.PriorityQueue
import java.util.TreeMap
import kotlin.collections.plusAssign
import kotlin.reflect.KType
import kotlin.time.Instant

/**
 * Support for [GraphIncrementalPlanSimulation], which implements graph-based incremental simulation at the kernel level.
 */
class KernelIncrementalSimulator(
    planStart: Instant,
    private val planEnd: Instant,
    constructPlan: context (BasicInitScope) () -> List<KernelActivity>,
    private val reportHandler: IncrementalReportHandler
) {
    /** All cell nodes allocated in the DAG */
    private val cellNodes: MutableMap<Cell<*>, TreeMap<SimulationTime, CellNode<*>>> = mutableMapOf()
    // TODO: This taskNodes set is likely overkill, but useful for debugging. Get rid of it once we have confidence in the simulator.
    /** All task nodes allocated in the DAG */
    private val taskNodes: MutableSet<TaskNode> = mutableSetOf()
    // TODO: Find a way to prevent taskBranch from growing indefinitely as tasks are added and removed
    /** The permanently-assigned branch number for each task. */
    private val taskBranch: MutableMap<Task<*>, Int> = mutableMapOf()
    // TODO: We may want to de-duplicate frontier actions, and/or sort them by type
    //   Using a TreeSet with a "thenBy" that gets an integer sorting key for action type might accomplish this...
    //   I should prove that before I make the code change, though.
    /** The work list of actions to resolve the DAG. */
    private val frontier: PriorityQueue<FrontierAction> = PriorityQueue(compareBy { it.time })
    /** Root task nodes corresponding to activities in the plan, recorded to facilitate revoking tasks. */
    private val planTaskNodes: MutableMap<KernelActivity, RootTaskNode> = mutableMapOf()
    /** Root nodes with which we may merge restart requests, rather than re-running. */
    private val rootMergeOpportunities: MutableMap<Task<*>, RootTaskNode> = mutableMapOf()

    init {
        // Init happens before any tasks, at plan start.
        val cellAllocTime = SimulationTime(planStart, batch = -1)
        var initTime = cellAllocTime
        var startTime = initTime.nextBatch()
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
                        put(initTime, CellWriteNode(cellAllocTime, it, value, null, identity()))
                    }
                }

            override fun <T> spawn(name: Name, step: PureTaskStep<T>) {
                // Schedule the task on its own branch, as part of the first batch.
                val task = Task.of(name, step)
                frontier += StartTask(RootTaskNode(startTime.branchFor(task), task).also(taskNodes::add))
            }

            override fun <T> read(cell: Cell<T>): T {
                // Since there cannot be effects during init,
                // we may safely assume the first node to be the write node added during allocation
                return (getCellNodes(cell).firstEntry().value as CellWriteNode<T>).value
            }

            override fun <T> report(value: T) {
                lastReport = ReportNode(initTime, lastReport, value).also { lastReport?.next = it }
                reportHandler.report(lastReport)
            }
        }
        val activities = constructPlan(basicInitScope)
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
            if (DEBUG) File("/Users/dlegg/Code/Pyre/tmp/tmp-final.dot").writeText(dumpDot(checkIntegrity = false))
        }
    }

    private val DEBUG = false

    private fun resolve() {
        var debugStep = 0
        while (true) {
            // DEBUG
            if (DEBUG) File("/Users/dlegg/Code/Pyre/tmp/tmp${debugStep++.toString().padStart(6, '0')}.dot").writeText(dumpDot())
            when (val action = frontier.poll() ?: break) {
                is StartTask -> {
                    // Start the job in the next step. Roots are already assigned to their own branches.
                    action.node.next = StepBeginNode(
                        action.time,
                        action.node,
                        action.node.task,
                    ).also {
                        frontier += ContinueTask(it)
                        taskNodes += it
                    }
                }

                is ContinueTask -> {
                    // The "basic" form of continuing a task, what we do for freshly-added tasks.
                    action.node.continueWith(action.node.continuation::runStep)
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
                    // Also schedule that repeat to be revoked if it isn't merged.
                    rootMergeOpportunities.remove(action.node.task)
                    revokeSingleTask(action.node)?.let {
                        frontier += RevokeMergeOpportunity(it)
                        rootMergeOpportunities[it.task] = it
                    }
                }

                is RerunTask -> {
                    // Revoke the rest of this task, up to any repeat.
                    // If it repeats, add that repeat as a merge opportunity, and schedule it to be revoked if not merged.
                    revokeSingleTask(action.node)?.let {
                        frontier += RevokeMergeOpportunity(it)
                        rootMergeOpportunities[it.task] = it
                    }
                    val prior = checkNotNull(action.node.prior) {
                        "Internal error! Cannot rerun the first node in a task chain."
                    }
                    // Find the root node from which to replay this task
                    val root = checkNotNull(generateSequence(prior) { it.prior }.firstOrNull { it is RootTaskNode }) {
                        "Internal error! Task chain does not start at a root task node."
                    } as RootTaskNode
                    prior.continueWith { realActions ->
                        // We replay the task by continuing from prior, but with a more complex continuation function.
                        var next: TaskNode? = root.next
                        // That continuation function will intercept all actions taken by the task,
                        // matching them to the task nodes from root through prior.
                        // Once we've exhausted the task nodes we want to replay, we defer to realActions.
                        var stepResult: Task.TaskStepResult<*>
                        while (true) {
                            stepResult = root.task.runStep(object : Task.BasicTaskActions {
                                override fun <V> read(cell: Cell<V>): V = if (next != null) {
                                    check(next is ReadNode) {
                                        "Internal error! Task replay (read) did not align with history (${next!!::class.simpleName}"
                                    }
                                    @Suppress("UNCHECKED_CAST")
                                    // Replay the value we read last time, and advance next
                                    val result = ((next as ReadNode).cell as CellNode<V>).value
                                    next = next?.next
                                    result
                                } else {
                                    realActions.read(cell)
                                }

                                override fun <V> emit(cell: Cell<V>, effect: Effect<V>) = if (next != null) {
                                    check(next is WriteNode) {
                                        "Internal error! Task replay (write) did not align with history (${next!!::class.simpleName}"
                                    }
                                    // Ignore the write and advance next
                                    next = next?.next
                                } else {
                                    realActions.emit(cell, effect)
                                }

                                override fun <V> report(value: V) = if (next != null) {
                                    check(next is ReportNode<*>) {
                                        "Internal error! Task replay (report) did not align with history (${next!!::class.simpleName}"
                                    }
                                    // Ignore the report and advance next
                                    next = next?.next
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
                                        "Internal error! Task replay (await) did not align with history (${next!!::class.simpleName}"
                                    }
                                    next = next?.next
                                }
                                is Task.TaskStepResult.Complete<*> -> {
                                    check(false) {
                                        "Internal error! Task replay (complete) did not align with history (${next!!::class.simpleName}"
                                    }
                                }
                                is Task.TaskStepResult.Restart<*> -> {
                                    // We only ever replay part of a single task.
                                    // The task should never restart while replaying.
                                    check(false) {
                                        "Internal error! Task replay (restart) did not align with history (${next!!::class.simpleName}"
                                    }
                                }
                                is Task.TaskStepResult.Spawn<*, *> -> {
                                    check(next is SpawnNode) {
                                        "Internal error! Task replay (spawn) did not align with history (${next!!::class.simpleName}"
                                    }
                                    next = next?.next
                                }
                            }
                        }
                        stepResult
                    }
                }

                is CheckCell -> {
                    when (action.node) {
                        is CellMergeNode<*> -> {
                            // We need to check our prior list (branches to merge).
                            // I think we must have more than one prior, or we would have deleted this node instead of checking it.
                            // Maybe that logic should be rolled into this step instead, though?
                            // Roll up the net effect of each branch and apply the merge.
                            // If that computed value is equal to this value, we're done.
                            // Otherwise, re-run all readers and awaiters, and check all next cell nodes.
                            TODO("check merge node")
                        }
                        is CellStepNode<*> -> {
                            // Apply the step duration to the prior cell value.
                            // If that computed value is equal to this value, we're done.
                            // Otherwise, re-run all readers and awaiters, and check all next cell nodes.
                            TODO("check step node")
                        }
                        is CellWriteNode<*> -> {
                            // Apply the effect to the prior cell value.
                            // If that computed value is equal to this value, we're done.
                            // Otherwise, re-run all readers and awaiters, and check all next cell nodes.
                            TODO("check write node")
                        }
                    }
                    // TODO: After writing the logic for each kind of cell node individually,
                    //   see if there's a nice way to refactor them to reduce duplication.
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
                                SimulationTime(
                                    action.time.instant + result.time.toKotlinDuration(),
                                    branch = action.time.branch)
                            } else {
                                action.time
                            }
                            if (satisfiedTime.instant < planEnd) {
                                action.node.next = StepBeginNode(
                                    satisfiedTime,
                                    action.node,
                                    action.node.continuation,
                                ).also {
                                    frontier += ContinueTask(it)
                                    taskNodes += it
                                }
                            }
                            // If satisfiedTime >= planEnd, don't schedule the node. It's the same as UnsatisfiedUntil(inf.)
                        }
                        is UnsatisfiedUntil -> {
                            // If we're unsatisfied only for a finite time, add and schedule an await for that time
                            // If that time is in the future, find and occupy a branch in batch 0 then.
                            // If that time is now, run in the next step; this await already occupies a branch.
                            result.time?.let { t ->
                                val recheckTime = if (t > ZERO) {
                                    SimulationTime(
                                        action.time.instant + t.toKotlinDuration(),
                                        branch = action.time.branch)
                                } else {
                                    action.time
                                }
                                if (recheckTime.instant < planEnd) {
                                    action.node.next = AwaitNode(
                                        recheckTime,
                                        action.node,
                                        action.node.condition,
                                        continuation = action.node.continuation,
                                    ).also {
                                        frontier += CheckCondition(it)
                                        taskNodes += it
                                    }
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

    // TODO: If we ever do cleanup on taskBranch to keep it from growing indefinitely,
    //   update the branch assignment rule to account for that.
    private fun SimulationTime.branchFor(task: Task<*>): SimulationTime =
        copy(branch = taskBranch.computeIfAbsent(task) { taskBranch.size })

    private interface ContinuationActions {
        val basicTaskActions: Task.BasicTaskActions
        val lastTaskStepNode: TaskNode
        val mergeOpportunity: RootTaskNode?
    }

    /**
     * Run [continuation], appending nodes after this [TaskNode] to record its actions.
     * Mutates the simulator as needed to record all consequences of continuing this task.
     */
    private fun TaskNode.continueWith(continuation: (Task.BasicTaskActions) -> Task.TaskStepResult<*>) {
        // TODO: Clean up lastTaskStepNode.time to just be time if we commit to using time = branch only
        // Expand this task node
        var lastTaskStepNode: TaskNode = this
        // Go find the root task, to look up root merge opportunities...
        // TODO: Key off of something easier to find...
        val rootTaskNode = generateSequence(this) { it.prior }.first { it is RootTaskNode }
        val rootTask = (rootTaskNode as RootTaskNode).task
        // We can merge only if this action is happening at the same time as the merge opportunity.
        val mergeOpportunity: RootTaskNode? = rootMergeOpportunities[rootTask]?.takeIf { it.time == time }
        // If we might merge, link the merge opportunity to the currently-running branch,
        // so we can detect if changes made by this branch trigger a re-run of the branch we're about to merge to.
        mergeOpportunity?.let {
            lastTaskStepNode.next = it
            it.prior = lastTaskStepNode
        }
        val basicTaskActions = object : Task.BasicTaskActions {
            override fun <V> read(cell: Cell<V>): V {
                // Look up the cell node
                val cellNode = getCellNode(cell, lastTaskStepNode.time)
                // Record this read in the graph
                lastTaskStepNode = ReadNode(
                    lastTaskStepNode.time,
                    lastTaskStepNode,
                    cellNode,
                    next = mergeOpportunity,
                ).also {
                    lastTaskStepNode.next = it
                    mergeOpportunity?.prior = it
                    cellNode.reads += it
                    taskNodes += it
                }
                // Return the value
                return cellNode.value
            }

            override fun <V> emit(cell: Cell<V>, effect: Effect<V>) {
                // Look up the cell node we're writing to
                val writeTime = lastTaskStepNode.time
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
                    cell,
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
                        // TODO: Should this be a full revoke, or should we somehow permit re-merging?
                        fullyRevokeTask(it)
                    }
                    if (awaiter.next == null) {
                        // The awaiter is active (it had no next node, or the next node was revoked).
                        // Build a new await node and schedule it for the next batch, on the awaiter's branch
                        awaiter.next = AwaitNode(
                            writeNode.time.nextBatch().copy(branch = awaiter.time.branch),
                            awaiter,
                            awaiter.condition,
                            continuation = awaiter.continuation,
                        ).also {
                            frontier += CheckCondition(it)
                            taskNodes += it
                        }
                    }
                }

                // Having constructed the cell's write node, now construct the next step node for the task.
                lastTaskStepNode = WriteNode(
                    writeTime,
                    lastTaskStepNode,
                    writeNode,
                    next = mergeOpportunity,
                ).also {
                    // Also add the edges from the prior task step and from the cell write node to this.
                    lastTaskStepNode.next = it
                    mergeOpportunity?.prior = it
                    writeNode.writer = it
                    taskNodes += it
                }
            }

            override fun <V> report(value: V) {
                // Record this report in the task graph and issue it to the reportHandler
                lastTaskStepNode = ReportNode(
                    lastTaskStepNode.time,
                    lastTaskStepNode,
                    value,
                    next = mergeOpportunity,
                ).also {
                    lastTaskStepNode.next = it
                    mergeOpportunity?.prior = it
                    taskNodes += it
                    reportHandler.report(it)
                }
            }
        }
        when (val result = continuation(basicTaskActions)) {
            is Task.TaskStepResult.Await<*> -> {
                // Create an await node and add it to the frontier, to be checked in the next batch.
                lastTaskStepNode = AwaitNode(
                    lastTaskStepNode.time.nextBatch(),
                    lastTaskStepNode,
                    result.condition,
                    continuation = result.continuation,
                    next = mergeOpportunity,
                ).also {
                    // Having constructed the node, link it to chain of task step nodes
                    lastTaskStepNode.next = it
                    mergeOpportunity?.prior = it
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
                    // Throughout this ContinueTask action, we've kept mergeOpportunity connected to lastTaskStepNode.
                    // Just leave those connections intact, and the graph should be correct.
                } else {
                    // Add a new root task, from which we can restart the next task at any time.
                    lastTaskStepNode = RootTaskNode(
                        // Restarting does not yield to the engine, it's the next step of this task.
                        lastTaskStepNode.time,
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
                    lastTaskStepNode.time.nextBatch(),
                    lastTaskStepNode,
                    RootTaskNode(
                        lastTaskStepNode.time.nextBatch().branchFor(result.child),
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
                    frontier += ContinueTask(it)
                    taskNodes += it
                }
            }
        }
    }

    /**
     * Revoke all task nodes starting from [task].
     * This includes repetitions and children spawned by this task.
     */
    private fun fullyRevokeTask(task: TaskNode) {
        var next: TaskNode? = task
        while (next != null) {
            next = revokeSingleTask(task)
        }
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
            is ReportNode<*> -> reportHandler.revoke(task)
            is WriteNode -> revokeCell(task.cell)
            is RootTaskNode -> { /* Nothing to do */ }
            is AwaitNode -> task.reads.forEach { it.awaiters -= task }
            // The child of a revoked parent can never be merged.
            // If the root of this parent wasn't merged, it's because this task and any candidate for merging
            // couldn't be proven to be in the same state. Since children (may) inherit state from their parents,
            // any child spawned from possibly-different parents may itself be possibly-different, so cannot be merged.
            is SpawnNode -> fullyRevokeTask(task.child)
            is StepBeginNode -> { /* Nothing to do */ }
        }
        // Continue revoking the rest of this task
        return task.next?.let { it as? RootTaskNode ?: revokeSingleTask(it) }
    }

    private fun <T> revokeCell(cell: CellNode<T>) {
        when (cell) {
            is CellWriteNode -> {
                checkNotNull(cell.prior) {
                    "Cannot revoke initial cell write node!"
                }
                for (next in cell.next) {
                    when (next) {
                        is CellWriteNode -> {
                            next.prior = cell.prior
                            frontier += CheckCell(next)
                        }
                        is CellStepNode -> {
                            next.prior = cell.prior!!
                            frontier += CheckCell(next)
                        }
                        is CellMergeNode -> {
                            next.prior.remove(cell)
                            when (val prior = cell.prior!!) {
                                is CellWriteNode -> {
                                    // We're shortening this branch without removing it.
                                    // Link the write before cell to next, then schedule next to be re-checked.
                                    next.prior += prior
                                    frontier += CheckCell(next)
                                }
                                is CellStepNode, is CellMergeNode -> {
                                    // We've completely removed a branch.
                                    if (next.prior.size <= 1) {
                                        // If we removed the second-to-last branch, revoke the merge itself too.
                                        revokeCell(next)
                                    }
                                }
                            }
                        }
                    }
                }
                // A CellWriteNode is revoked only when the writer is revoked. Nothing to do for cell.writer
            }
            is CellStepNode -> TODO("Revoking cell step nodes")
            is CellMergeNode -> {
                val prior = checkNotNull(cell.prior.singleOrNull()) {
                    "Internal error! Only single-branch merge nodes can be revoked."
                }
                for (next in cell.next) {
                    when (next) {
                        is CellWriteNode -> {
                            next.prior = prior
                            frontier += CheckCell(next)
                        }
                        is CellStepNode -> {
                            next.prior = prior
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
        cell.reads.forEach {
            frontier += RerunTask(it)
        }
        cell.awaiters.forEach {
            frontier += RerunTask(it)
        }
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
        if ((t as SimulationTime).isConcurrentWith(time)) {
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

    private fun SimulationTime.nextBatch() = copy(batch = batch + 1)

    private fun SimulationTime.batchStart() = when (this) {
        is SimulationTime -> copy(branch = 0)
    }

    private fun SimulationTime.cellSteppingBatch() = when (this) {
        // Conceptually, cells are stepped in a special "batch", before any tasks are run
        is SimulationTime -> copy(batch = -1, branch = 0)
    }

    infix fun SimulationTime.isConcurrentWith(other: SimulationTime): Boolean =
        instant == other.instant && batch == other.batch && branch != other.branch

    infix fun SimulationTime.isCausallyBefore(other: SimulationTime): Boolean =
        this < other && !(this isConcurrentWith other)

    infix fun SimulationTime.isCausallyAfter(other: SimulationTime): Boolean =
        this > other && !(this isConcurrentWith other)

    private sealed interface FrontierAction {
        val node: SGNode
        val time: SimulationTime get() = node.time

        data class StartTask(override val node: RootTaskNode) : FrontierAction
        data class ContinueTask(override val node: YieldingStepNode) : FrontierAction
        data class RerunTask(override val node: TaskNode) : FrontierAction
        data class CheckCell(override val node: CellNode<*>) : FrontierAction
        data class CheckCondition(override val node: AwaitNode) : FrontierAction
        data class RevokeMergeOpportunity(override val node: RootTaskNode) : FrontierAction
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
        fun SimulationTime.rank() = when (this) { is SimulationTime -> copy(branch = 0) }
        // The "file" is the horizontal position within the rank
        fun SimulationTime.file() = when (this) { is SimulationTime -> branch }
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
                            is ReportNode<*> -> "Report ${node.content}"
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

/**
 * A generalization of [ReportHandler] which allows the simulator to revoke a report it issued previously,
 * in response to incremental changes to the simulation.
 */
interface IncrementalReportHandler {
    fun report(report: ReportNode<*>)
    fun revoke(report: ReportNode<*>)
}
