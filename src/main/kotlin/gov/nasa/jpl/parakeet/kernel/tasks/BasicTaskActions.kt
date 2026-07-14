package gov.nasa.jpl.parakeet.kernel.tasks

import gov.nasa.jpl.pyre.kernel.Cell
import gov.nasa.jpl.pyre.kernel.Effect

/**
 * The "non-yielding" actions a [gov.nasa.jpl.pyre.kernel.tasks.PureTask] can take.
 *
 * Non-yielding actions don't interrupt the flow of control, aka "yield".
 */
interface BasicTaskActions {
    fun <V> read(cell: Cell<V>): V
    fun <V> emit(cell: Cell<V>, effect: Effect<V>)
    fun <V> report(value: V)
    // Note that "spawn" is not listed here. Arguably, it's a non-yielding action and should be here.
    // It winds up being easier to restore tasks if we can choose which branch (parent or child) to take.
    // For this reason, it's better to treat spawn as a yielding action.
}