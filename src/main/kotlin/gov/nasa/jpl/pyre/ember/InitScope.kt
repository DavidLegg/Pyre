package gov.nasa.jpl.pyre.ember

/**
 * These are the actions allowed during "initialization", before the simulation starts running.
 * Note that this is the only time we're allowed to allocate cells.
 */
interface InitScope {
    fun <T: Any, E> allocate(cell: Cell<T, E>): CellSet.CellHandle<T, E>
    fun <T> spawn(name: String, step: () -> Task.PureStepResult<T>)
}

object InitScopeThroughContext {
    context (scope: InitScope)
    fun <T: Any, E> allocate(cell: Cell<T, E>): CellSet.CellHandle<T, E> = scope.allocate(cell)

    context (scope: InitScope)
    fun <T> spawn(name: String, step: () -> Task.PureStepResult<T>) = scope.spawn(name, step)
}
