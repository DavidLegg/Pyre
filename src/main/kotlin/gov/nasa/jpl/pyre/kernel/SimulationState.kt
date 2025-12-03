package gov.nasa.jpl.pyre.kernel

import gov.nasa.jpl.pyre.kernel.Task.TaskStepResult.*
import gov.nasa.jpl.pyre.kernel.CellSet.Cell
import gov.nasa.jpl.pyre.kernel.Duration.Companion.ZERO
import gov.nasa.jpl.pyre.kernel.FinconCollectingContext.Companion.report
import gov.nasa.jpl.pyre.kernel.FinconCollector.Companion.within
import gov.nasa.jpl.pyre.kernel.InconProvider.Companion.within
import gov.nasa.jpl.pyre.kernel.InconProvidingContext.Companion.provide
import gov.nasa.jpl.pyre.kernel.NameOperations.asSequence
import kotlinx.serialization.SerializationException
import java.util.Comparator.comparing
import java.util.PriorityQueue
import kotlin.reflect.KType

typealias ReportHandler = (Any?, KType) -> Unit

class SimulationState(private val reportHandler: ReportHandler) {
    // Use a class, not a data class, for performance.
    // Adding and removing entries from the task queue is faster when the entry uses object identity
    // rather than deep equality.
    private class TaskEntry(val time: Duration, val task: Task<*>) {
        operator fun component1() = time
        operator fun component2() = task
        override fun toString(): String = "$task @ $time"
    }

    private var time: Duration = Duration(0)
    private var cells: TrunkCellSet = TrunkCellSet()
    private val rootTasks: MutableList<TaskEntry> = mutableListOf()
    private val tasks: PriorityQueue<TaskEntry> = PriorityQueue(comparing(TaskEntry::time))
    private val cellListeners: MutableMap<Cell<*>, MutableSet<AwaitingTask<*>>> = mutableMapOf()
    private val listeningTasks: MutableMap<AwaitingTask<*>, Set<Cell<*>>> = mutableMapOf()
    private val modifiedCells: MutableSet<Cell<*>> = mutableSetOf()

    private class AwaitingTask<T>(val await: Await<T>) {
        var scheduledTask: TaskEntry? = null
        override fun toString(): String = "${await.rewait} -- $await"
    }
    private val awaitingTasks: MutableSet<AwaitingTask<*>> = mutableSetOf()

    fun initScope() = object : BasicInitScope {
        override fun <T : Any> allocate(
            name: Name,
            value: T,
            valueType: KType,
            stepBy: (T, Duration) -> T,
            mergeConcurrentEffects: (Effect<T>, Effect<T>) -> Effect<T>
        ): Cell<T> = cells.allocate(name, value, valueType, stepBy, mergeConcurrentEffects)
        override fun <T> spawn(name: Name, step: PureTaskStep<T>) = addTask(name, step)
        override fun <T> read(cell: Cell<T>): T = cells[cell]
    }

    fun time() = time

    fun <T> addTask(name: Name, step: PureTaskStep<T>, time: Duration = time()) {
        val task = Task.of(name, step)
        rootTasks += TaskEntry(time, task)
        addTask(task, time)
    }

