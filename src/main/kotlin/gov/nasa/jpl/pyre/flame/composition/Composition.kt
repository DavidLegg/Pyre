package gov.nasa.jpl.pyre.flame.composition

import gov.nasa.jpl.pyre.ember.Cell
import gov.nasa.jpl.pyre.ember.CellSet
import gov.nasa.jpl.pyre.ember.Task
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext

/**
 * Creates a subcontext, wherein cells and tasks are given names prepended with "contextName/".
 * This reduces the amount of manual bookkeeping required to build large, hierarchical models.
 */
fun SparkInitContext.subContext(contextName: String): SparkInitContext {
    return object : SparkInitContext by this {
        override fun <T : Any, E> allocate(cell: Cell<T, E>): CellSet.CellHandle<T, E> =
            this@subContext.allocate(cell.copy(name = "$contextName/${cell.name}"))

        override fun <T> spawn(name: String, step: () -> Task.PureStepResult<T>) =
            this@subContext.spawn("$contextName/$name", step)
    }
}
