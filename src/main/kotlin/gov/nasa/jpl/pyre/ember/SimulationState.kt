package gov.nasa.jpl.pyre.ember

import gov.nasa.jpl.pyre.ember.Task.TaskStepResult.*
import gov.nasa.jpl.pyre.ember.CellSet.CellHandle
import java.util.Comparator.comparing
import java.util.PriorityQueue

class SimulationState(private val reportHandler: (JsonValue) -> Unit) {
    private data class TaskEntry(val time: Duration, val task: Task<*>)

    private var time: Duration = Duration(0)
    private var cells: CellSet = CellSet()
    // TODO: For performance, it may be simpler to maintain a priority multimap keyed on task time,
    //  rather than collecting a batch of tasks when running them...
    //  Consider replacing tasks with a more efficient data structure
    private val tasks: PriorityQueue<TaskEntry> = PriorityQueue(comparing(TaskEntry::time))
    // TODO: For the sake of restoring awaiting tasks, we may need to save pseudo-tasks,
    //  which defer back to the original task on everything except runStep, instead of the Await step itself.
    private val cellListeners: MutableMap<CellHandle<*, *>, MutableSet<Task<*>>> = mutableMapOf()
    private val listeningTasks: MutableMap<Task<*>, Set<CellHandle<*, *>>> = mutableMapOf()
    private val conditionalTasks: MutableMap<Task<*>, TaskEntry> = mutableMapOf()
    private val modifiedCells: MutableSet<CellHandle<*, *>> = mutableSetOf()
    private val completedTasks: MutableSet<Task<*>> = mutableSetOf()

    /**
     * These are the actions allowed during "initialization", before the simulation starts running.
     * Note that this is the only time we're allowed to allocate cells.
     */
    interface SimulationInitializer {
        fun <T: Any, E> allocate(cell: Cell<T, E>): CellHandle<T, E>
        fun spawn(task: Task<*>)
    }

    fun initializer() = object : SimulationInitializer {
        override fun <T: Any, E> allocate(cell: Cell<T, E>) = cells.allocate(cell)
        override fun spawn(task: Task<*>) = addTask(task)
    }

    fun time() = time

    fun addTask(task: Task<*>, time: Duration = time()) {
        tasks += TaskEntry(time, task)
    }

    fun stepTo(endTime: Duration) {
        val batchTime = tasks.peek()?.time
        if (batchTime != null && batchTime == time) {
            // Collect a batch of tasks, prior to running any of those tasks,
            // since running the tasks may add new tasks that should logically come after this batch.
            val taskBatch = mutableListOf<Task<*>>()
            while (tasks.peek()?.time == batchTime) taskBatch += tasks.remove().task
            runTaskBatch(taskBatch)
        } else {
            val stepTime = batchTime ?: endTime
            InternalLogger.log("Step time to $stepTime")
            require(stepTime >= time) { "Requested step time $stepTime is earlier than current time $time" }
            cells.stepBy(stepTime - time)
            time = stepTime
        }
    }

    private tailrec fun runTaskBatch(tasks: Collection<Task<*>>) {
        val reactions = mutableSetOf<Task<*>>()
        InternalLogger.block("Start batch", "End batch") {
            // For each task, split the cells so that task operates on an isolated state.
            val cellSetBranches = tasks.map { task ->
                cells.split().also { runTask(task, it) }
            }
            // Finally join those branched states back into the trunk state.
            cells = CellSet.join(cellSetBranches)
            // Now, collect all the reactions to effects made by this batch.
            // Use a set to de-duplicate reaction tasks automatically.
            modifiedCells.flatMapTo(reactions) { cellListeners[it] ?: emptySet() }
            modifiedCells.clear()
            reactions.forEach { InternalLogger.log("Triggered reaction ${it.id}") }
        }
        // Run another batch automatically, without going back to the outer simulation loop
        // This is because reactions are not always "proper" tasks; we may be unable to save a fincon with these in the queue.
        // Instead, we need to run reactions until they resolve back to proper tasks, at which point we can yield back to the driver.
        // Giving reactions priority like this also means that awaiting tasks have no "blindspot" between the condition evaluation and their reaction.
        // Tasks which await a condition "see" exactly the state which triggered the condition, instead of a tick later.
        if (reactions.isNotEmpty()) runTaskBatch(reactions)
    }

