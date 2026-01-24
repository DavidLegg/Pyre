package gov.nasa.jpl.pyre.incremental

import gov.nasa.jpl.pyre.kernel.Cell
import gov.nasa.jpl.pyre.kernel.Condition
import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.Effect
import gov.nasa.jpl.pyre.kernel.Task
import java.util.TreeSet
import kotlin.time.Instant

interface SimulationGraph {
    // Time within the simulator is primarily the Instant at which a task runs.
    // Within a single instant, there's a series of job batches.
    // All the jobs in a batch run in parallel.
    // The ordering of steps between two parallel jobs is meaningless, but we can impose an arbitrary order for sorting purposes.
    // Finally, within a job, there are a series of steps.
    // When doing incremental re-simulation, steps in a branch may be added and removed, but they cannot be re-ordered.
    // Because of this, the step number is not recorded in the time field. Instead, we traverse the graph itself.
    data class SimulationTime(
        val instant: Instant,
        val batch: Int = 0,
        val branch: Int = 0,
    ) : Comparable<SimulationTime> {
        override fun compareTo(other: SimulationTime): Int {
            var n = instant.compareTo(other.instant)
            if (n == 0) n = batch.compareTo(other.batch)
            if (n == 0) n = branch.compareTo(other.branch)
            return n
        }

        override fun toString(): String = "$instant::$batch/$branch"
    }

    sealed interface SGNode {
        val time: SimulationTime
    }

    sealed interface TaskNode : SGNode, Comparable<TaskNode> {
        val prior: TaskNode?
        var next: TaskNode?

        override fun compareTo(other: TaskNode): Int {
            val n = time.compareTo(other.time)
            if (n != 0) return n

            var thisAncestor = this.prior
            var otherAncestor = other.prior
            while (thisAncestor != null || otherAncestor != null) {
                if (thisAncestor === other) return 1
                if (otherAncestor === this) return -1
                thisAncestor = thisAncestor?.prior?.takeIf { it.time == time }
                otherAncestor = otherAncestor?.prior?.takeIf { it.time == time }
            }
            throw IllegalStateException("Internal error! Task nodes on the same branch are not connected by 'prior' edges.")
        }
    }

    /**
     * Any step of a task which yields back to the simulator.
     * These have an explicit continuation [Task] used to resume the rest of the task when appropriate.
     */
    sealed interface YieldingStepNode : TaskNode {
        val continuation: Task<*>
    }

    /**
     * Any [TaskNode] which is not a [YieldingStepNode].
     * The task continues immediately, with no opportunity for the simulator to pause at this point.
     */
    sealed interface NonYieldingStepNode : TaskNode

    /** The root task, from which a task can be restarted or replayed. */
    class RootTaskNode(
        override val time: SimulationTime,
        val task: Task<*>,
        // prior is mutable so that spawn nodes can link them after making them
        override var prior: TaskNode? = null,
        override var next: TaskNode? = null,
    ) : TaskNode

    /** The first node in a task step, following a root or yielding step. Used to schedule the next task step. */
    class StepBeginNode(
        override val time: SimulationTime,
        override val prior: TaskNode,
        override val continuation: Task<*>,
        override var next: TaskNode? = null,
    ) : YieldingStepNode

    class ReadNode(
        override val time: SimulationTime,
        override val prior: TaskNode,
        val cell: CellNode<*>,
        override var next: TaskNode? = null,
    ) : NonYieldingStepNode

    class WriteNode(
        override val time: SimulationTime,
        override val prior: TaskNode,
        val cell: CellWriteNode<*>,
        override var next: TaskNode? = null,
    ) : NonYieldingStepNode

    class ReportNode<T>(
        override val time: SimulationTime,
        override val prior: TaskNode?,
        val content: T,
        override var next: TaskNode? = null,
    ) : NonYieldingStepNode

    class SpawnNode(
        override val time: SimulationTime,
        override val prior: TaskNode,
        val child: RootTaskNode,
        override val continuation: Task<*>,
        override var next: TaskNode? = null,
    ) : YieldingStepNode

    class AwaitNode(
        override val time: SimulationTime,
        override val prior: TaskNode,
        val condition: Condition,
        val reads: MutableSet<CellNode<*>> = TreeSet(compareBy { it.time }),
        override val continuation: Task<*>,
        override var next: TaskNode? = null,
    ) : YieldingStepNode

    sealed interface CellNode<T> : SGNode {
        val cell: Cell<T>
        var value: T
        val reads: MutableSet<ReadNode>
        val awaiters: MutableSet<AwaitNode>
        val next: MutableList<CellNode<T>>
    }

    class CellWriteNode<T>(
        override val time: SimulationTime,
        override val cell: Cell<T>,
        override var value: T,
        var prior: CellNode<T>?,
        val effect: Effect<T>,
        // Writer is var and nullable to facilitate construction; it should never nominally be null when finalized.
        var writer: WriteNode? = null,
        override val next: MutableList<CellNode<T>> = mutableListOf(),
        override val reads: MutableSet<ReadNode> = TreeSet(compareBy { it.time }),
        override val awaiters: MutableSet<AwaitNode> = TreeSet(compareBy { it.time }),
    ) : CellNode<T>

    class CellMergeNode<T>(
        override val time: SimulationTime,
        override val cell: Cell<T>,
        override var value: T,
        var batchStart: CellNode<T>,
        var prior: MutableList<CellWriteNode<T>>,
        override val next: MutableList<CellNode<T>> = mutableListOf(),
        override val reads: MutableSet<ReadNode> = TreeSet(compareBy { it.time }),
        override val awaiters: MutableSet<AwaitNode> = TreeSet(compareBy { it.time }),
    ) : CellNode<T>

    class CellStepNode<T>(
        override val time: SimulationTime,
        override val cell: Cell<T>,
        override var value: T,
        var prior: CellNode<T>,
        val step: Duration,
        override val next: MutableList<CellNode<T>> = mutableListOf(),
        override val reads: MutableSet<ReadNode> = TreeSet(compareBy { it.time }),
        override val awaiters: MutableSet<AwaitNode> = TreeSet(compareBy { it.time }),
    ) : CellNode<T>
}