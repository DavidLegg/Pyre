package gov.nasa.jpl.pyre.kernel

import gov.nasa.jpl.pyre.utilities.andThen
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

    operator fun <T> get(cellHandle: CellHandle<T>): Cell<T> {
        val cellState = map[cellHandle] as CellState<T>
        val cell = cellState.cell
        return cell.copy(value = cellState.getValue())
    }

    private fun <T> CellState<T>.getValue(): T {
        return effect?.let { it(cell.value) } ?: cell.value
    }

    fun <T> emit(cellHandle: CellHandle<T>, effect: Effect<T>) =
        map.compute(cellHandle) { _, cellState -> (cellState as CellState<T>)
            .copy(effect = cellState.effect?.let { it andThen effect } ?: effect) }

    fun split(): CellSet {
        fun <T> collapseCellState(cs: CellState<T>) = CellState(
            cs.cell.copy(value=cs.getValue()),
            null)
        return CellSet(map.mapValuesTo(mutableMapOf()) { (_, cellState) -> collapseCellState(cellState) })
    }

    fun save(finconCollector: FinconCollector) {
        fun <T> saveCell(state: CellState<T>) = with(state.cell) {
            finconCollector.within(name).report(state.getValue(), valueType)
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
            CellState(copy(value = stepBy(state.getValue(), delta)), null)
        }
        map.replaceAll { _, state -> stepCell(state) }
    }

    companion object {
        fun join(cellSets: Collection<CellSet>): CellSet {
            val mergedMap: MutableMap<CellHandle<*>, CellState<*>> = mutableMapOf()
            for (cs in cellSets) {
                cs.map.forEach { (handle, state) -> mergedMap.merge(handle, state) { s1, s2 -> join(s1, s2) } }
            }
            return CellSet(mergedMap)
        }

        private fun <T> join(s1: CellState<T>, s2: CellState<*>): CellState<T> {
            // Just a sanity check - this could be omitted for performance...
            require(s1.cell.value == s2.cell.value)
            return s1.copy(effect = s1.effect?.let { e1 ->
                s2.effect?.let { e2 ->
                    s1.cell.mergeConcurrentEffects(e1, e2 as Effect<T>)
                } ?: e1
            } ?: (s2.effect as Effect<T>?))
        }
    }
}