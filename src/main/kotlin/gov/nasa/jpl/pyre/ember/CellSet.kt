package gov.nasa.jpl.pyre.ember

import kotlin.reflect.KType

@Suppress("UNCHECKED_CAST")
class CellSet private constructor(
    private val map: MutableMap<CellHandle<*, *>, CellState<*, *>>
) {
    // CellHandle is class, not data class, because we *want* to use object-identity equality
    class CellHandle<T, E>(val name: String, val valueType: KType) {
        override fun toString() = name
    }
    data class CellState<T, E>(val cell: Cell<T, E>, val effect: E)

    constructor() : this(mutableMapOf())

    fun <T: Any, E> allocate(cell: Cell<T, E>) =
        CellHandle<T, E>(cell.name, cell.valueType)
            .also { map[it] = CellState(cell, cell.effectTrait.empty()) }

    operator fun <T, E> get(cellHandle: CellHandle<T, E>): Cell<T, E> {
        val cellState = map[cellHandle] as CellState<T, E>
        val cell = cellState.cell
        return cell.copy(value = cell.applyEffect(cell.value, cellState.effect))
    }

    fun <T, E> emit(cellHandle: CellHandle<T, E>, effect: E) =
        map.compute(cellHandle) { _, cellState -> (cellState as CellState<T, E>)
            .copy(effect = cellState.cell.effectTrait.sequential(cellState.effect, effect)) }

    fun split(): CellSet {
        fun <T, E> collapseCellState(cs: CellState<T, E>) = CellState(
            cs.cell.copy(value=cs.cell.applyEffect(cs.cell.value, cs.effect)),
            cs.cell.effectTrait.empty())
        return CellSet(map.mapValuesTo(mutableMapOf()) { (_, cellState) -> collapseCellState(cellState) })
    }

    fun save(finconCollector: FinconCollector) {
        fun <T, E> saveCell(state: CellState<T, E>) = with(state.cell) {
            finconCollector.report(sequenceOf(name), applyEffect(value, state.effect), type=valueType)
        }
        map.values.forEach { saveCell(it) }
    }

    fun restore(inconProvider: InconProvider) {
        fun <T, E> restoreCell(handle: CellHandle<T, E>) = with(this[handle]) {
            // If incon is missing, ignore it and move on
            inconProvider.get<T>(sequenceOf(name), valueType)?.let {
                map[handle] = CellState(copy(value=it), effectTrait.empty())
            }
        }
        map.keys.forEach { restoreCell(it) }
    }

    fun stepBy(delta: Duration) {
        fun <T, E> stepCell(state: CellState<T, E>) = with(state.cell) {
            CellState(copy(value = stepBy(applyEffect(value, state.effect), delta)), effectTrait.empty())
        }
        map.replaceAll { _, state -> stepCell(state) }
    }

    companion object {
        fun join(cellSets: Collection<CellSet>): CellSet {
            val mergedMap: MutableMap<CellHandle<*, *>, CellState<*, *>> = mutableMapOf()
            for (cs in cellSets) {
                cs.map.forEach { (handle, state) -> mergedMap.merge(handle, state) { s1, s2 -> join(s1, s2) } }
            }
            return CellSet(mergedMap)
        }

        private fun <T, E> join(s1: CellState<T, E>, s2: CellState<*, *>): CellState<T, E> {
            // Just a sanity check - this could be omitted for performance...
            require(s1.cell.value == s2.cell.value)
            return s1.copy(effect = s1.cell.effectTrait.concurrent(s1.effect, s2.effect as E))
        }
    }
}