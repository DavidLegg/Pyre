package gov.nasa.jpl.pyre.incremental

import gov.nasa.jpl.pyre.kernel.Cell
import gov.nasa.jpl.pyre.kernel.Condition
import gov.nasa.jpl.pyre.kernel.Effect
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.tasks.Task

sealed interface SGNode {
    val serialId: Int
    val time: SimulationTime


    sealed interface TaskNode : SGNode {
        val taskName: Name
        var prior: TaskNode?
        var next: TaskNode?
    }

    /**
     * Any step of a task which yields back to the simulator.
     * These have an explicit continuation [Task] used to resume the rest of the task when appropriate.
     */
    sealed interface YieldingStepNode : TaskNode {
        /** The handle to continue running this task. Null after executing, since any task step may only be run once. */
        var continuation: Task?
    }

    /**
     * Any [TaskNode] which is not a [YieldingStepNode] nor a [FinalStepNode].
     * The task continues immediately, with no opportunity for the simulator to pause at this point.
     */
    sealed interface NonYieldingStepNode : TaskNode

    /**
     * A [TaskNode] indicating the task is complete.
     */
    sealed interface FinalStepNode : TaskNode

    /** The root task, from which a task can be restarted or replayed. */
    class StartTaskNode(
        override val serialId: Int,
        override val time: SimulationTime,
        task: Task,
        // prior is mutable so that spawn nodes can link them after making them
        override var prior: TaskNode? = null,
        override var next: TaskNode? = null,
    ) : YieldingStepNode {
        override val taskName: Name = task.name
        override var continuation: Task? = task
        override fun toString(): String = "Start($taskName) @ $time"
    }

    class ReadNode(
        override val serialId: Int,
        override val taskName: Name,
        override val time: SimulationTime,
        override var prior: TaskNode?,
        var cell: CellNode<*>,
        var value: Any?,
        override var next: TaskNode? = null,
    ) : NonYieldingStepNode {
        override fun toString(): String = "Read(${cell.cell.name}) @ $time"
    }

    class WriteNode(
        override val serialId: Int,
        override val taskName: Name,
        override val time: SimulationTime,
        override var prior: TaskNode?,
        val cell: CellWriteNode<*>,
        override var next: TaskNode? = null,
    ) : NonYieldingStepNode {
        override fun toString(): String = "Write(${cell.effect}, ${cell.cell.name}) @ $time"
    }

    // TODO: Add an interface IncrementalReport which only has (time, content)
    class ReportNode<T>(
        override val serialId: Int,
        override val taskName: Name,
        override val time: SimulationTime,
        override var prior: TaskNode?,
        val content: T,
        override var next: TaskNode? = null,
    ) : NonYieldingStepNode {
        override fun toString(): String = "Report($content) @ $time"
    }

    class SpawnNode(
        override val serialId: Int,
        override val taskName: Name,
        override val time: SimulationTime,
        override var prior: TaskNode?,
        val child: StartTaskNode,
        override var continuation: Task?,
        override var next: TaskNode? = null,
    ) : YieldingStepNode {
        override fun toString(): String = "Spawn($child) @ $time"
    }

    class AwaitNode(
        override val serialId: Int,
        override val taskName: Name,
        override val time: SimulationTime,
        override var prior: TaskNode?,
        val condition: Condition,
        val reads: MutableMap<CellNode<*>, Any?> = mutableMapOf(),
        // TODO: Should the continuation on an AwaitNode just be the rewait instead?
        //   If we do that, we can just run the rewait task to recover the continuation...
        //   But to do that safely, we'd have to reload all the AwaitNodes' continuations when we do that, maybe?
        //   Alternatively, maybe both the incremental and single-shot simulator should lose the notion of a "rewait" task,
        //   and just store the condition directly instead?
        //   The question then is how to deal with task history for awaiting vs. await-completed tasks...
        val rewait: Task,
        override var continuation: Task?,
        override var next: TaskNode? = null,
    ) : YieldingStepNode {
        override fun toString(): String = "Await($condition) @ $time"
    }

    class AwaitCompleteNode(
        override val serialId: Int,
        override val taskName: Name,
        override val time: SimulationTime,
        override var prior: TaskNode?,
        override var continuation: Task?,
        override var next: TaskNode? = null,
    ) : YieldingStepNode {
        override fun toString(): String = "AwaitComplete($taskName) @ $time"
    }

