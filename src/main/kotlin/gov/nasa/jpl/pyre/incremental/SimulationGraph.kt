package gov.nasa.jpl.pyre.incremental

import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.Effect
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.PureTaskStep

interface SimulationGraph {
    sealed interface SGNode

    class ReportNode(
        val report: IncrementalReport<*>,
    ) : SGNode

    sealed interface TaskNode : SGNode

    class RootTaskNode(
        val name: Name,
        val step: PureTaskStep<*>,
        var continuation: RunTaskNode? = null,
    ) : TaskNode

    class RunTaskNode(
        val parent: TaskNode,
        val reads: List<CellNode<*>>,
        val writes: List<CellNode<*>>,
        val reports: List<ReportNode>,
        val child: RootTaskNode?,
        var continuation: TaskNode? = null,
    ) : TaskNode

    sealed interface CellNode<T> : SGNode

    class CellWriteNode<T>(
        val value: T,
        val effect: Effect<T>,
    ) : CellNode<T>

    class CellMergeNode<T>(
        val branches: List<CellWriteNode<T>>,
    ) : CellNode<T>

    class CellStepNode<T>(
        val step: Duration,
    ) : CellNode<T>
}