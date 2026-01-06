package gov.nasa.jpl.pyre.kernel

import gov.nasa.jpl.pyre.kernel.MutableSnapshot.Companion.report
import gov.nasa.jpl.pyre.kernel.MutableSnapshot.Companion.within
import gov.nasa.jpl.pyre.kernel.Task.TaskStepResult.*
import gov.nasa.jpl.pyre.kernel.Duration.Companion.ZERO
import gov.nasa.jpl.pyre.kernel.Snapshot.Companion.provide
import gov.nasa.jpl.pyre.kernel.Snapshot.Companion.within
import gov.nasa.jpl.pyre.kernel.NameOperations.asSequence
import gov.nasa.jpl.pyre.kernel.NameOperations.div
import gov.nasa.jpl.pyre.utilities.andThen
import kotlinx.serialization.SerializationException
import java.util.Comparator.comparing
import java.util.PriorityQueue
import kotlin.reflect.KType

typealias ReportHandler = (Any?) -> Unit

class SimulationState(private val reportHandler: ReportHandler, incon: Snapshot? = null) {
    // Use a class, not a data class, for performance.
    // Adding and removing entries from the task queue is faster when the entry uses object identity
    // rather than deep equality.
    private class TaskEntry(val time: Duration, val task: Task<*>) {
        operator fun component1() = time
        operator fun component2() = task
        override fun toString(): String = "$task @ $time"
    }

    private var time: Duration = if (incon != null) {
        requireNotNull(incon.provide("simulation", "time")) {
            "Incon must specify a ${Duration::class.simpleName} at simulation.time"
        }
    } else {
        ZERO
    }
    private var cells: MutableList<Cell<*>> = mutableListOf()
    private val rootTaskNames: MutableList<Name> = mutableListOf()
    private val tasks: PriorityQueue<TaskEntry> = PriorityQueue(comparing(TaskEntry::time))
    private val cellListeners: MutableMap<Cell<*>, MutableSet<AwaitingTask<*>>> = mutableMapOf()
    private val listeningTasks: MutableMap<AwaitingTask<*>, Set<Cell<*>>> = mutableMapOf()
    private val modifiedCells: MutableSet<Cell<*>> = mutableSetOf()

    private class AwaitingTask<T>(val await: Await<T>) {
        var scheduledTask: TaskEntry? = null
        override fun toString(): String = "${await.rewait} -- $await"
    }
    private val awaitingTasks: MutableSet<AwaitingTask<*>> = mutableSetOf()

    val initScope = object : BasicInitScope {
        private val cellIncon = incon?.within("cells")
        private val taskIncon = incon?.within("tasks")

        override fun <T : Any> allocate(
            name: Name,
            value: T,
            valueType: KType,
            stepBy: (T, Duration) -> T,
            mergeConcurrentEffects: (Effect<T>, Effect<T>) -> Effect<T>
        ): Cell<T> = CellImpl(
            name,
            cellIncon?.within(name.asSequence())?.provide(valueType) ?: value,
            valueType,
            stepBy,
            mergeConcurrentEffects,
        ).also { cells += it }

        override fun <T> spawn(name: Name, step: PureTaskStep<T>) {
            // "name" is a root task name. Remember it for saving a fincon later.
            rootTaskNames += name
            val rootTask = Task.of(name, step)

            // Look up which, if any, children of this task to restore from incon.
            val idsToRestore = taskIncon?.within(name.asSequence() + "children")?.provide<List<List<String>>>()
            if (idsToRestore == null) {
                tasks += TaskEntry(time, rootTask)
            } else {
                // To restore tasks, we replay their history provided by the incon.
                // In so doing, they may access any part of the model.
                // Since the model may not be fully constructed when this task is spawned, we need to defer
                // restoring this group of tasks until after the model is constructed.
                // We can do this by injecting a dummy task, run during the next sim step, to actually restore the tasks.
                // Since the simulation won't advance until initialization is complete, this guarantees the model is fully constructed.
                tasks += TaskEntry(time, Task.of(name / "restore") {
                    try {
                        // For any child reported in the incon, call restore using that child's id (encoded as the condition keys)
                        // to get the state history which spawned and later ran that child.
                        tasks += idsToRestore.map {
                            // Since that child is reported as needing to be restored, throw an error if there's no incon data for it
                            requireNotNull(restoreTask(rootTask, taskIncon.within(it.asSequence())))
                        }
                    } catch (_: SerializationException) {
                        tasks += TaskEntry(time, rootTask)
                    }
                    Task.PureStepResult.Complete(Unit)
                })
            }
        }

        override fun <T> report(value: T) = reportHandler(value)
    }

    private fun restoreTask(rootTask: Task<*>, inconProvider: Snapshot): TaskEntry? =
        rootTask.restore(inconProvider)?.let { restoredTask ->
            val restoredTime = requireNotNull(inconProvider.within("time").provide<Duration>())
            TaskEntry(restoredTime, restoredTask)
        }

    fun time() = time