    class TaskCompleteNode(
        override val serialId: Int,
        override val taskName: Name,
        override val time: SimulationTime,
        override var prior: TaskNode?,
        override var next: TaskNode? = null,
    ) : FinalStepNode {
        override fun toString(): String = "TaskComplete($taskName) @ $time"
    }

    // TODO: Should we also have a TaskErrorNode / TaskCrashedNode, implementing FinalStepNode?
    //   Rationale: The kernel has final say over tasks; catching errors there means you *can't* crash the simulator.
    //   It also has a little extra context about what task this is (task name, parent names, etc.)
    //   Counter-rationale: This adds complexity to the kernel.
    //   It also means I basically *can't* crash out of a sim even if that behavior is desired, for some reason.
    //   Also, I don't have access to the stderr channel from the kernel.
    //   There's no great way for the kernel to report that the task failed.
    //   We could catch from coroutineTask instead, where we're converting foundation tasks to kernel tasks.
    //   In that case, we could catch the error, report the failure on stderr, and gracefully complete the task.
    //   Additionally, if we *really* need to crash the sim, we could either (a) have a dedicated "CrashSim" exception
    //   which we handle specially from coroutineTask, or (b) we could have special tasks which don't get error catching.

    // Related thought - should cells have a "failed" state, separate from any value?
    // When we apply an effect, merge effects, or step a cell, that's model-provided code. It could throw exceptions.
    // If it does, right now, the simulator just crashes. That's not great behavior, especially if throwing from a task
    // only crashes the one task. I don't like the asymmetry in severity there.
    // Instead, if an operation on a cell throws, we could capture the exception and mark the cell as "failed".
    // TBD how to handle "clearing" a cell, since effects are a function of the cell's value... maybe you can't clear it?
    // If a task or condition tries to read a "failed" cell, we throw a "CellFailedException" of our own making.
    // Tasks and conditions have the option to catch this (e.g. a resource-reporting task could catch this,
    // make a report to the stderr channel that the resource failed, and await it being cleared).
    // Since most tasks wouldn't catch it though, those parts of the model could just crash out and turn off.
    // This way, a bad state can crash the parts of the model that depend on it, deterministically.
    // Tasks still have an option to recover if they really need to.

    sealed interface CellNode<T> : SGNode {
        val cell: Cell<T>
        var value: T
        val reads: MutableSet<ReadNode>
        val awaiters: MutableSet<AwaitNode>
        val next: MutableList<CellNode<T>>
    }

    class CellWriteNode<T>(
        override val serialId: Int,
        override val time: SimulationTime,
        override val cell: Cell<T>,
        override var value: T,
        var prior: CellNode<T>?,
        val effect: Effect<T>,
        // Writer is var and nullable to facilitate construction; it should never nominally be null when finalized.
        var writer: WriteNode? = null,
        override val next: MutableList<CellNode<T>> = mutableListOf(),
        override val reads: MutableSet<ReadNode> = mutableSetOf(),
        override val awaiters: MutableSet<AwaitNode> = mutableSetOf(),
    ) : CellNode<T> {
        override fun toString(): String = "CellWrite($effect, ${cell.name}) @ $time"
    }

    class CellMergeNode<T>(
        override val serialId: Int,
        override val time: SimulationTime,
        override val cell: Cell<T>,
        override var value: T,
        var batchStart: CellNode<T>,
        var prior: MutableList<CellWriteNode<T>>,
        override val next: MutableList<CellNode<T>> = mutableListOf(),
        override val reads: MutableSet<ReadNode> = mutableSetOf(),
        override val awaiters: MutableSet<AwaitNode> = mutableSetOf(),
    ) : CellNode<T> {
        override fun toString(): String = "CellMerge(${cell.name}) @ $time"
    }

    class CellStepNode<T>(
        override val serialId: Int,
        override val time: SimulationTime,
        override val cell: Cell<T>,
        override var value: T,
        var prior: CellNode<T>,
        override val next: MutableList<CellNode<T>> = mutableListOf(),
        override val reads: MutableSet<ReadNode> = mutableSetOf(),
        override val awaiters: MutableSet<AwaitNode> = mutableSetOf(),
    ) : CellNode<T> {
        override fun toString(): String = "CellStep(${cell.name}) @ $time"
    }
}