package gov.nasa.jpl.pyre.incremental.foundation

import gov.nasa.jpl.pyre.incremental.foundation.SimulationGraph.*
import gov.nasa.jpl.pyre.incremental.kernel.KernelPlan
import gov.nasa.jpl.pyre.incremental.kernel.KernelPlanEdits
import gov.nasa.jpl.pyre.kernel.BasicInitScope
import gov.nasa.jpl.pyre.kernel.Cell
import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.Effect
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.PureTaskStep
import gov.nasa.jpl.pyre.utilities.identity
import java.util.TreeMap
import kotlin.reflect.KType
import kotlin.time.Instant

/**
 * Support for [GraphIncrementalPlanSimulation], which implements graph-based incremental simulation at the kernel level.
 */
class KernelIncrementalSimulator(
    constructModel: context (BasicInitScope) () -> Unit,
    kernelPlan: KernelPlan,
    modelClass: KType,
) {
    val cellNodes: MutableMap<Cell<*>, TreeMap<Instant, CellNode<*>>> = mutableMapOf()

    init {
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

            override fun <T> spawn(
                name: Name,
                step: PureTaskStep<T>
            ) {
                TODO("Not yet implemented")
            }

            override fun <T> read(cell: Cell<T>): T {
                // Since there cannot be effects during init, we may safely assume the first cell to be a write node
                return (getCellNodes(cell).firstEntry().value as CellWriteNode<T>).value
            }

            override fun <T> report(value: T) {
                TODO("Not yet implemented")
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
}