    private fun <T> runTask(task: Task<T>, cellSet: CellSet) {
        fun <V, E, T> runTaskRead(stepResult: Read<V, E, T>) =
            runTask(stepResult.continuation(cellSet[stepResult.cell].value), cellSet)

        fun runTaskAwait(stepResult: Await<T>) {
            fun reset(rewaitTask: Task<T>) {
                // Reset the cells we're listening to
                listeningTasks.remove(rewaitTask)?.forEach { cellListeners[it]?.remove(rewaitTask) }
                // TODO: for long-term performance, we may want to use a proper multi-map instead of map-to-sets
                //   That way we won't accrue empty sets in the values over time
                // Remove the task evaluation if it's there
                conditionalTasks.remove(rewaitTask)?.let { tasks.remove(it) }
            }

            // Build a short-circuiting task to ensure we don't re-run the part of the task which produced the Await,
            // we *just* reproduce that Await result
            val rewaitTask = object: Task<T> by task {
                override fun runStep(): Await<T> {
                    // Remove all listeners before re-evaluating the condition so we don't double-listen
                    reset(this)
                    return stepResult
                }
            }

            // Evaluate the condition
            val (cellsRead, readyTime) = evaluateCondition(stepResult.condition, cellSet)

            // Schedule listeners to re-evaluate condition
            for (cell in cellsRead) {
                cellListeners.getOrPut(cell) { mutableSetOf() } += rewaitTask
            }
            listeningTasks[rewaitTask] = cellsRead

            if (readyTime != null) {
                // Add conditional task to resume the awaiting task when the condition is satisfied
                val continuationTask = object : Task<T> by stepResult.continuation {
                    override fun runStep(): Task.TaskStepResult<T> {
                        // Remove all listeners before continuing so we don't re-trigger the condition
                        reset(rewaitTask)
                        return stepResult.continuation.runStep()
                    }
                }
                val continuationEntry = TaskEntry(time + readyTime, continuationTask)
                tasks += continuationEntry
                conditionalTasks[rewaitTask] = continuationEntry
            }
        }

        fun <V, E, T> runTaskEmit(step: Emit<V, E, T>) {
            // We mark the cell as modified, instead of directly adding listeners, to keep the simulation deterministic.
            // This is because a task T may await this cell in parallel with this.
            // If T is ahead of this in the batch, adding listeners would add it;
            // but if T were behind this in the batch, adding listeners would miss it.
            // By deferring the resolution from cell to listener to the end of the batch,
            // T will always be added, which is conservative and deterministic.
            cellSet.emit(step.cell, step.effect)
            modifiedCells += step.cell
            runTask(step.continuation, cellSet)
        }

        // TODO: Think about how to support looping / restarting tasks...
        //   These would restart from the beginning, as a way to limit incon size, rather than complete
        //   Maybe that can just be a special kind of Spawn step...
        //   A PureTaskStep called "Restart" becomes a TaskStepResult.Spawn that points back to the original Task
        InternalLogger.startBlock("Continue ${task.id}...")
        val stepResult = task.runStep()
        InternalLogger.log("... returns $stepResult")
        when (stepResult) {
            is Complete -> completedTasks += stepResult.continuation
            is Delay -> tasks += TaskEntry(time + stepResult.time, stepResult.continuation)
            is Await -> runTaskAwait(stepResult)
            is Emit<*, *, *> -> runTaskEmit(stepResult)
            is Read<*, *, *> -> runTaskRead(stepResult)
            is Report -> {
                reportHandler(stepResult.value)
                runTask(stepResult.continuation, cellSet)
            }
            is Spawn<*, *> -> {
                tasks += TaskEntry(time, stepResult.child)
                runTask(stepResult.continuation, cellSet)
            }
        }
        InternalLogger.endBlock()
    }

    private fun evaluateCondition(condition: Condition, cellSet: CellSet): Pair<Set<CellHandle<*, *>>, Duration?> {
        InternalLogger.log("Eval condition $condition")
        fun <V> evaluateRead(read: Condition.Read<V>) =
            with (evaluateCondition(read.continuation(cellSet[read.cell].value), cellSet)) { copy(first = first + read.cell) }

        return when (condition) {
            is Condition.Complete -> Pair(emptySet(), condition.time)
            is Condition.Read<*> -> evaluateRead(condition)
        }
    }

    // REVIEW: Not completely sure this save/restore procedure will work.
    // This needs to be tested fairly extensively...

    fun save(finconCollector: FinconCollector) {
        with (finconCollector.withPrefix("simulation")) {
            report("time", value= Duration.serializer().serialize(time))
        }
        cells.save(finconCollector.withPrefix("cells"))
        val taskCollector = finconCollector.withPrefix("tasks")
        val taskStateCollector = taskCollector.withSuffix("state")
        val taskTimeCollector = taskCollector.withSuffix("time")
        // Conditional tasks are generated by awaiting tasks. We shouldn't restore them directly.
        // Instead, we should restore the awaiting task, and it will generate the conditional task when it first runs.
        val excludedTasks: Set<Task<*>> = conditionalTasks.mapTo(mutableSetOf()) { it.value.task }
        tasks.forEach {
            it.task.takeUnless(excludedTasks::contains)?.save(taskStateCollector)
            taskTimeCollector.report(it.task.id.rootId.conditionKeys(), Duration.serializer().serialize(it.time))
        }
        // Save all the awaiting tasks
        listeningTasks.keys.forEach { task ->
            task.save(taskStateCollector)
            taskTimeCollector.report(task.id.rootId.conditionKeys(), Duration.serializer().serialize(time))
        }
        // Save the completed tasks as well, such that they won't re-run when we restore
        completedTasks.forEach {
            it.save(taskStateCollector)
        }
    }

    fun restore(inconProvider: InconProvider) {
        with (inconProvider.withPrefix("simulation")) {
            time = Duration.serializer().deserialize(requireNotNull(get("time"))).getOrThrow()
        }
        cells.restore(inconProvider.withPrefix("cells"))
        val taskProvider = inconProvider.withPrefix("tasks")
        val taskStateProvider = taskProvider.withSuffix("state")
        val taskTimeProvider = taskProvider.withSuffix("time")
        val rootTasks = tasks.toList()
        tasks.clear()
        for (rootTask in rootTasks) {
            val restoredTasks = rootTask.task.restore(taskStateProvider)
            // If there was no incon data for this task, default back to the root task.
            if (restoredTasks == null) tasks.add(rootTask)
            // Otherwise, restore all the children of that task
            else for (restoredTask in restoredTasks) {
                if (restoredTask.isCompleted()) {
                    completedTasks += restoredTask
                } else {
                    val restoredTime = Duration.serializer()
                        .deserialize(requireNotNull(taskTimeProvider.get(restoredTask.id.rootId.conditionKeys())))
                        .getOrThrow()
                    tasks += TaskEntry(restoredTime, restoredTask)
                }
            }
        }
    }
}
