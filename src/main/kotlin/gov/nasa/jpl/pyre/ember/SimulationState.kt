package gov.nasa.jpl.pyre.ember

import gov.nasa.jpl.pyre.ember.Task.TaskStepResult.*
import gov.nasa.jpl.pyre.ember.CellSet.CellHandle
import gov.nasa.jpl.pyre.ember.Condition.ConditionResult
import gov.nasa.jpl.pyre.ember.Task.PureStepResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.util.Comparator.comparing
import java.util.PriorityQueue

class SimulationState(private val reportHandler: (JsonElement) -> Unit) {
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

    /**
     * These are the actions allowed during "initialization", before the simulation starts running.
     * Note that this is the only time we're allowed to allocate cells.
     */
    interface SimulationInitContext {
        fun <T: Any, E> allocate(cell: Cell<T, E>): CellHandle<T, E>
        fun <T> spawn(name: String, step: () -> PureStepResult<T>)
    }

    fun initContext() = object : SimulationInitContext {
        override fun <T: Any, E> allocate(cell: Cell<T, E>) = cells.allocate(cell)
        override fun <T> spawn(name: String, step: () -> PureStepResult<T>) = addTask(name, step)
    }

    fun time() = time

    fun <T> addTask(name: String, step: PureTaskStep<T>, time: Duration = time()) {
        addTask(Task.of(name, step), time)
    }

    private fun addTask(task: Task<*>, time: Duration) {
        tasks += TaskEntry(time, task)
    }

    fun stepTo(endTime: Duration) {
        val batchTime = tasks.peek()?.time
        if (batchTime != null && batchTime == time && batchTime <= endTime) {
            // Collect a batch of tasks, prior to running any of those tasks,
            // since running the tasks may add new tasks that should logically come after this batch.
            val taskBatch = mutableListOf<Task<*>>()
            while (tasks.peek()?.time == batchTime) taskBatch += tasks.remove().task
            runTaskBatch(taskBatch)
        } else {
            val stepTime = minOf(batchTime ?: endTime, endTime)
            if (time == stepTime) {
                InternalLogger.log("Simulation already at $stepTime with no tasks to do.")
            } else {
                InternalLogger.log("Step time to $stepTime")
                require(stepTime >= time) { "Requested step time $stepTime is earlier than current time $time" }
                cells.stepBy(stepTime - time)
                time = stepTime
            }
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
            val (cellsRead, result) = evaluateCondition(stepResult.condition(), cellSet)

            // Schedule listeners to re-evaluate condition
            for (cell in cellsRead) {
                cellListeners.getOrPut(cell) { mutableSetOf() } += rewaitTask
            }
            listeningTasks[rewaitTask] = cellsRead

            when (result) {
                is Condition.SatisfiedAt -> {
                    // Add conditional task to resume the awaiting task when the condition is satisfied
                    val continuationTask = object : Task<T> by stepResult.continuation {
                        override fun runStep(): Task.TaskStepResult<T> {
                            // Remove all listeners before continuing so we don't re-trigger the condition
                            reset(rewaitTask)
                            return stepResult.continuation.runStep()
                        }
                    }
                    val continuationEntry = TaskEntry(time + result.time, continuationTask)
                    tasks += continuationEntry
                    conditionalTasks[rewaitTask] = continuationEntry
                }
                is Condition.UnsatisfiedUntil -> {
                    if (result.time != null) {
                        // Schedule the rewait task to re-evaluate the condition once this unsatisfied result expires
                        val rewaitEntry = TaskEntry(time + result.time, rewaitTask)
                        tasks += rewaitEntry
                        // Also register this scheduled run to be canceled, if an effect re-evaluates the condition sooner
                        conditionalTasks[rewaitTask] = rewaitEntry
                    }
                }
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
            is Complete -> Unit // Nothing to do
            is Delay -> addTask(stepResult.continuation, time + stepResult.time)
            is Await -> runTaskAwait(stepResult)
            is Emit<*, *, *> -> runTaskEmit(stepResult)
            is Read<*, *, *> -> runTaskRead(stepResult)
            is Report -> {
                reportHandler(stepResult.value)
                runTask(stepResult.continuation, cellSet)
            }
            is Spawn<*, *> -> {
                addTask(stepResult.child, time)
                runTask(stepResult.continuation, cellSet)
            }
        }
        InternalLogger.endBlock()
    }

    private fun evaluateCondition(condition: Condition, cellSet: CellSet): Pair<Set<CellHandle<*, *>>, ConditionResult> {
        InternalLogger.log("Eval condition $condition")
        fun <V> evaluateRead(read: Condition.Read<V>) =
            with (evaluateCondition(read.continuation(cellSet[read.cell].value), cellSet)) { copy(first = first + read.cell) }

        return when (condition) {
            is Condition.SatisfiedAt, is Condition.UnsatisfiedUntil -> Pair(emptySet(), condition)
            is Condition.Read<*> -> evaluateRead(condition)
        }
    }

    // REVIEW: Not completely sure this save/restore procedure will work.
    // This needs to be tested fairly extensively...

    fun save(finconCollector: FinconCollector) {
        with (finconCollector.withPrefix("simulation")) {
            report("time", value=Json.encodeToJsonElement(time))
        }
        cells.save(finconCollector.withPrefix("cells"))
        val taskCollector = finconCollector.withPrefix("tasks")
        val taskTimeCollector = taskCollector.withSuffix("time")
        // Conditional tasks are generated by awaiting tasks. We shouldn't restore them directly.
        // Instead, we should restore the awaiting task, and it will generate the conditional task when it first runs.
        val excludedTasks: Set<Task<*>> = conditionalTasks.mapTo(mutableSetOf()) { it.value.task }
        tasks.forEach {
            it.task.takeUnless(excludedTasks::contains)?.save(taskCollector)
            taskTimeCollector.report(it.task.id.rootId.conditionKeys(), Json.encodeToJsonElement(it.time))
        }
        // Save all the awaiting tasks
        listeningTasks.keys.forEach {
            it.save(taskCollector)
            taskTimeCollector.report(it.id.rootId.conditionKeys(), Json.encodeToJsonElement(time))
        }
    }

    fun restore(inconProvider: InconProvider) {
        with (inconProvider.withPrefix("simulation")) {
            time = Json.decodeFromJsonElement(requireNotNull(get("time")))
        }
        cells.restore(inconProvider.withPrefix("cells"))
        val taskProvider = inconProvider.withPrefix("tasks")
        val taskTimeProvider = taskProvider.withSuffix("time")
        val rootTasks = tasks.toList()
        tasks.clear()
        for (rootTask in rootTasks) {
            for (restoredTask in rootTask.task.restore(taskProvider)) {
                val restoredTime: Duration = Json.decodeFromJsonElement(
                    requireNotNull(taskTimeProvider.get(restoredTask.id.rootId.conditionKeys())))
                tasks += TaskEntry(restoredTime, restoredTask)
            }
        }
    }
}
