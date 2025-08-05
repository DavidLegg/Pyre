package gov.nasa.jpl.pyre.flame.tasks

import gov.nasa.jpl.pyre.ember.Cell
import gov.nasa.jpl.pyre.ember.CellSet
import gov.nasa.jpl.pyre.ember.Task
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope

/**
 * Creates a subcontext named "$this/$contextName".
 * If models incorporate the context name into the names of tasks and resources,
 * this provides an easy way to build hierarchical models without a lot of manual bookkeeping.
 */
fun SparkInitContext.subContext(contextName: String) = object : SparkInitContext by this {
    override fun <T : Any, E> allocate(cell: Cell<T, E>): CellSet.CellHandle<T, E> =
        this@subContext.allocate(cell.copy(name = "$contextName/${cell.name}"))

    override fun <T> spawn(name: String, step: () -> Task.PureStepResult<T>) =
        this@subContext.spawn("$contextName/$name", step)

    override fun onStartup(name: String, block: suspend SparkTaskScope.() -> Unit) =
        this@subContext.onStartup("$contextName/$name", block)

    override fun toString() = "${this@subContext}/$contextName"
}
