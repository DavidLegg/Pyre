package gov.nasa.jpl.pyre.incremental

import gov.nasa.jpl.pyre.kernel.Condition
import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.Effect
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.PureTaskStep
import gov.nasa.jpl.pyre.kernel.Task
import java.util.TreeSet

interface SimulationGraph {
    sealed interface SGNode {
        val time: SimulationTime
    }

    sealed interface TaskNode : SGNode {
        val prior: TaskNode?
        var next: TaskNode?
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

    class ReportNode(
        override val prior: TaskNode,
        val report: IncrementalReport<*>,
        override var next: TaskNode? = null,
    ) : NonYieldingStepNode {
        override val time: SimulationTime get() = report.time
    }

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
        val value: T
        val reads: MutableSet<ReadNode>
        val awaiters: MutableSet<AwaitNode>
        val next: MutableList<CellNode<T>>
    }

    class CellWriteNode<T>(
        override val time: SimulationTime,
        override val value: T,
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
        override val value: T,
        var prior: MutableList<CellWriteNode<T>>,
        override val next: MutableList<CellNode<T>> = mutableListOf(),
        override val reads: MutableSet<ReadNode> = TreeSet(compareBy { it.time }),
        override val awaiters: MutableSet<AwaitNode> = TreeSet(compareBy { it.time }),
    ) : CellNode<T>

    class CellStepNode<T>(
        override val time: SimulationTime,
        override val value: T,
        var prior: CellNode<T>,
        val step: Duration,
        override val next: MutableList<CellNode<T>> = mutableListOf(),
        override val reads: MutableSet<ReadNode> = TreeSet(compareBy { it.time }),
        override val awaiters: MutableSet<AwaitNode> = TreeSet(compareBy { it.time }),
    ) : CellNode<T>
}