    /**
     * Add a task which won't be saved if a fincon is taken.
     *
     * The caller assumes all responsibility for ensuring that
     * 1) A fincon without this task will nevertheless restore the simulation adequately, or
     * 2) This task will run to completion before a fincon is taken.
     */
    fun <T> addEphemeralTask(name: Name, step: PureTaskStep<T>, time: Duration = time()) {
        tasks += TaskEntry(time, Task.of(name, step))
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
                for (cell in cells) {
                    cell.stepBy(stepTime - time)
                }
                time = stepTime
            }
        }
    }

    private fun <T> Cell<T>.stepBy(time: Duration) {
        (this as CellImpl<T>).value = stepBy(value, time)
    }

    private fun runTaskBatch(tasks: MutableSet<Task<*>>) {
        for (task in tasks) {
            runTask(task)
        }

        fun <T> Cell<T>.applyTrunkNetEffect() {
            (this as CellImpl<T>).value = trunkNetEffect!!.value ?: trunkNetEffect!!.effect(value)
            trunkNetEffect = null
        }

        // Join the tasks' effects by applying the net effect on every modified cell.
        // Also collect all the reactions to effects made by this batch.
        // Since awaitingTasks is a set, it de-duplicates reactions automatically.
        modifiedCells.flatMapTo(awaitingTasks) {
            it.applyTrunkNetEffect()
            cellListeners[it] ?: emptySet()
        }
        modifiedCells.clear()

        // Now, evaluate all the awaiting tasks.
        // Running conditions all at once at the end of the batch avoids double-evaluation when a task
        // affects a cell and awaits a condition on that cell, as opposed to evaluating the condition when running the task.
        for (await in awaitingTasks) {
            evaluateAwaitingTask(await)
        }
        awaitingTasks.clear()
    }

    private fun <T> runTask(task: Task<T>) {
        val cellsModifiedByThisTask: MutableSet<Cell<*>> = mutableSetOf()

        val actions = object : Task.BasicTaskActions {
            override fun <V> read(cell: Cell<V>): V = (cell as CellImpl<V>).value

            override fun <V> emit(cell: Cell<V>, effect: Effect<V>) {
                // Store the trunk value if this is the first write to this cell
                (cell as CellImpl<V>).trunkValue = cell.trunkValue ?: cell.value
                // Update the value by directly applying the effect
                cell.value = effect(cell.value)
                // Record the new net effect of this branch, composing with prior effects if present
                cell.branchNetEffect = cell.branchNetEffect?.andThen(effect) ?: effect
                // Record that we modified this cell, so we can revert it back to trunk state later
                cellsModifiedByThisTask += cell
                // We mark the cell as modified, instead of directly adding listeners, to keep the simulation deterministic.
                // This is because a task T may await this cell in parallel with this.
                // If T is ahead of this in the batch, adding listeners would add it;
                // but if T were behind this in the batch, adding listeners would miss it.
                // By deferring the resolution from cell to listener to the end of the batch,
                // T will always be added, which is conservative and deterministic.
                modifiedCells += cell
            }

            override fun <V> report(value: V) = reportHandler(value)
        }

        // "trampoline" through the continuations of task
        // That is, iterate on nextTask - each iteration runs one step of task, then updates nextTask with the next step.
        // When there are no more steps to run now, break out of the loop and quit.
        // While a little less natural than just recurring on runTask, this avoids stack overflow on "intensive tasks",
        // tasks which run many steps without yielding.
        var nextTask = task

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
                    tasks += TaskEntry(time, stepResult.child)
                    nextTask = stepResult.continuation
                }
                is NoOp -> {
                    nextTask = stepResult.continuation
                }
            }
        }

        fun <T> Cell<T>.revertBranch() {
            // Merge the branch net effect into the trunk net effect:
            (this as CellImpl<T>).trunkNetEffect = if (trunkNetEffect == null) {
                // This is the only branch to modify this cell; adopt the branch net effect and record our net value
                NetEffect(value, branchNetEffect!!)
            } else {
                // Otherwise, concurrent-merge the effects from trunk and branch, and throw away the net value.
                NetEffect(null, mergeConcurrentEffects(trunkNetEffect?.effect!!, branchNetEffect!!))
            }
            // Revert the cell value to the trunk value
            value = trunkValue!!
            // Finally, reset the branch bookkeeping variables to mark this cell in "trunk" state
            trunkValue = null
            branchNetEffect = null
        }

        // Collect the net effects of this task, and reset all cells to "trunk" state.
        for (cell in cellsModifiedByThisTask) {
            cell.revertBranch()
        }
    }

    private fun <T> evaluateAwaitingTask(awaitingTask: AwaitingTask<T>) {
        // Reset any listeners from any prior evaluation of this task
        resetListeners(awaitingTask)

        // Evaluate the condition, recording the cells we read along the way
        val cellsRead: MutableSet<Cell<*>> = mutableSetOf()
        val result = awaitingTask.await.condition(object : ReadActions {
            override fun <V> read(cell: Cell<V>): V {
                cellsRead += cell
                return (cell as CellImpl<V>).value
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

    fun save(snapshot: MutableSnapshot) {
        with (snapshot.within("simulation")) {
            within("time").report(time)
        }
        val cellCollector = snapshot.within("cells")
        for (cell in cells) {
            cellCollector.within(cell.name.asSequence()).report((cell as CellImpl<*>).value, cell.valueType)
        }
        val taskCollector = snapshot.within("tasks")
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
        for (rootTaskName in rootTaskNames) {
            val tasks = tasksByRootTaskName.getOrDefault(rootTaskName, listOf())
            taskCollector.within(rootTaskName.asSequence() + "children")
                .report(tasks.map { it.task.id.name.asSequence().toList() })
        }
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