    /**
     * Add a task which won't be saved if a fincon is taken.
     *
     * The caller assumes all responsibility for ensuring that
     * 1) A fincon without this task will nevertheless restore the simulation adequately, or
     * 2) This task will run to completion before a fincon is taken.
     */
    fun <T> addEphemeralTask(name: Name, step: PureTaskStep<T>, time: Duration = time()) {
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
            val taskBatch = mutableSetOf<Task<*>>()
            while (tasks.peek()?.time == batchTime) taskBatch += tasks.remove().task
            runTaskBatch(taskBatch)
        } else {
            val stepTime = minOf(batchTime ?: endTime, endTime)
            if (time != stepTime) {
                require(stepTime >= time) {
                    "Requested step time $stepTime is earlier than current time $time"
                }
                cells.stepBy(stepTime - time)
                time = stepTime
            }
        }
    }

    private fun runTaskBatch(tasks: MutableSet<Task<*>>) {
        // For each task, split the cells so that task operates on an isolated state.
        val cellSetBranches = tasks.map { task ->
            cells.split().also { runTask(task, it) }
        }
        // Finally join those branched states back into the trunk state.
        cells.join(cellSetBranches)

        // Now, collect all the reactions to effects made by this batch.
        // Since awaitingTasks is a set, it de-duplicates reactions automatically.
        modifiedCells.flatMapTo(awaitingTasks) { cellListeners[it] ?: emptySet() }
        modifiedCells.clear()

        // Now, evaluate all the awaiting tasks.
        // Running conditions all at once at the end of the batch avoids double-evaluation when a task
        // affects a cell and awaits a condition on that cell, as opposed to evaluating the condition when running the task.
        for (await in awaitingTasks) {
            evaluateAwaitingTask(await)
        }
        awaitingTasks.clear()
    }

    private fun <T> evaluateAwaitingTask(awaitingTask: AwaitingTask<T>) {
        // Reset any listeners from any prior evaluation of this task
        resetListeners(awaitingTask)

        // Evaluate the condition, recording the cells we read along the way
        val cellsRead: MutableSet<Cell<*>> = mutableSetOf()
        val result = awaitingTask.await.condition(object : ReadActions {
            override fun <V> read(cell: Cell<V>): V {
                cellsRead += cell
                return cells[cell]
            }
        })

        when (result) {
            is SatisfiedAt -> if (result.time == ZERO) {
                // If a condition is satisfied immediately, don't bother adding listeners or listener cleanup
                tasks += TaskEntry(time, awaitingTask.await.continuation)
                // Since we're discarding the awaiting task immediately, no need to set scheduledTask either.
            } else {
                // Otherwise, schedule the task with listeners to cancel and re-eval as needed
                val entry = TaskEntry(time + result.time, conditionalTask(awaitingTask, awaitingTask.await.continuation))
                tasks += entry
                // Also register this scheduled run to be canceled, if an effect re-evaluates the condition sooner
                awaitingTask.scheduledTask = entry
                setListeners(awaitingTask, cellsRead)
            }
            is UnsatisfiedUntil -> {
                // Set listeners to re-evaluate the condition if any read cell changes
                setListeners(awaitingTask, cellsRead)
                if (result.time != null) {
                    // Schedule the rewait task to re-evaluate the condition once this unsatisfied result expires
                    val entry = TaskEntry(time + result.time, conditionalTask(awaitingTask, awaitingTask.await.rewait))
                    tasks += entry
                    // Also register this scheduled run to be canceled, if an effect re-evaluates the condition sooner
                    awaitingTask.scheduledTask = entry
                }
            }
        }
    }

    private fun <T> conditionalTask(awaitingTask: AwaitingTask<T>, task: Task<T>) = object : Task<T> by task {
        override fun runStep(actions: Task.BasicTaskActions): Task.TaskStepResult<T> {
            // Remove all listeners before continuing so we don't re-trigger the condition
            resetListeners(awaitingTask)
            return task.runStep(actions)
        }
    }

    private fun <T> setListeners(awaitingTask: AwaitingTask<T>, cellsRead: Set<Cell<*>>) {
        // Schedule listeners to re-evaluate condition if cells change
        for (cell in cellsRead) {
            cellListeners.getOrPut(cell) { mutableSetOf() } += awaitingTask
        }
        listeningTasks[awaitingTask] = cellsRead
    }

    private fun <T> resetListeners(awaitingTask: AwaitingTask<T>) {
        // Reset the cells we're listening to
        listeningTasks.remove(awaitingTask)?.forEach { cellListeners[it]?.remove(awaitingTask) }
        // Remove the scheduled rewait or continuation, if it's there.
        awaitingTask.scheduledTask?.let(tasks::remove)
    }

    private fun <T> runTask(task: Task<T>, cellSet: CellSet) {
        // "trampoline" through the continuations of task
        // That is, iterate on nextTask - each iteration runs one step of task, then updates nextTask with the next step.
        // When there are no more steps to run now, break out of the loop and quit.
        // While a little less natural than just recurring on runTask, this avoids stack overflow on "intensive tasks",
        // tasks which run many steps without yielding.
        var nextTask = task

        val actions = object : Task.BasicTaskActions {
            override fun <V> read(cell: Cell<V>): V = cellSet[cell]
            override fun <V> emit(cell: Cell<V>, effect: Effect<V>) {
                cellSet.emit(cell, effect)
                // We mark the cell as modified, instead of directly adding listeners, to keep the simulation deterministic.
                // This is because a task T may await this cell in parallel with this.
                // If T is ahead of this in the batch, adding listeners would add it;
                // but if T were behind this in the batch, adding listeners would miss it.
                // By deferring the resolution from cell to listener to the end of the batch,
                // T will always be added, which is conservative and deterministic.
                modifiedCells += cell
            }
            override fun <V> report(value: V, type: KType) = reportHandler(value, type)
        }

        while (true) {
            val stepResult = try {
                nextTask.runStep(actions)
            } catch (e: Throwable) {
                System.err.println("Error while running ${nextTask.id}: $e")
                throw e
            }
            when (stepResult) {
                is Complete -> break // Nothing to do
                is Await -> {
                    awaitingTasks += AwaitingTask(stepResult)
                    break
                }
                is Spawn<*, T> -> {
                    addTask(stepResult.child, time)
                    nextTask = stepResult.continuation
                }
                is NoOp -> {
                    nextTask = stepResult.continuation
                }
            }
        }
    }

    fun save(finconCollector: FinconCollector) {
        with (finconCollector.within("simulation")) {
            within("time").report(time)
        }
        cells.save(finconCollector.within("cells"))
        val taskCollector = finconCollector.within("tasks")
        // Tasks which are the scheduled re-evaluation or continuation of a condition should not be directly restored.
        // Instead, we should restore the listening task, and it will generate the appropriate task when it first runs.
        val excludedTasks: Set<TaskEntry> = listeningTasks.keys.mapNotNullTo(mutableSetOf()) { it.scheduledTask }

        val tasksToSave: List<TaskEntry> =
            tasks.filter { it !in excludedTasks } + listeningTasks.keys.map { TaskEntry(time, it.await.rewait) }

        tasksToSave.forEach { (time, task) ->
            taskCollector.within(task.id.name.asSequence())
                .also(task::save)
                .within("time")
                .report<Duration>(time)
        }
        val tasksByRootTaskName = tasksToSave.groupBy { it.task.id.rootTaskName }
        // Report the running children for every root task.
        // If a root task and all its children are complete, report an empty list of running children.
        // This distinguishes completed root tasks from new root tasks, where a root task is added because the model changes.
        for (rootTask in rootTasks) {
            val rootTaskName = rootTask.task.id.rootTaskName
            val tasks = tasksByRootTaskName.getOrDefault(rootTaskName, listOf())
            taskCollector.within(rootTaskName.asSequence() + "children")
                .report(tasks.map { it.task.id.name.asSequence().toList() })
        }
    }

    fun restore(inconProvider: InconProvider) {
        with (inconProvider.within("simulation")) {
            time = requireNotNull(within("time").provide())
        }
        cells.restore(inconProvider.within("cells"))
        val taskProvider = inconProvider.within("tasks")
        tasks.clear()
        for (rootTask in rootTasks) {
            val idsToRestore = taskProvider.within(rootTask.task.id.name.asSequence() + "children").provide<List<List<String>>>()
            if (idsToRestore == null) {
                tasks += TaskEntry(time, rootTask.task)
            } else {
                try {
                    // For any child reported in the fincon, call restore using that child's id (encoded as the condition keys)
                    // to get the state history which spawned and later ran that child.
                    tasks += idsToRestore.map {
                        // Since that child is reported as needing to be restored, throw an error if there's no incon data for it
                        requireNotNull(restoreTask(rootTask.task, taskProvider.within(it.asSequence())))
                    }
                } catch (_: SerializationException) {
                    tasks += TaskEntry(time, rootTask.task)
                }
            }
        }
    }

    private fun restoreTask(rootTask: Task<*>, inconProvider: InconProvider): TaskEntry? =
        rootTask.restore(inconProvider)?.let { restoredTask ->
            val restoredTime = requireNotNull(inconProvider.within("time").provide<Duration>())
            TaskEntry(restoredTime, restoredTask)
        }

    /**
     * Dump the state of the simulation to standard out.
     * This is triggered when the simulation is unhealthy, so normal outputs may not happen.
     * The exact format of this dump is subject to change over time.
     */
    fun dump() {
        println("${this::class.simpleName} dump:")
        println("  Simulation time: $time")
        println("  Active tasks:")
        tasks.sortedBy { it.time }.forEach { (time, task) -> println("    $time - ${task.id}") }
        println("  Waiting tasks:")
        val waitingTasks = awaitingTasks.toMutableSet()
        waitingTasks += listeningTasks.keys
        waitingTasks.forEach { println("    ${it.await.rewait.id}") }
    }
}
