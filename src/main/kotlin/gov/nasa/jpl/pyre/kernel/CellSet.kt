package gov.nasa.jpl.pyre.kernel

import gov.nasa.jpl.pyre.utilities.andThen
import kotlin.collections.mapValuesTo
import kotlin.reflect.KType

@Suppress("UNCHECKED_CAST")
class CellSet private constructor(
    private val map: MutableMap<CellHandle<*>, CellState<*>>
) {
    // CellHandle is class, not data class, because we *want* to use object-identity equality
    class CellHandle<T>(val name: String, val valueType: KType) {
        override fun toString() = name
    }
    data class CellState<T>(val cell: Cell<T>, val effect: Effect<T>?)

    constructor() : this(mutableMapOf())

    fun <T: Any> allocate(cell: Cell<T>) =
        CellHandle<T>(cell.name, cell.valueType)
            .also { map[it] = CellState(cell, null) }

    operator fun <T> get(cellHandle: CellHandle<T>): Cell<T> =
        (map.getValue(cellHandle) as CellState<T>).cell

    fun <T> emit(cellHandle: CellHandle<T>, effect: Effect<T>) =
        map.compute(cellHandle) { _, cellState -> CellState(
            (cellState as CellState<T>).cell.copy(value = effect(cellState.cell.value)),
            cellState.effect?.let { it andThen effect } ?: effect)
        }

    fun split(): CellSet = CellSet(map.toMutableMap())

    fun save(finconCollector: FinconCollector) {
        fun <T> saveCell(state: CellState<T>) = with(state.cell) {
            finconCollector.within(name).report(state.cell.value, valueType)
        }
        map.values.forEach { saveCell(it) }
    }

    fun restore(inconProvider: InconProvider) {
        fun <T> restoreCell(handle: CellHandle<T>) = with(this[handle]) {
            // If incon is missing, ignore it and move on
            inconProvider.within(name).provide<T>(valueType)?.let {
                map[handle] = CellState(copy(value=it), null)
            }
        }
        map.keys.forEach { restoreCell(it) }
    }

    fun stepBy(delta: Duration) {
        fun <T> stepCell(state: CellState<T>) = with(state.cell) {
            CellState(copy(value = stepBy(state.cell.value, delta)), null)
        }
        map.replaceAll { _, state -> stepCell(state) }
    }

    companion object {
        /**
         * Join a collection of branches that were [split] from this CellSet.
         */
        fun CellSet.join(cellSets: Collection<CellSet>): CellSet =
            CellSet(map.mapValuesTo(mutableMapOf()) { (handle, baseCell) ->
                baseCell.join(cellSets.map { it.map.getValue(handle) })
            })

        private fun <T> CellState<T>.join(branches: Collection<CellState<*>>): CellState<T> {
            val nontrivialBranches = branches.filter { it.effect != null }
            return when (nontrivialBranches.size) {
                // No nontrivial branches - return the base cell unchanged
                0 -> this
                // One nontrivial branch - return that branch's cell, but null out the effects (which are now collapsed)
                1 -> (nontrivialBranches.first() as CellState<T>).copy(effect = null)
                // Multiple nontrivial branches - concurrent-merge their effects and apply to base value
                else -> {
                    val netEffect = nontrivialBranches
                        .map { it.effect as Effect<T> }
                        .reduce(cell.mergeConcurrentEffects)
                    CellState(
                        cell.copy(value = netEffect(cell.value)),
                        null
                    )
                }
            }
        }
    }
}