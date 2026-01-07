package gov.nasa.jpl.pyre.incremental.foundation

import gov.nasa.jpl.pyre.incremental.foundation.SimulationGraph.*
import gov.nasa.jpl.pyre.incremental.kernel.KernelActivity
import gov.nasa.jpl.pyre.incremental.kernel.KernelPlan
import gov.nasa.jpl.pyre.incremental.kernel.KernelPlanEdits
import gov.nasa.jpl.pyre.kernel.BasicInitScope
import gov.nasa.jpl.pyre.kernel.Cell
import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.Effect
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.PureTaskStep
import gov.nasa.jpl.pyre.kernel.ReportHandler
import gov.nasa.jpl.pyre.utilities.identity
import java.util.TreeMap
import kotlin.contracts.ExperimentalContracts
import kotlin.reflect.KType
import kotlin.time.Instant

/**
 * Support for [GraphIncrementalPlanSimulation], which implements graph-based incremental simulation at the kernel level.
 */
class KernelIncrementalSimulator {
    val cellNodes: MutableMap<Cell<*>, TreeMap<Instant, CellNode<*>>> = mutableMapOf()
    val taskNodes: MutableMap<KernelActivity, RootTaskNode> = mutableMapOf()
    val reportNodes: MutableList<Any?> = mutableListOf()

    // Need to use secondary constructor to add a contract statement
    @OptIn(ExperimentalContracts::class)
    constructor(
        constructModel: context (BasicInitScope) () -> Unit,
        kernelPlan: KernelPlan,
        reportHandler: IncrementalReportHandler
    ) {
        kotlin.contracts.contract {
            callsInPlace(constructModel, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
        }
        var reportTime = ReportTimeImpl(kernelPlan.planStart)
        val basicInitScope = object : BasicInitScope {
            override fun <T : Any> allocate(
                name: Name,
                value: T,
                valueType: KType,
                stepBy: (T, Duration) -> T,
                mergeConcurrentEffects: (Effect<T>, Effect<T>) -> Effect<T>
            ): Cell<T> {
                // Create an incremental cell, and add its initial value to the graph
                return IncrementalCellImpl<T>(name).also {
                    cellNodes[it] = TreeMap<Instant, CellNode<*>>().apply {
                        put(kernelPlan.planStart, CellWriteNode(value, identity()))
                    }
                }
            }

            override fun <T> spawn(name: Name, step: PureTaskStep<T>) {
                // Add the task node into the graph. Implicitly, the continuation is not expanded.
                taskNodes[KernelActivity(name, kernelPlan.planStart, step)] = RootTaskNode(name, step)
            }

            override fun <T> read(cell: Cell<T>): T {
                // Since there cannot be effects during init,
                // we may safely assume the first node to be the write node added during allocation
                return (getCellNodes(cell).firstEntry().value as CellWriteNode<T>).value
            }

            override fun <T> report(value: T) {
                // Create the uniquely-identifiable incremental report
                val report = IncrementalReport(reportTime, value)
                reportNodes += ReportNode(report)
                reportHandler.report(report)
                reportTime = reportTime.nextStep()
            }
        }
        constructModel(basicInitScope)
    }

    fun run(planEdits: KernelPlanEdits) {
        TODO("Not yet implemented")
    }

    private class IncrementalCellImpl<T>(override val name: Name) : Cell<T>
    @Suppress("UNCHECKED_CAST")
    private fun <T> getCellNodes(cell: Cell<T>) = cellNodes.getValue(cell) as TreeMap<Instant, CellNode<T>>

    // Time within the simulator is primarily the Instant at which a task runs.
    // Within a single instant, there's a series of job batches.
    // All the jobs in a batch run in parallel.
    // The ordering of steps between two parallel jobs is meaningless, but we can impose an arbitrary order for sorting purposes.
    // Finally, within a job, there are a series of steps.
    private data class ReportTimeImpl(
        override val instant: Instant,
        val batch: Int = 0,
        val branch: Int = 0,
        val step: Int = 0,
    ) : ReportTime {
        override fun compareTo(other: ReportTime): Int = when (other) {
            is ReportTimeImpl -> {
                var n = instant.compareTo(other.instant)
                if (n == 0) n = batch.compareTo(other.batch)
                if (n == 0) n = branch.compareTo(other.branch)
                if (n == 0) n = step.compareTo(other.step)
                n
            }
        }

        fun nextBatch() = copy(batch = batch + 1, branch = 0, step = 0)
        fun nextBranch() = copy(branch = branch + 1, step = 0)
        fun nextStep() = copy(step = step + 1)
    }
}

// TODO: Consider pushing time-of-report into the kernel generally, instead of waiting for foundation to introduce that.
//   This may be especially easy if we switch to Instant-based times everywhere.
// TODO: There's also a problem here with doing incremental, fine-grained report modification.
//   What I mean by that is multiple reports, issued from one task during one batch.
//   Those reports are ordered, and that order should be reportable and preserved.
/** Wrapper around a report to give every report a unique identity, so they can later be revoked. */
class IncrementalReport<T>(
    val time: ReportTime,
    val content: T,
) {
    override fun toString(): String = "$time: $content"
}

sealed interface ReportTime : Comparable<ReportTime> {
    val instant: Instant
}

/**
 * A generalization of [ReportHandler] which allows the simulator to revoke a report it issued previously,
 * in response to incremental changes to the simulation.
 */
interface IncrementalReportHandler {
    fun report(report: IncrementalReport<*>)
    fun revoke(report: IncrementalReport<*>)
}
