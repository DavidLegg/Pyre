package gov.nasa.jpl.pyre.kernel

/**
 * These are the actions allowed during "initialization", before the simulation starts running.
 * Note that this is the only time we're allowed to allocate cells.
 */
interface BasicInitScope {
    fun <T: Any> allocate(cell: Cell<T>): CellSet.CellHandle<T>
    fun <T> spawn(name: Name, step: () -> Task.PureStepResult<T>)
    fun <T> read(cell: CellSet.CellHandle<T>): T

    companion object {
        context (scope: BasicInitScope)
        fun <T: Any> allocate(cell: Cell<T>): CellSet.CellHandle<T> = scope.allocate(cell)

        context (scope: BasicInitScope)
        fun <T> spawn(name: Name, step: () -> Task.PureStepResult<T>) = scope.spawn(name, step)

        context (scope: BasicInitScope)
        fun <T> read(cell: CellSet.CellHandle<T>): T = scope.read(cell)
    }
}
