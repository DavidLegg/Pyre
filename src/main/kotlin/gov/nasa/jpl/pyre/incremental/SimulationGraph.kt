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

    sealed interface TaskNode : SGNode

    /**
     * All [TaskNode]s except for [RootTaskNode], these record the actions taken by a task.
     */
    sealed interface TaskStepNode : TaskNode {
        val prior: TaskNode
        var next: TaskStepNode?
    }

    /**
     * Any step of a task which yields back to the simulator.
     * These have an explicit continuation [Task] used to resume the rest of the task when appropriate.
     */
    sealed interface YieldingStepNode : TaskStepNode {
        var continuation: Task<*>?
    }

    /**
     * Any [TaskStepNode] which is not a [YieldingStepNode].
     * The task continues immediately, with no opportunity for the simulator to pause at this point.
     */
    sealed interface NonYieldingStepNode : TaskStepNode

    class RootTaskNode(
        override val time: SimulationTime,
        val name: Name,
        val task: PureTaskStep<*>,
        var next: TaskNode? = null,
    ) : TaskNode

    class ReadNode(
        override val time: SimulationTime,
        override val prior: TaskNode,
        val cell: CellNode<*>,
        override var next: TaskStepNode? = null,
    ) : NonYieldingStepNode

    class WriteNode(
        override val time: SimulationTime,
        override val prior: TaskNode,
        val cell: CellWriteNode<*>,
        override var next: TaskStepNode? = null,
    ) : NonYieldingStepNode

    class ReportNode(
        override val time: SimulationTime,
        override val prior: TaskNode,
        val report: IncrementalReport<*>,
        override var next: TaskStepNode? = null,
    ) : NonYieldingStepNode

    class SpawnNode(
        override val time: SimulationTime,
        override val prior: TaskNode,
        val child: RootTaskNode,
        override var continuation: Task<*>?,
        override var next: TaskStepNode? = null,
    ) : YieldingStepNode

    class AwaitNode(
        override val time: SimulationTime,
        override val prior: TaskNode,
        val condition: Condition,
        val reads: MutableSet<CellNode<*>>,
        override var continuation: Task<*>?,
        override var next: TaskStepNode? = null,
    ) : YieldingStepNode

    sealed interface CellNode<T> : SGNode {
        val value: T
        val reads: MutableSet<ReadNode>
        val next: MutableList<CellNode<T>>
    }

    class CellWriteNode<T>(
        override val time: SimulationTime,
        override val value: T,
        var prior: CellNode<T>?,
        val effect: Effect<T>,
        override val next: MutableList<CellNode<T>> = mutableListOf(),
        override val reads: MutableSet<ReadNode> = TreeSet(compareBy { it.time }),
    ) : CellNode<T>

    class CellMergeNode<T>(
        override val time: SimulationTime,
        override val value: T,
        val prior: MutableList<CellWriteNode<T>>,
        override val next: MutableList<CellNode<T>> = mutableListOf(),
        override val reads: MutableSet<ReadNode> = TreeSet(compareBy { it.time }),
    ) : CellNode<T>

    class CellStepNode<T>(
        override val time: SimulationTime,
        override val value: T,
        var prior: CellNode<T>,
        val step: Duration,
        override val next: MutableList<CellNode<T>> = mutableListOf(),
        override val reads: MutableSet<ReadNode> = TreeSet(compareBy { it.time }),
    ) : CellNode<T>
}