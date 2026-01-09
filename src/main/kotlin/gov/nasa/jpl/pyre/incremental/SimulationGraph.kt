package gov.nasa.jpl.pyre.incremental

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

    class ReportNode(
        val reporter: RunTaskNode?,
        val report: IncrementalReport<*>,
    ) : SGNode {
        override val time: SimulationTime get() = report.time
    }

    sealed interface TaskNode : SGNode

    class RootTaskNode(
        override val time: SimulationTime,
        val name: Name,
        val task: PureTaskStep<*>,
        var continuation: RunTaskNode? = null,
    ) : TaskNode

    class RunTaskNode(
        override val time: SimulationTime,
        val parent: TaskNode,
        var task: Task<*>?,
        val reads: MutableList<CellNode<*>> = mutableListOf(),
        val writes: MutableList<CellNode<*>> = mutableListOf(),
        val reports: MutableList<ReportNode> = mutableListOf(),
        var child: RootTaskNode? = null,
        var continuation: TaskNode? = null,
    ) : TaskNode

    sealed interface CellNode<T> : SGNode {
        val value: T
        val reads: MutableSet<RunTaskNode>
        val next: MutableList<CellNode<T>>
    }

    class CellWriteNode<T>(
        override val time: SimulationTime,
        override val value: T,
        var prior: CellNode<T>?,
        val effect: Effect<T>,
        override val next: MutableList<CellNode<T>> = mutableListOf(),
        override val reads: MutableSet<RunTaskNode> = TreeSet(),
    ) : CellNode<T>

    class CellMergeNode<T>(
        override val time: SimulationTime,
        override val value: T,
        val prior: MutableList<CellWriteNode<T>>,
        override val next: MutableList<CellNode<T>> = mutableListOf(),
        override val reads: MutableSet<RunTaskNode> = TreeSet(),
    ) : CellNode<T>

    class CellStepNode<T>(
        override val time: SimulationTime,
        override val value: T,
        var prior: CellNode<T>,
        val step: Duration,
        override val next: MutableList<CellNode<T>> = mutableListOf(),
        override val reads: MutableSet<RunTaskNode> = TreeSet(),
    ) : CellNode<T>
}